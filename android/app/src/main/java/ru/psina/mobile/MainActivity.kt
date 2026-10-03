package ru.psina.mobile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import ru.psina.mobile.ui.ClientsScreen
import ru.psina.mobile.ui.ControlsScreen
import ru.psina.mobile.ui.ModsScreen
import ru.psina.mobile.ui.MoreScreen
import ru.psina.mobile.ui.Screen
import ru.psina.mobile.ui.ServersScreen
import ru.psina.mobile.ui.Ui

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    private val screens = mutableMapOf<String, Screen>()

    private data class Tab(val key: String, val icon: String, val titleRes: Int)

    private val tabs = listOf(
        Tab("clients", "▦", R.string.tab_clients),
        Tab("controls", "✛", R.string.tab_controls),
        Tab("servers", "◍", R.string.tab_servers),
        Tab("mods", "⧉", R.string.tab_mods),
        Tab("more", "⋯", R.string.tab_more)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        Store.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.color(this@MainActivity, R.color.bg))
        }

        content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(content)

        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.color(this@MainActivity, R.color.bg2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@MainActivity, 62f)
            )
        }
        root.addView(tabBar)

        setContentView(root)
        buildTabs()
        show(Prefs.clientId?.let { "clients" } ?: "clients")
    }

    private fun buildTabs() {
        tabBar.removeAllViews()
        tabs.forEach { t ->
            val v = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                setOnClickListener { show(t.key) }
            }
            v.addView(Ui.text(this, t.icon, 18f, R.color.muted).apply {
                gravity = Gravity.CENTER
                tag = "icon"
            })
            v.addView(Ui.text(this, getString(t.titleRes), 10.5f, R.color.muted).apply {
                gravity = Gravity.CENTER
                tag = "label"
            })
            tabBar.addView(v)
        }
    }

    private fun markTabs(active: String) {
        for (i in 0 until tabBar.childCount) {
            val v = tabBar.getChildAt(i) as LinearLayout
            val on = tabs[i].key == active
            (v.findViewWithTag<TextView>("icon")).setTextColor(Ui.color(this, if (on) R.color.accent else R.color.muted))
            (v.findViewWithTag<TextView>("label")).setTextColor(Ui.color(this, if (on) R.color.accent else R.color.muted))
        }
    }

    fun show(key: String) {
        val screen = screens.getOrPut(key) {
            when (key) {
                "clients" -> ClientsScreen(this)
                "controls" -> ControlsScreen(this)
                "servers" -> ServersScreen(this)
                "mods" -> ModsScreen(this)
                else -> MoreScreen(this)
            }
        }
        content.removeAllViews()
        val v = screen.view()
        v.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        content.addView(v)
        screen.onShown()
        markTabs(key)
    }

    override fun onResume() {
        super.onResume()
        screens["controls"]?.onShown()
        screens["more"]?.onShown()
    }
}
