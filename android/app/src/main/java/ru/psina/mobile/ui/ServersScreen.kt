package ru.psina.mobile.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.concurrent.thread
import ru.psina.mobile.R
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import java.net.InetSocketAddress
import java.net.Socket

/** Экран «Сервера»: адреса, пинг, быстрый выбор активного сервера. */
class ServersScreen(act: Activity) : Screen(act) {

    private lateinit var root: LinearLayout
    private lateinit var box: LinearLayout

    override fun view(): View {
        root = Ui.column(act)
        box = Ui.column(act, 0f)
        root.addView(Ui.title(act, act.getString(R.string.servers_title)))
        root.addView(Ui.sub(act, act.getString(R.string.servers_sub)))
        root.addView(box)
        root.addView(Ui.button(act, "+ " + act.getString(R.string.add_server), primary = true).apply {
            setOnClickListener { edit(null) }
        })
        val sc = Ui.scroll(act, root)
        render()
        return sc
    }

    override fun onShown() { render() }

    private fun render() {
        box.removeAllViews()
        val servers = Store.servers()
        val active = Prefs.serverId
        servers.forEach { s ->
            val on = s.id == active
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(
                    Ui.color(act, if (on) R.color.card2 else R.color.card),
                    Ui.color(act, if (on) R.color.accent else R.color.line), 14f, act
                )
                setPadding(Ui.dp(act, 12f), Ui.dp(act, 12f), Ui.dp(act, 12f), Ui.dp(act, 12f))
                isClickable = true
                setOnClickListener { Prefs.serverId = s.id; render(); Ui.toast(act, "Активен: ${s.name}") }
            }
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(act, 8f) }

            val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(Ui.text(act, (if (on) "● " else "○ ") + s.name, 15f))
            head.addView(Ui.filler(act))
            val ping = Ui.sub(act, "—")
            head.addView(ping)
            card.addView(head)
            card.addView(Ui.sub(act, s.address))

            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(act, 6f) }
            row.addView(Ui.button(act, act.getString(R.string.ping)).apply {
                textSize = 13f
                setOnClickListener { ping(s.host, s.port, ping) }
            })
            row.addView(Ui.button(act, act.getString(R.string.copy_ip)).apply {
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = Ui.dp(act, 8f) }
                setOnClickListener {
                    val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("server", s.address))
                    Ui.toast(act, "Скопировано: ${s.address}")
                }
            })
            row.addView(Ui.button(act, "✎").apply {
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = Ui.dp(act, 8f) }
                setOnClickListener { edit(s) }
            })
            card.addView(row)
            box.addView(card)
        }
    }

    private fun edit(existing: Store.Server?) {
        val name = Ui.field(act, "название", existing?.name ?: "")
        val host = Ui.field(act, "адрес", existing?.host ?: "", "mc.example.com")
        val port = Ui.field(act, "порт", (existing?.port ?: 25565).toString(), "25565", numeric = true)

        val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
        val box = Ui.column(act)
        box.addView(Ui.title(act, if (existing == null) "Новый сервер" else "Изменить сервер"))
        box.addView(Ui.labeled(act, "Название", name))
        box.addView(Ui.labeled(act, "Адрес", host))
        box.addView(Ui.labeled(act, "Порт", port))
        box.addView(Ui.button(act, act.getString(R.string.save), primary = true).apply {
            setOnClickListener {
                val h = host.text.toString().trim()
                if (h.isBlank()) { Ui.toast(act, "Укажи адрес"); return@setOnClickListener }
                val list = Store.servers()
                val id = existing?.id ?: ("s" + System.currentTimeMillis())
                val s = Store.Server(
                    id,
                    name.text.toString().ifBlank { h },
                    h,
                    port.text.toString().toIntOrNull() ?: 25565
                )
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) list[idx] = s else list.add(s)
                Store.saveServers(list)
                dlg.dismiss(); render()
            }
        })
        if (existing != null) {
            box.addView(Ui.button(act, act.getString(R.string.delete)).apply {
                setOnClickListener {
                    val list = Store.servers().filterNot { it.id == existing.id }
                    Store.saveServers(list)
                    if (Prefs.serverId == existing.id) Prefs.serverId = null
                    dlg.dismiss(); render()
                }
            })
        }
        box.addView(Ui.button(act, act.getString(R.string.cancel)).apply { setOnClickListener { dlg.dismiss() } })
        dlg.setContentView(Ui.scroll(act, box))
        dlg.show()
    }

    private fun ping(host: String, port: Int, out: android.widget.TextView) {
        out.text = "…"
        thread {
            val t0 = System.currentTimeMillis()
            val ms = try {
                Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
                System.currentTimeMillis() - t0
            } catch (e: Exception) {
                Logx.i("ping $host:$port -> ${e.message}")
                -1L
            }
            act.runOnUiThread {
                out.text = if (ms < 0) "недоступен" else "$ms мс"
                out.setTextColor(Ui.color(act, if (ms in 0..400) R.color.accent else R.color.warn))
            }
        }
    }
}
