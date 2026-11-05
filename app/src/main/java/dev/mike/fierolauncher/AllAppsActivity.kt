package dev.mike.fierolauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.TextView
import dev.mike.fierolauncher.ui.KeyAction
import dev.mike.fierolauncher.ui.KeyActionHandler
import dev.mike.fierolauncher.ui.KeyRouter
import dev.mike.fierolauncher.ui.ListItem
import dev.mike.fierolauncher.ui.ListScreen
import dev.mike.fierolauncher.ui.RoutedKey
import dev.mike.fierolauncher.ui.say

/**
 * Every launchable app, alphabetical. Digits filter T9-style against the start of any word
 * ("6277" -> Maps); Back deletes a digit, then leaves. Left soft: put the app on a D-pad key.
 */
class AllAppsActivity : Activity(), KeyActionHandler {

    private lateinit var list: ListScreen
    private lateinit var title: TextView
    private lateinit var softRight: TextView

    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var typed = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_apps)
        list = findViewById(R.id.list)
        title = findViewById(R.id.title)
        softRight = findViewById(R.id.soft_right)
        list.setEmptyText("Loading…")
        list.requestFocus()
        load()
    }

    private fun load() {
        val app = applicationContext
        FieroApp.io.execute {
            val apps = Apps.load(app)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                all = apps
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val keep = shown.getOrNull(list.selectedIndex)
        shown = all.filter { it.matchesT9(typed) }
        list.setEmptyText(if (typed.isEmpty()) "No apps" else "No match")
        list.setListItems(shown.map { ListItem(it.label) }, shown.indexOf(keep).coerceAtLeast(0))
        title.text = if (typed.isEmpty()) getString(R.string.all_apps) else "Find: $typed  (${shown.size})"
        softRight.text = if (typed.isEmpty()) "" else "Clear"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        KeyRouter.dispatch(event, SCREEN, this) || super.dispatchKeyEvent(event)

    override fun onKeyAction(key: RoutedKey, event: KeyEvent): Boolean {
        val repeat = event.repeatCount > 0
        when (key.action) {
            KeyAction.UP -> list.move(-1)
            KeyAction.DOWN -> list.move(+1)
            KeyAction.LEFT -> list.page(-1)
            KeyAction.RIGHT -> list.page(+1)
            KeyAction.OK -> if (!repeat) shown.getOrNull(list.selectedIndex)?.let { Apps.launch(this, it.component, it.label) }
            KeyAction.DIGIT -> if (!repeat) {
                typed += key.digit
                applyFilter()
            }
            KeyAction.BACK -> if (!repeat) {
                if (typed.isEmpty()) finish() else {
                    typed = typed.dropLast(1)
                    applyFilter()
                }
            }
            KeyAction.SOFT_RIGHT -> if (!repeat && typed.isNotEmpty()) {
                typed = ""
                applyFilter()
            }
            KeyAction.SOFT_LEFT -> if (!repeat) shown.getOrNull(list.selectedIndex)?.let(::options)
            KeyAction.STAR, KeyAction.POUND, KeyAction.CALL -> return false
        }
        return true
    }

    private fun options(app: AppEntry) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        for (dir in Shortcuts.Dir.entries) {
            val now = Shortcuts.get(this, dir)?.label ?: "—"
            actions += "${dir.arrow} key: $now" to {
                Shortcuts.set(this, dir, Shortcuts.Shortcut(app.label, app.component))
                say("${dir.arrow} ${app.label}")
            }
        }
        actions += "App info" to {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.packageName, null)),
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Put ${app.label} on")
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    companion object {
        private const val SCREEN = "AllApps"
    }
}
