package ru.psina.mobile.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import kotlin.concurrent.thread
import ru.psina.mobile.BuildConfig
import ru.psina.mobile.R
import ru.psina.mobile.core.Engine
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store

/** Экран «Ещё»: настройки, статус движка, занятое место, сайты, лог. */
class MoreScreen(act: Activity) : Screen(act) {

    private lateinit var root: LinearLayout
    private lateinit var engineBox: LinearLayout
    private lateinit var storage: android.widget.TextView

    override fun view(): View {
        root = Ui.column(act)
        root.addView(Ui.title(act, act.getString(R.string.more_title)))
        root.addView(Ui.sub(act, act.getString(R.string.more_sub)))

        // ---- профиль ----
        root.addView(Ui.section(act, "Профиль"))
        val nick = Ui.field(act, "nick", Prefs.nickname, "Player")
        nick.setOnFocusChangeListener { _, has -> if (!has) { Prefs.nickname = nick.text.toString(); Ui.toast(act, "Ник сохранён") } }
        root.addView(Ui.labeled(act, act.getString(R.string.nickname), nick))

        val ramLabel = Ui.text(act, "${act.getString(R.string.ram)}: ${Prefs.ramGb}", 14f)
        val ram = android.widget.SeekBar(act).apply {
            max = 15; progress = (Prefs.ramGb - 1).coerceIn(0, 15)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, f: Boolean) {
                    ramLabel.text = "${act.getString(R.string.ram)}: ${p + 1}"
                    Prefs.ramGb = p + 1
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        root.addView(ramLabel); root.addView(ram)

        val args = Ui.field(act, "javaArgs", Prefs.javaArgs, "-XX:+UseG1GC", numeric = false)
        args.setOnFocusChangeListener { _, has -> if (!has) Prefs.javaArgs = args.text.toString() }
        root.addView(Ui.labeled(act, act.getString(R.string.java_args), args))

        // ---- движок ----
        root.addView(Ui.section(act, act.getString(R.string.engine)))
        engineBox = Ui.column(act, 0f)
        root.addView(engineBox)

        // ---- хранилище ----
        root.addView(Ui.section(act, act.getString(R.string.storage_used)))
        storage = Ui.sub(act, "…")
        root.addView(storage)
        root.addView(Ui.button(act, "Очистить загрузки").apply {
            setOnClickListener {
                Ui.confirm(act, "Очистить загрузки?", "Промежуточные файлы будут удалены.") {
                    Paths.downloads.deleteRecursively(); Paths.downloads.mkdirs()
                    refreshStorage(); Ui.toast(act, "Очищено")
                }
            }
        })

        // ---- манифест ----
        root.addView(Ui.section(act, "Манифест"))
        val url = Ui.field(act, "manifestUrl", Prefs.manifestUrl, "", numeric = false)
        url.setOnFocusChangeListener { _, has -> if (!has) Prefs.manifestUrl = url.text.toString() }
        root.addView(Ui.labeled(act, "URL манифеста", url))
        root.addView(Ui.button(act, "Обновить манифест").apply {
            setOnClickListener {
                thread {
                    val m = Store.loadManifest(true)
                    act.runOnUiThread { Ui.toast(act, "Клиентов: ${m.clients.size}") }
                }
            }
        })

        // ---- сайты ----
        root.addView(Ui.section(act, act.getString(R.string.sites)))
        root.addView(Ui.button(act, "Modrinth").apply {
            setOnClickListener { Engine.openUrl(act, "https://modrinth.com/mods") }
        })
        root.addView(Ui.button(act, "CurseForge").apply {
            setOnClickListener { Engine.openUrl(act, "https://www.curseforge.com/minecraft/search?class=mc-mods") }
        })
        root.addView(Ui.button(act, "PsinaLauncher (GitHub)").apply {
            setOnClickListener { Engine.openUrl(act, "https://github.com/pyBIrtat/PsinaLauncher/releases/latest") }
        })

        // ---- о приложении ----
        root.addView(Ui.section(act, act.getString(R.string.about)))
        root.addView(Ui.sub(act, "Psina Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        root.addView(Ui.sub(act, "Данные: ${Paths.root.absolutePath}"))
        root.addView(Ui.button(act, "Показать лог").apply {
            setOnClickListener {
                val log = Logx.tail(200).ifBlank { "Лог пуст" }
                val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
                val box = Ui.column(act)
                box.addView(Ui.title(act, "Лог"))
                box.addView(Ui.text(act, log, 11.5f, R.color.muted).apply { typeface = android.graphics.Typeface.MONOSPACE })
                box.addView(Ui.button(act, act.getString(R.string.close)).apply { setOnClickListener { dlg.dismiss() } })
                dlg.setContentView(Ui.scroll(act, box))
                dlg.show()
            }
        })

        val sc = Ui.scroll(act, root)
        refreshStorage()
        return sc
    }

    override fun onShown() { refreshEngines(); refreshStorage() }

    private fun refreshEngines() {
        engineBox.removeAllViews()
        val found = Engine.installed(act)
        if (found.isEmpty()) {
            engineBox.addView(Ui.sub(act, act.getString(R.string.engine_missing)))
            engineBox.addView(Ui.sub(act, "Можно экспортировать инстанс архивом и закинуть в движок вручную."))
        } else {
            found.forEach { engineBox.addView(Ui.text(act, "● ${it.title}  (${it.pkg})", 14f, R.color.accent)) }
        }
    }

    private fun refreshStorage() {
        thread {
            val used = Paths.sizeOf(Paths.root)
            val inst = Paths.sizeOf(Paths.instances)
            act.runOnUiThread {
                storage.text = "${used / 1048576} МБ всего · инстансы ${inst / 1048576} МБ"
            }
        }
    }
}
