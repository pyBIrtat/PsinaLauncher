package ru.psina.mobile.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import ru.psina.mobile.R
import ru.psina.mobile.controls.CtrlLayout
import ru.psina.mobile.controls.LayoutExport
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import ru.psina.mobile.core.Logx
import java.io.File

/** Экран «Управление»: раскладки кнопок, сенса, экспорт/импорт. */
class ControlsScreen(act: Activity) : Screen(act) {

    private lateinit var root: LinearLayout
    private lateinit var layoutsBox: LinearLayout

    override fun view(): View {
        root = Ui.column(act)
        layoutsBox = Ui.column(act, 0f)

        root.addView(Ui.title(act, act.getString(R.string.controls_title)))
        root.addView(Ui.sub(act, act.getString(R.string.controls_sub)))

        // сенса
        val sensLabel = Ui.text(act, "${act.getString(R.string.sensitivity)}: ${"%.2f".format(Prefs.sensitivity)}", 14f)
        val sens = SeekBar(act).apply {
            max = 300
            progress = ((Prefs.sensitivity - 0.5f) / 2.5f * 300).toInt().coerceIn(0, 300)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = 0.5f + p / 300f * 2.5f
                    sensLabel.text = "${act.getString(R.string.sensitivity)}: ${"%.2f".format(v)}"
                    if (fromUser) Prefs.sensitivity = v
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveCurrent() }
            })
        }
        root.addView(Ui.labeled(act, "", sensLabel))
        root.addView(sens)

        // редактор
        root.addView(Ui.button(act, "✛  " + act.getString(R.string.open_editor), primary = true).apply {
            setOnClickListener { openEditor() }
        })

        root.addView(Ui.section(act, "Раскладки"))
        root.addView(layoutsBox)

        root.addView(Ui.button(act, "+ " + act.getString(R.string.new_layout)).apply {
            setOnClickListener { newLayout() }
        })
        root.addView(Ui.button(act, act.getString(R.string.reset_layout)).apply {
            setOnClickListener {
                Ui.confirm(act, "Сбросить раскладку?", "Текущая раскладка вернётся к стандартной.") {
                    current().buttons.clear()
                    current().buttons.addAll(CtrlLayout.default(Prefs.sensitivity).buttons)
                    saveCurrent(); render()
                    Ui.toast(act, "Сброшено")
                }
            }
        })
        root.addView(Ui.button(act, act.getString(R.string.export_layout)).apply {
            setOnClickListener { exportLayout() }
        })
        root.addView(Ui.button(act, act.getString(R.string.import_layout)).apply {
            setOnClickListener { importLayout() }
        })

        val sc = Ui.scroll(act, root)
        render()
        return sc
    }

    override fun onShown() { render() }

    private fun currentId(): String = Prefs.activeLayout

    private fun current(): CtrlLayout {
        val json = Store.readLayout(currentId())
        return if (json != null) {
            try { LayoutExport.fromAny(json) } catch (e: Exception) { CtrlLayout.default(Prefs.sensitivity) }
        } else {
            CtrlLayout.default(Prefs.sensitivity).also { Store.writeLayout(currentId(), it.toJson()) }
        }
    }

    private fun saveCurrent() {
        val l = current()
        l.sensitivity = Prefs.sensitivity
        Store.writeLayout(currentId(), l.toJson())
    }

    private fun render() {
        layoutsBox.removeAllViews()
        val ids = (Store.layoutIds() + currentId()).distinct()
        ids.forEach { id ->
            val on = id == currentId()
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = Ui.rounded(
                    Ui.color(act, if (on) R.color.card2 else R.color.card),
                    Ui.color(act, if (on) R.color.accent else R.color.line), 12f, act
                )
                setPadding(Ui.dp(act, 12f), Ui.dp(act, 10f), Ui.dp(act, 12f), Ui.dp(act, 10f))
                isClickable = true
                setOnClickListener {
                    Prefs.activeLayout = id
                    Ui.toast(act, "Раскладка: $id")
                    render()
                }
            }
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(act, 6f) }

            row.addView(Ui.text(act, (if (on) "● " else "○ ") + id, 15f))
            row.addView(Ui.filler(act))
            val cnt = try {
                val j = Store.readLayout(id)
                if (j != null) LayoutExport.fromAny(j).buttons.size else 0
            } catch (e: Exception) { 0 }
            row.addView(Ui.sub(act, "$cnt кнопок"))
            row.addView(Ui.text(act, "  ✕", 15f, R.color.muted).apply {
                isClickable = true
                setOnClickListener {
                    if (id == "default") { Ui.toast(act, "default удалять нельзя"); return@setOnClickListener }
                    Ui.confirm(act, "Удалить раскладку $id?", "") {
                        Store.deleteLayout(id)
                        if (Prefs.activeLayout == id) Prefs.activeLayout = "default"
                        render()
                    }
                }
            })
            layoutsBox.addView(row)
        }
    }

    private fun openEditor() {
        saveCurrent()
        act.startActivity(Intent(act, ru.psina.mobile.ui.ControlsActivity::class.java))
    }

    private fun newLayout() {
        Ui.prompt(act, "Имя новой раскладки", "layout${Store.layoutIds().size + 1}") { name ->
            val id = Paths.sanitize(name)
            Store.writeLayout(id, CtrlLayout.default(Prefs.sensitivity).also { it.name = id }.toJson())
            Prefs.activeLayout = id
            render()
            Ui.toast(act, "Создана: $id")
        }
    }

    private fun exportLayout() {
        val l = current()
        val dir = Paths.exports.apply { mkdirs() }
        val f = File(dir, "layout-${currentId()}.json")
        f.writeText(l.toJson())
        val pojav = File(dir, "layout-${currentId()}.pojav.json")
        pojav.writeText(LayoutExport.toPojav(l))
        Ui.info(act, "Раскладка сохранена", "${f.name}\n${pojav.name}\n\n(${dir.absolutePath})")
    }

    private fun importLayout() {
        FileImport.pending = { json ->
            try {
                val l = LayoutExport.fromAny(json)
                val id = Paths.sanitize(l.name.ifBlank { "imported" })
                Store.writeLayout(id, l.toJson())
                Prefs.activeLayout = id
                render()
                Ui.toast(act, "Импортировано: $id (${l.buttons.size} кнопок)")
            } catch (e: Exception) {
                Logx.e("импорт раскладки", e)
                Ui.info(act, "Не удалось прочитать раскладку", e.message ?: "")
            }
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try { act.startActivityForResult(i, FileImport.REQ) } catch (e: Exception) {
            Ui.info(act, "Нет файлового менеджера", e.message ?: "")
        }
    }
}
