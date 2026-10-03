package ru.psina.mobile.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread
import ru.psina.mobile.LogoLoader
import ru.psina.mobile.R
import ru.psina.mobile.core.Engine
import ru.psina.mobile.core.Installer
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.ManifestRepo
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import ru.psina.mobile.export.InstanceExporter

/** Экран «Клиенты»: версии, список клиентов, установка и запуск. */
class ClientsScreen(act: Activity) : Screen(act) {

    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText

    private var version: String? = null
    private var query: String = ""
    private var manifest: ManifestRepo.Manifest? = null

    override fun view(): View {
        root = Ui.column(act)
        list = Ui.column(act, 0f)
        chips = Ui.hbox(act)
        status = Ui.sub(act, "Загрузка манифеста…")
        search = Ui.field(act, "q", "", act.getString(R.string.search_hint), numeric = false)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString() ?: ""
                render()
            }
        })

        root.addView(status)
        root.addView(chips)
        root.addView(search)
        root.addView(list)

        val sc = Ui.scroll(act, root)
        refresh()
        return sc
    }

    override fun onShown() { render() }

    private fun refresh() {
        status.text = "Загрузка манифеста…"
        thread {
            val m = try {
                Store.loadManifest(true)
            } catch (e: Exception) {
                Logx.e("манифест не загружен", e)
                Store.loadManifest(false)
            }
            act.runOnUiThread {
                manifest = m
                version = version ?: m.versions.firstOrNull()
                status.text = act.getString(R.string.clients_sub, m.clients.size, m.versions.size)
                buildChips(m.versions)
                render()
            }
        }
    }

    private fun buildChips(versions: List<String>) {
        chips.removeAllViews()
        val all = listOf<String?>(null) + versions
        all.forEachIndexed { i, v ->
            val on = version == v
            val label = v ?: act.getString(R.string.all_versions)
            val t = Ui.text(act, label, 13f, if (on) R.color.ink else R.color.text).apply {
                setPadding(Ui.dp(act, 14f), Ui.dp(act, 7f), Ui.dp(act, 14f), Ui.dp(act, 7f))
                background = act.getDrawable(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
                isClickable = true
                setOnClickListener { version = v; buildChips(versions); render() }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (i > 0) lp.marginStart = Ui.dp(act, 6f)
            chips.addView(t, lp)
        }
    }

    private fun render() {
        val m = manifest ?: return
        list.removeAllViews()
        val filtered = m.clients.filter { c ->
            (version == null || c.mc == version) &&
                (query.isBlank() || c.name.contains(query, true) || c.id.contains(query, true))
        }
        if (filtered.isEmpty()) {
            list.addView(Ui.text(act, "Ничего не найдено", 14f, R.color.muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(act, 32f), 0, 0)
            })
            return
        }
        val byVer = filtered.groupBy { it.mc }
        m.versions.forEach { v ->
            val group = byVer[v] ?: return@forEach
            list.addView(Ui.section(act, "$v · ${group.size}"))
            group.forEach { list.addView(clientRow(it)) }
        }
    }

    private fun clientRow(c: ManifestRepo.Client): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.color(act, R.color.card), Ui.color(act, R.color.line), 14f, act)
            setPadding(Ui.dp(act, 10f), Ui.dp(act, 10f), Ui.dp(act, 10f), Ui.dp(act, 10f))
            isClickable = true
            setOnClickListener { openClient(c) }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = Ui.dp(act, 8f)
        card.layoutParams = lp

        val logo = Ui.image(act, 64f, 22f)
        if (c.logo.isNotBlank()) LogoLoader.load(c.mc, c.logo, logo)
        card.addView(logo)

        val meta = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val mlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mlp.marginStart = Ui.dp(act, 12f)
        meta.layoutParams = mlp
        meta.addView(Ui.text(act, c.name, 15f).apply { setTypeface(typeface, Typeface.BOLD) })
        meta.addView(Ui.sub(act, "${c.mc} · ${c.id}"))
        if (c.isPortable) meta.addView(Ui.sub(act, "портативка · ${c.portable}"))
        if (c.requires.isNotEmpty()) meta.addView(Ui.sub(act, "требует: ${c.requires.joinToString()}"))
        card.addView(meta)

        val installed = Store.isInstalled(c.id)
        card.addView(Ui.text(act, if (installed) "●" else "○", 16f,
            if (installed) R.color.accent else R.color.muted))
        return card
    }

    private fun openClient(c: ManifestRepo.Client) {
        val installed = Store.isInstalled(c.id)
        val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
        val sheet = Ui.column(act)
        sheet.addView(Ui.title(act, c.name))
        sheet.addView(Ui.sub(act, "версия ${c.mc}"))
        sheet.addView(Ui.sub(act, if (c.isPortable) "портативка" else "fabric-клиент"))
        if (c.extra.isNotEmpty()) sheet.addView(Ui.sub(act, "доп. моды: " + c.extra.joinToString { it.name }))
        Store.installedInfo(c.id)?.let { info ->
            sheet.addView(Ui.sub(act, "файлов: ${info.files} · ${info.bytes / 1048576} МБ"))
        }

        if (!installed) {
            sheet.addView(Ui.button(act, act.getString(R.string.install), primary = true).apply {
                setOnClickListener { dlg.dismiss(); install(c) }
            })
        } else {
            sheet.addView(Ui.button(act, act.getString(R.string.play), primary = true).apply {
                setOnClickListener { dlg.dismiss(); play(c) }
            })
            sheet.addView(Ui.button(act, act.getString(R.string.export_instance)).apply {
                setOnClickListener { dlg.dismiss(); exportInstance(c) }
            })
            sheet.addView(Ui.button(act, act.getString(R.string.reinstall)).apply {
                setOnClickListener { dlg.dismiss(); install(c) }
            })
            sheet.addView(Ui.button(act, act.getString(R.string.remove)).apply {
                setOnClickListener {
                    dlg.dismiss()
                    Ui.confirm(act, "Удалить ${c.name}?", "Файлы инстанса будут удалены.") {
                        Store.uninstall(c.id, c.mc)
                        Ui.toast(act, "Удалено")
                        render()
                    }
                }
            })
        }
        sheet.addView(Ui.button(act, act.getString(R.string.close)).apply { setOnClickListener { dlg.dismiss() } })
        dlg.setContentView(Ui.scroll(act, sheet))
        dlg.show()
    }

    private fun install(c: ManifestRepo.Client) {
        val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
        val box = Ui.column(act)
        val stage = Ui.text(act, "Подготовка…", 15f)
        val detail = Ui.sub(act, "")
        val bar = Ui.progress(act)
        box.addView(Ui.title(act, "Установка ${c.name}"))
        box.addView(stage)
        box.addView(detail)
        box.addView(bar)
        dlg.setContentView(Ui.scroll(act, box))
        dlg.setCancelable(false)
        dlg.show()

        thread {
            try {
                val res = Installer.install(c) { p ->
                    act.runOnUiThread {
                        stage.text = p.stage
                        detail.text = p.detail
                        bar.progress = p.percent
                    }
                }
                act.runOnUiThread {
                    dlg.dismiss()
                    Ui.toast(act, "Готово: ${res.files.size} файлов, ${res.bytes / 1048576} МБ")
                    render()
                }
            } catch (e: Exception) {
                Logx.e("установка ${c.id} не удалась", e)
                act.runOnUiThread {
                    dlg.dismiss()
                    Ui.info(act, "Ошибка установки", e.message ?: e.toString())
                }
            }
        }
    }

    private fun play(c: ManifestRepo.Client) {
        val engines = Engine.installed(act)
        if (engines.isEmpty()) {
            Ui.confirm(
                act, "Движок не найден",
                "На телефоне нет движка Java-Minecraft. Экспортировать инстанс архивом, " +
                    "чтобы закинуть в движок вручную?"
            ) { exportInstance(c) }
            return
        }
        val names = engines.map { it.title }.toTypedArray()
        android.app.AlertDialog.Builder(act, R.style.Theme_Psina_Dialog)
            .setTitle("Запустить через")
            .setItems(names) { _, i ->
                val dir = ru.psina.mobile.core.Paths.instanceDir(c.mc)
                val ok = Engine.launch(act, engines[i], dir, c.mc, Prefs.nickname, Prefs.ramGb)
                if (!ok) Ui.info(act, "Не удалось запустить", "Экспортируй инстанс архивом.")
            }
            .show()
    }

    private fun exportInstance(c: ManifestRepo.Client) {
        thread {
            try {
                val zip = InstanceExporter.exportZip(c.mc, c.id, Prefs.nickname, Prefs.ramGb)
                act.runOnUiThread {
                    Ui.toast(act, "Экспорт: ${zip.name}")
                    Engine.share(act, zip, "Psina ${c.name} ${c.mc}")
                }
            } catch (e: Exception) {
                Logx.e("экспорт не удался", e)
                act.runOnUiThread { Ui.info(act, "Ошибка экспорта", e.message ?: e.toString()) }
            }
        }
    }
}
