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
import ru.psina.mobile.core.AndroidCompat
import ru.psina.mobile.core.Engine
import ru.psina.mobile.core.Installer
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.ManifestRepo
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.PlayPipeline
import ru.psina.mobile.core.PlayState
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import ru.psina.mobile.export.InstanceExporter
import java.io.File

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

    /** Показывать ли клиентов, которые на телефоне не поедут. */
    private var showPcOnly = false

    private lateinit var pcToggle: TextView

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

        pcToggle = Ui.text(act, "", 12f, R.color.muted).apply {
            setPadding(Ui.dp(act, 12f), Ui.dp(act, 8f), Ui.dp(act, 12f), Ui.dp(act, 8f))
            background = act.getDrawable(R.drawable.bg_chip)
            isClickable = true
            setOnClickListener { showPcOnly = !showPcOnly; updatePcToggle(); render() }
        }

        root.addView(status)
        root.addView(chips)
        root.addView(search)
        root.addView(pcToggle)
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

    private fun updatePcToggle() {
        val hidden = (manifest?.clients ?: emptyList()).count { !it.playableOnPhone }
        pcToggle.text = if (showPcOnly) "Скрыть «Только ПК» ($hidden)" else "Показать «Только ПК» ($hidden)"
    }

    private fun render() {
        val m = manifest ?: return
        list.removeAllViews()
        updatePcToggle()
        val filtered = m.clients.filter { c ->
            (version == null || c.mc == version) &&
                (showPcOnly || c.playableOnPhone) &&
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

        val spec = AndroidCompat.specOf(c)
        val tagColor = when (spec.status) {
            ru.psina.mobile.core.Support.READY -> R.color.accent
            ru.psina.mobile.core.Support.EXPERIMENTAL -> R.color.warn
            ru.psina.mobile.core.Support.PC_ONLY -> R.color.muted
        }
        meta.addView(Ui.text(act, AndroidCompat.statusLabel(spec.status), 11f, tagColor))
        if (spec.status == ru.psina.mobile.core.Support.PC_ONLY && spec.notes.isNotBlank()) {
            meta.addView(Ui.sub(act, spec.notes))
        }
        if (c.requires.isNotEmpty()) meta.addView(Ui.sub(act, "требует: ${c.requires.joinToString()}"))
        card.addView(meta)

        val installed = Store.isInstalled(c.id)
        card.addView(Ui.text(act, if (installed) "●" else "○", 16f,
            if (installed) R.color.accent else R.color.muted))
        return card
    }

    /** Что именно мешает клиенту на телефоне: разбор его jar'а. */
    private fun showScan(c: ManifestRepo.Client) {
        val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
        val box = Ui.column(act)
        val result = Ui.sub(act, "Смотрю содержимое…")
        box.addView(Ui.title(act, "Проверка ${c.name}"))
        box.addView(result)
        box.addView(Ui.button(act, act.getString(R.string.close)).apply {
            setOnClickListener { dlg.dismiss() }
        })
        dlg.setContentView(Ui.scroll(act, box))
        dlg.show()

        thread {
            val text = try {
                val jar = File(Paths.modsDir(c.mc), Installer.fileNameOf(c.jar)).takeIf { it.exists() }
                    ?: Paths.modsDir(c.mc).listFiles()?.firstOrNull { it.name.startsWith(c.id) }
                if (jar == null) {
                    "Сначала установи клиента — тогда посмотрю его jar."
                } else {
                    val r = AndroidCompat.scan(jar)
                    buildString {
                        append(jar.name).append('\n')
                        append("классов: ").append(r.classes)
                        append(", максимум ").append(r.javaLabel).append('\n')
                        append("нативов: ").append(r.natives.size)
                        append(", скриптов: ").append(r.scripts.size).append('\n')
                        r.natives.take(6).forEach { append("  • ").append(it).append('\n') }
                        append('\n')
                        append(AndroidCompat.verdict(r))
                        if (r.findings.isNotEmpty()) {
                            append('\n')
                            r.findings.forEach { f ->
                                append("• ").append(f.why)
                                append(" — ").append(f.count).append(" класс(ов)\n")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logx.e("разбор ${c.id} не удался", e)
                "Не удалось разобрать: ${e.message}"
            }
            act.runOnUiThread { result.text = text }
        }
    }

    private fun openClient(c: ManifestRepo.Client) {
        val installed = Store.isInstalled(c.id)
        val spec = AndroidCompat.specOf(c)
        val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
        val sheet = Ui.column(act)
        sheet.addView(Ui.title(act, c.name))
        sheet.addView(Ui.sub(act, "версия ${c.mc}"))
        sheet.addView(Ui.sub(act, if (c.isPortable) "портативка" else "fabric-клиент"))
        sheet.addView(Ui.sub(act, "телефон: ${AndroidCompat.statusLabel(spec.status)}"))
        if (spec.notes.isNotBlank()) sheet.addView(Ui.sub(act, spec.notes))
        if (c.extra.isNotEmpty()) sheet.addView(Ui.sub(act, "доп. моды: " + c.extra.joinToString { it.name }))
        Store.installedInfo(c.id)?.let { info ->
            sheet.addView(Ui.sub(act, "файлов: ${info.files} · ${info.bytes / 1048576} МБ"))
        }

        if (!spec.isPlayable) {
            sheet.addView(Ui.sub(act, "Этот клиент работает только на ПК — нужен его Windows-рантайм."))
        }

        if (!installed) {
            sheet.addView(Ui.button(act, act.getString(R.string.install), primary = true).apply {
                isEnabled = spec.isPlayable
                setOnClickListener { dlg.dismiss(); install(c) }
            })
        } else {
            sheet.addView(Ui.button(act, act.getString(R.string.play), primary = true).apply {
                setOnClickListener { dlg.dismiss(); play(c) }
            })
            sheet.addView(Ui.button(act, act.getString(R.string.export_instance)).apply {
                setOnClickListener { dlg.dismiss(); exportInstance(c) }
            })
            sheet.addView(Ui.button(act, "Проверить, пойдёт ли на телефоне").apply {
                setOnClickListener { dlg.dismiss(); showScan(c) }
            })
            sheet.addView(Ui.button(act, act.getString(R.string.reinstall)).apply {
                isEnabled = spec.isPlayable
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
        val pipeline = PlayPipeline(act)
        val dlg = android.app.Dialog(act, R.style.Theme_Psina_Dialog)
        val box = Ui.column(act)
        val stage = Ui.text(act, "Подготовка…", 15f)
        val detail = Ui.sub(act, "")
        val bar = Ui.progress(act)
        val cancelBtn = Ui.button(act, "Отмена").apply { setOnClickListener { pipeline.cancelDownload() } }
        box.addView(Ui.title(act, "Играем: ${c.name}"))
        box.addView(stage)
        box.addView(detail)
        box.addView(bar)
        box.addView(cancelBtn)
        dlg.setContentView(Ui.scroll(act, box))
        dlg.setCancelable(false)
        dlg.show()

        pipeline.onState = { state ->
            act.runOnUiThread {
                when (state) {
                    PlayState.LoadingManifest -> { stage.text = "Загружаем манифест"; bar.progress = 0 }
                    PlayState.CheckingPhoneStorage -> stage.text = "Проверяем место на телефоне"
                    PlayState.CheckingAndroidCompatibility -> stage.text = "Проверяем совместимость телефона"
                    PlayState.CheckingFiles -> stage.text = "Проверяем файлы профиля"
                    is PlayState.Downloading -> {
                        stage.text = "Скачиваем"
                        detail.text = state.file
                        bar.progress = state.percent
                    }
                    PlayState.Verifying -> stage.text = "Проверяем целостность (sha256)"
                    PlayState.Installing -> stage.text = "Устанавливаем"
                    PlayState.PreparingMobileProfile -> stage.text = "Готовим мобильный профиль"
                    PlayState.PreparingRuntime -> stage.text = "Ищем движок Java-Minecraft"
                    is PlayState.LaunchingMinecraft -> stage.text = "Передаём профиль в ${state.engineTitle}"
                    is PlayState.LaunchRequestSent -> {
                        dlg.dismiss()
                        Ui.info(
                            act, "Профиль передан в ${state.engineTitle}",
                            "Файлы скачаны, проверены и готовы. Мы отправили запрос на запуск, но " +
                                "${state.engineTitle} не даёт способа подтвердить, что Minecraft правда " +
                                "открылся — это ограничение самого движка, а не PsinaLauncher. " +
                                "Открой ${state.engineTitle}, найди версию/профиль «${c.mc}» и запусти " +
                                "её, если игра не появилась сама."
                        )
                    }
                    PlayState.MinecraftExited -> dlg.dismiss()
                    is PlayState.LaunchFailed -> {
                        dlg.dismiss()
                        Ui.info(act, state.error.title, "${state.error.reason}\n\n${state.error.whatToDo}")
                    }
                    else -> {}
                }
            }
        }
        thread { pipeline.run(c.id, Prefs.nickname, Prefs.ramGb) }
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
