package dev.mike.fierolauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FieroApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Drop the cached app list whenever something is installed, removed, updated or toggled.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = Apps.invalidate()
        }, filter)
    }

    companion object {
        /** One background thread for package-manager queries; never block the UI on them. */
        val io: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
