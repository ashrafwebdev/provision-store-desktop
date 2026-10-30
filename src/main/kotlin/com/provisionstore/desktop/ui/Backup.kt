package com.provisionstore.desktop.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Asks where to save a backup copy of the database. Returns null if cancelled. */
fun chooseBackupTarget(): File? {
    val dialog = FileDialog(Frame(), "Save backup", FileDialog.SAVE)
    dialog.file = "provision-backup.db"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** Asks which backup file to restore. Returns null if cancelled. */
fun chooseBackupSource(): File? {
    val dialog = FileDialog(Frame(), "Open backup", FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
