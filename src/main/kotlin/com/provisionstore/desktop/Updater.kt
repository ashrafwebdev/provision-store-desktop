package com.provisionstore.desktop

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Checks the latest GitHub release for a newer installer and runs it.
 * Releases are tagged "v<version>" (for example "v1.5.0") and carry a .msi file.
 * The installer upgrades the app in place; the shop's data stays in AppData.
 */
object Updater {

    private const val REPO = "ashrafwebdev/provision-store-desktop"

    data class Release(val version: String, val msiUrl: String)

    /** Returns the newer release, or null when this version is current. */
    fun newerRelease(): Release? {
        val json = JSONObject(get("https://api.github.com/repos/$REPO/releases/latest"))
        val version = json.getString("tag_name").removePrefix("v")
        if (!isNewer(version, APP_VERSION)) return null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".msi")) {
                return Release(version, asset.getString("browser_download_url"))
            }
        }
        return null
    }

    /** Compares dotted numbers: "1.10.0" is newer than "1.9.0". */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun download(release: Release, onProgress: (Float) -> Unit): File {
        val target = File(System.getProperty("java.io.tmpdir"), "ProvisionStore-${release.version}.msi")
        val connection = open(release.msiUrl)
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(if (total > 0) downloaded.toFloat() / total else -1f)
                }
            }
        }
        return target
    }

    /** Starts the Windows installer. The caller should close the app straight after. */
    fun install(msi: File) {
        ProcessBuilder("msiexec", "/i", msi.absolutePath).start()
    }

    private fun get(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun open(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }
}
