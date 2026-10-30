package com.provisionstore.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.provisionstore.desktop.data.Store
import java.io.File
import com.provisionstore.desktop.ui.DayScreen
import com.provisionstore.desktop.ui.ItemsScreen
import com.provisionstore.desktop.ui.KhataScreen
import com.provisionstore.desktop.ui.SellScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

private enum class Tab(val label: String, val icon: ImageVector) {
    DAY("Day", Icons.Filled.CalendarToday),
    SELL("Sell", Icons.Filled.ShoppingCart),
    ITEMS("Items", Icons.Filled.Category),
    KHATA("Khata", Icons.Filled.Book),
}

fun main() = application {
    val store = remember {
        Store(Store.defaultFile()).also { it.dailyBackup(File(it.file.parentFile, "backups")) }
    }
    Window(
        onCloseRequest = {
            store.close()
            exitApplication()
        },
        title = "Provision Store $APP_VERSION",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
    ) {
        MaterialTheme {
            App(store)
        }
    }
}

@Composable
private fun App(store: Store) {
    var tab by remember { mutableStateOf(Tab.DAY) }
    var message by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Look for a newer installer when the app opens. Stay quiet if the check fails.
    LaunchedEffect(Unit) {
        release = withContext(Dispatchers.IO) { runCatching { Updater.newerRelease() }.getOrNull() }
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Tab.entries.forEach { t ->
                NavigationRailItem(
                    selected = tab == t,
                    onClick = {
                        tab = t
                        message = ""
                    },
                    icon = { Icon(t.icon, contentDescription = t.label) },
                    label = { Text(t.label) },
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxSize().padding(16.dp)) {
            release?.let { r ->
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Version ${r.version} is ready to install.")
                        if (downloading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        } else {
                            Row {
                                Button(onClick = {
                                    scope.launch {
                                        downloading = true
                                        message = "Downloading the update..."
                                        try {
                                            val file = withContext(Dispatchers.IO) { Updater.download(r) { } }
                                            Updater.install(file)
                                            store.close()
                                            exitProcess(0)
                                        } catch (e: Exception) {
                                            downloading = false
                                            message = "Update failed: ${e.message}"
                                        }
                                    }
                                }) { Text("Install now") }
                                Spacer(Modifier.height(0.dp))
                                TextButton(onClick = { release = null }) { Text("Later") }
                            }
                        }
                    }
                }
            }

            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }

            when (tab) {
                Tab.DAY -> DayScreen(store) { message = it }
                Tab.SELL -> SellScreen(store) { message = it }
                Tab.ITEMS -> ItemsScreen(store) { message = it }
                Tab.KHATA -> KhataScreen(store) { message = it }
            }
        }
    }
}
