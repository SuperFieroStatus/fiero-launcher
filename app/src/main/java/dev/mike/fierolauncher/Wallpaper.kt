package dev.mike.fierolauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import dev.mike.fierolauncher.ui.KeyRouter
import java.io.File

/**
 * The TCL Flip 2's outer screen draws the system wallpaper behind its clock, so a black wallpaper gives
 * a black outer screen. (Fiero's home screen is black regardless; the lock screen turns black too.)
 *
 * Driven from adb (HomeActivity extra `wallpaper`):
 *   adb shell am start -n dev.mike.fierolauncher/.HomeActivity --es wallpaper black
 *   adb shell am start -n dev.mike.fierolauncher/.HomeActivity --es wallpaper restore
 * "black" first saves the current wallpaper (needs READ_EXTERNAL_STORAGE, granted over adb) so
 * "restore" can put it back; without a backup, restore resets to the phone's default.
 */
object Wallpaper {
    private fun backup(c: Context) = File(c.filesDir, "wallpaper-backup.png")

    fun apply(context: Context, what: String): String = when (what) {
        "black" -> black(context)
        "restore" -> restore(context)
        else -> "Unknown wallpaper option: $what"
    }

    private fun black(c: Context): String {
        val wm = WallpaperManager.getInstance(c)
        val file = backup(c)
        if (!file.exists()) {
            try {
                val bmp = (wm.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null) file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: SecurityException) {
                Log.w(KeyRouter.TAG, "wallpaper: no backup (storage permission): $e")
            }
        }
        val w = wm.desiredMinimumWidth.takeIf { it > 0 } ?: 480
        val h = wm.desiredMinimumHeight.takeIf { it > 0 } ?: 320
        val black = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).also { Canvas(it).drawColor(Color.BLACK) }
        wm.setBitmap(black, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        return if (file.exists()) "Wallpaper black (old one saved)" else "Wallpaper black (old one not saved)"
    }

    private fun restore(c: Context): String {
        val wm = WallpaperManager.getInstance(c)
        val file = backup(c)
        val bmp = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        return if (bmp != null) {
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            file.delete()
            "Wallpaper restored"
        } else {
            wm.clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            "Wallpaper reset to the phone's default"
        }
    }
}
