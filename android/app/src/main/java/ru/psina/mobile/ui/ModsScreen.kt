package ru.psina.mobile.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread
import ru.psina.mobile.R
import ru.psina.mobile.core.Installer
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Modrinth
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import java.io.File

/** Экран «Моды»: поиск на Modrinth и установка в выбранный инстанс. */
class ModsScreen(act: Activity) : Screen(act) {

    private lateinit var root: LinearLayout
    private lateinit var box: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView

    private var mc: String = "1.21.11"

    override fun view(): View {
        root = Ui.column(act)
        box = Ui.column(act, 0f)
        status = Ui.sub(act, "")
        input = Ui.field(act, "поиск", "", act.getString(R.string.search_hint_mods))
        input.setOnEditorActionListener { _, _, _ -> search(); true }

        root.addView(Ui.title(act, act.getString(R.string.mods_title)))
        root.addView(Ui.sub(act, act.getString(R.string.mods_sub)))

        val ver = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(ver)
        Store.loadManifest(false).versions.forEach { v ->
            val chip = Ui.text(act, v, 13f).apply {
                setPadding(Ui.dp(act, 14f), Ui.dp(act, 7f), Ui.dp(act, 14f), Ui.dp(act, 7f))
                background = act.getDrawable(if (v == mc) R.drawable.bg_chip_on else R.drawable.bg_chip)
                setTextColor(Ui.color(act, if (v == mc) R.color.ink else R.color.text))
                isClickable = true
                setOnClickListener {
                    mc = v
                    ver.removeAllViews()
                    buildVersionChips(ver)
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (ver.childCount > 0) lp.marginStart = Ui.dp(act, 6f)
            ver.addView(chip, lp)
        }

        root.addView(input)
        root.addView(Ui.button(act, "Найти", primary = true).apply { setOnClickListener { search() } })
        root.addView(status)
        root.addView(box)

        val sc = Ui.scroll(act, root)
        return sc
    }

    private fun buildVersionChips(box: LinearLayout) {
        Store.loadManifest(false).versions.forEach { v ->
            val chip = Ui.text(act, v, 13f).apply {
                setPadding(Ui.dp(act, 14f), Ui.dp(act, 7f), Ui.dp(act, 14f), Ui.dp(act, 7f))
                background = act.getDrawable(if (v == mc) R.drawable.bg_chip_on else R.drawable.bg_chip)
                setTextColor(Ui.color(act, if (v == mc) R.color.ink else R.color.text))
                isClickable = true
                setOnClickListener { mc = v; box.removeAllViews(); buildVersionChips(box) }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (box.childCount > 0) lp.marginStart = Ui.dp(act, 6f)
            box.addView(chip, lp)
        }
    }

    private fun search() {
        val q = input.text.toString().trim()
        if (q.isBlank()) { Ui.toast(act, "Введи запрос"); return }
        status.text = "Поиск…"
        box.removeAllViews()
        thread {
            try {
                val hits = Modrinth.search(q, mc)
                act.runOnUiThread {
                    status.text = "Найдено: ${hits.size} для $mc"
                    hits.forEach { box.addView(hitRow(it)) }
                    if (hits.isEmpty()) box.addView(Ui.text(act, "Ничего не найдено", 14f, R.color.muted))
                }
            } catch (e: Exception) {
                Logx.e("поиск Modrinth", e)
                act.runOnUiThread {
                    status.text = act.getString(R.string.mods_offline)
                    status.setTextColor(Ui.color(act, R.color.warn))
                }
            }
        }
    }

    private fun hitRow(hit: Modrinth.ModHit): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.color(act, R.color.card), Ui.color(act, R.color.line), 14f, act)
            setPadding(Ui.dp(act, 10f), Ui.dp(act, 10f), Ui.dp(act, 10f), Ui.dp(act, 10f))
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = Ui.dp(act, 8f) }

        val icon = ImageView(act).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(act, 40f), Ui.dp(act, 40f))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = Ui.rounded(Ui.color(act, R.color.bg2), Ui.color(act, R.color.line), 8f, act)
        }
        hit.iconUrl?.let { u -> ru.psina.mobile.IconLoader.load(u, icon) }
        card.addView(icon)

        val meta = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        meta.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = Ui.dp(act, 10f) }
        meta.addView(Ui.text(act, hit.title, 14.5f))
        meta.addView(Ui.sub(act, hit.description.take(90)))
        meta.addView(Ui.sub(act, "⬇ ${hit.downloads}"))
        card.addView(meta)

        card.addView(Ui.button(act, "＋").apply {
            textSize = 16f
            setOnClickListener { install(hit) }
        })
        return card
    }

    private fun install(hit: Modrinth.ModHit) {
        thread {
            try {
                val url = Modrinth.latestFile(hit.slug, mc)
                if (url == null) {
                    act.runOnUiThread { Ui.info(act, "Нет файла", "Для $mc у ${hit.title} нет сборки под Fabric.") }
                    return@thread
                }
                val dst = File(Paths.modsDir(mc), Installer.fileNameOf(url))
                if (dst.exists()) {
                    act.runOnUiThread { Ui.toast(act, "Уже установлен: ${dst.name}") }
                    return@thread
                }
                ru.psina.mobile.core.Net.download(url, dst, null)
                Logx.i("мод установлен: ${dst.name}")
                act.runOnUiThread { Ui.toast(act, "Установлен: ${hit.title}") }
            } catch (e: Exception) {
                Logx.e("установка мода ${hit.slug}", e)
                act.runOnUiThread { Ui.info(act, "Ошибка", e.message ?: "") }
            }
        }
    }
}
