package ru.psina.mobile.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.psina.mobile.R
import ru.psina.mobile.controls.CtrlButton
import ru.psina.mobile.controls.CtrlLayout
import ru.psina.mobile.controls.LayoutExport
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store

/**
 * Редактор кнопок: перетаскивание, изменение размера, привязка клавиш,
 * действия, прозрачность, переключатели, сенса.
 */
class ControlsActivity : AppCompatActivity() {

    private lateinit var stage: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var layout: CtrlLayout
    private var selected: CtrlButton? = null
    private var tool: String = "select"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = Prefs.activeLayout
        layout = Store.readLayout(id)?.let {
            try { LayoutExport.fromAny(it) } catch (e: Exception) { CtrlLayout.default(Prefs.sensitivity) }
        } ?: CtrlLayout.default(Prefs.sensitivity)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        stage = FrameLayout(this).apply {
            setBackgroundColor(Ui.color(this@ControlsActivity, R.color.bg))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(stage)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.color(this@ControlsActivity, R.color.bg2))
            setPadding(Ui.dp(this@ControlsActivity, 8f), Ui.dp(this@ControlsActivity, 8f),
                Ui.dp(this@ControlsActivity, 8f), Ui.dp(this@ControlsActivity, 8f))
        }
        listOf(
            "select" to "Выбор", "add" to "+ Кнопка", "joy" to "+ Джойстик",
            "sens" to "Сенса", "save" to "Готово"
        ).forEach { (k, label) ->
            val b = Ui.button(this, label, primary = k == "save").apply {
                textSize = 13f
                setOnClickListener { onTool(k) }
            }
            b.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Ui.dp(this@ControlsActivity, 6f) }
            toolbar.addView(b)
        }
        root.addView(toolbar)

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.color(this@ControlsActivity, R.color.card))
            visibility = View.GONE
            setPadding(Ui.dp(this@ControlsActivity, 12f), Ui.dp(this@ControlsActivity, 10f),
                Ui.dp(this@ControlsActivity, 12f), Ui.dp(this@ControlsActivity, 12f))
        }
        root.addView(panel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
        // Ждём первого прохода разметки, чтобы знать реальные размеры сцены.
        stage.post { rebuild() }
    }

    private fun onTool(k: String) {
        when (k) {
            "add" -> { addButton(CtrlButton.KIND_BUTTON); tool = "select" }
            "joy" -> { addButton(CtrlButton.KIND_JOYSTICK); tool = "select" }
            "sens" -> showSens()
            "save" -> { save(); finish() }
            else -> tool = k
        }
    }

    private fun addButton(kind: String) {
        val b = CtrlButton(
            id = "b" + System.currentTimeMillis(),
            label = if (kind == CtrlButton.KIND_JOYSTICK) "ДЖОЙ" else "BTN",
            fx = 0.45f, fy = 0.5f,
            w = if (kind == CtrlButton.KIND_JOYSTICK) 120f else 50f,
            h = if (kind == CtrlButton.KIND_JOYSTICK) 120f else 50f,
            kind = kind
        )
        layout.buttons.add(b)
        selected = b
        rebuild()
        showEdit(b)
    }

    private fun rebuild() {
        stage.removeAllViews()
        val w = stage.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = stage.height.takeIf { it > 0 } ?: (resources.displayMetrics.heightPixels * 0.6f).toInt()
        layout.buttons.forEach { b -> stage.addView(makeView(b, w, h)) }
        save()
    }

    private fun makeView(b: CtrlButton, stageW: Int, stageH: Int): View {
        val density = resources.displayMetrics.density
        val bw = (b.w * density).toInt()
        val bh = (b.h * density).toInt()
        val v = TextView(this).apply {
            text = b.label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Ui.color(this@ControlsActivity, R.color.text))
            background = Ui.rounded(
                (b.opacity * 0xFF).toInt().shl(24) or 0x101410,
                if (b == selected) Ui.color(this@ControlsActivity, R.color.accent) else 0x59FFFFFF,
                b.cornerRadius, this@ControlsActivity,
                strokeDp = if (b == selected) 2f else 1f
            )
            setPadding(Ui.dp(this@ControlsActivity, 2f), 0, Ui.dp(this@ControlsActivity, 2f), 0)
        }
        v.layoutParams = FrameLayout.LayoutParams(bw, bh).apply {
            leftMargin = (b.fx * stageW).toInt()
            topMargin = (b.fy * stageH).toInt()
        }

        var dx = 0f; var dy = 0f
        var startX = 0f; var startY = 0f
        var resizing = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    selected = b
                    startX = b.fx * stageW
                    startY = b.fy * stageH
                    dx = e.rawX; dy = e.rawY
                    resizing = e.x > view.width - Ui.dp(this, 24f) && e.y > view.height - Ui.dp(this, 24f)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (resizing) {
                        b.w = (b.w + (e.rawX - dx) / density).coerceAtLeast(30f)
                        b.h = (b.h + (e.rawY - dy) / density).coerceAtLeast(30f)
                        dx = e.rawX; dy = e.rawY
                        view.layoutParams = FrameLayout.LayoutParams(
                            (b.w * density).toInt(), (b.h * density).toInt()
                        ).apply { leftMargin = startX.toInt(); topMargin = startY.toInt() }
                    } else {
                        val nx = (startX + (e.rawX - dx)).coerceIn(0f, stageW - (b.w * density))
                        val ny = (startY + (e.rawY - dy)).coerceIn(0f, stageH - (b.h * density))
                        b.fx = nx / stageW
                        b.fy = ny / stageH
                        view.layoutParams = FrameLayout.LayoutParams(view.width, view.height).apply {
                            leftMargin = nx.toInt(); topMargin = ny.toInt()
                        }
                    }
                    view.requestLayout()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    showEdit(b)
                    rebuild()
                    true
                }
                else -> false
            }
        }
        v.setOnClickListener { selected = b; showEdit(b); rebuild() }
        return v
    }

    private fun showEdit(b: CtrlButton) {
        panel.removeAllViews()
        panel.visibility = View.VISIBLE
        panel.addView(Ui.title(this, "Кнопка: ${b.label}"))

        // подпись
        val label = Ui.field(this, "label", b.label)
        label.setOnFocusChangeListener { _, has -> if (!has) { b.label = label.text.toString(); rebuild() } }
        panel.addView(Ui.labeled(this, "Подпись", label))

        // клавиша
        val keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val keyText = Ui.text(this, b.keycodes.joinToString("+") { CtrlLayout.keyName(it) }.ifBlank { "не задана" }, 14f, R.color.accent)
        keyRow.addView(keyText)
        keyRow.addView(Ui.filler(this))
        keyRow.addView(Ui.button(this, "Привязать").apply {
            textSize = 13f
            setOnClickListener { bindKey(b) { kc -> b.keycodes.clear(); b.keycodes.add(kc); rebuild(); showEdit(b) } }
        })
        panel.addView(keyRow)

        // спец-действия
        val specials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        CtrlLayout.SPECIAL_LABELS.entries.take(4).forEach { (kc, name) ->
            specials.addView(Ui.button(this, name).apply {
                textSize = 11.5f
                setOnClickListener { b.keycodes.clear(); b.keycodes.add(kc); b.special = true; rebuild(); showEdit(b) }
            }.also {
                it.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = Ui.dp(this@ControlsActivity, 6f) }
            })
        }
        panel.addView(specials)

        // размер
        val sizeLabel = Ui.text(this, "Размер: ${b.w.toInt()}×${b.h.toInt()}", 13f)
        val size = android.widget.SeekBar(this).apply {
            max = 240; progress = b.w.toInt().coerceIn(30, 240)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, f: Boolean) {
                    b.w = p.toFloat(); b.h = p.toFloat()
                    sizeLabel.text = "Размер: ${b.w.toInt()}×${b.h.toInt()}"
                    if (f) rebuild()
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        panel.addView(sizeLabel); panel.addView(size)

        // прозрачность
        val opLabel = Ui.text(this, "Прозрачность: ${"%.2f".format(b.opacity)}", 13f)
        val op = android.widget.SeekBar(this).apply {
            max = 100; progress = (b.opacity * 100).toInt()
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, f: Boolean) {
                    b.opacity = p / 100f
                    opLabel.text = "Прозрачность: ${"%.2f".format(b.opacity)}"
                    if (f) rebuild()
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        panel.addView(opLabel); panel.addView(op)

        // переключатель
        val toggle = android.widget.CheckBox(this).apply {
            text = "Переключатель (toggle)"
            isChecked = b.toggle
            setTextColor(Ui.color(this@ControlsActivity, R.color.text))
            setOnCheckedChangeListener { _, on -> b.toggle = on; rebuild() }
        }
        panel.addView(toggle)

        panel.addView(Ui.button(this, "Удалить кнопку").apply {
            setOnClickListener {
                this@ControlsActivity.layout.buttons.remove(b)
                selected = null
                panel.visibility = View.GONE
                rebuild()
            }
        })
    }

    private fun bindKey(b: CtrlButton, onKey: (Int) -> Unit) {
        val dlg = android.app.Dialog(this, R.style.Theme_Psina_Dialog)
        val box = Ui.column(this)
        box.addView(Ui.title(this, "Клавиша для «${b.label}»"))
        box.addView(Ui.sub(this, "Нажми клавишу на подключённой клавиатуре или выбери из списка."))
        val input = Ui.field(this, "клавиша", "")
        input.setOnKeyListener { _, code, ev ->
            if (ev.action == android.view.KeyEvent.ACTION_DOWN) {
                onKey(code); dlg.dismiss(); true
            } else false
        }
        box.addView(input)
        box.addView(Ui.sub(this, "Частые:"))
        CtrlLayout.KEY_NAMES.entries.take(40).forEach { (kc, name) ->
            box.addView(Ui.button(this, name).apply {
                textSize = 13f
                setOnClickListener { onKey(kc); dlg.dismiss() }
            })
        }
        box.addView(Ui.button(this, "Отмена").apply { setOnClickListener { dlg.dismiss() } })
        dlg.setContentView(Ui.scroll(this, box))
        dlg.show()
        input.requestFocus()
    }

    private fun showSens() {
        val dlg = android.app.Dialog(this, R.style.Theme_Psina_Dialog)
        val box = Ui.column(this)
        box.addView(Ui.title(this, "Сенса: ${"%.2f".format(layout.sensitivity)}"))
        val sb = android.widget.SeekBar(this).apply {
            max = 300; progress = ((layout.sensitivity - 0.5f) / 2.5f * 300).toInt().coerceIn(0, 300)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    layout.sensitivity = 0.5f + p / 300f * 2.5f
                    Prefs.sensitivity = layout.sensitivity
                    (box.getChildAt(0) as TextView).text = "Сенса: ${"%.2f".format(layout.sensitivity)}"
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })
        }
        box.addView(sb)
        box.addView(Ui.button(this, "Готово").apply { setOnClickListener { dlg.dismiss(); save() } })
        dlg.setContentView(Ui.scroll(this, box))
        dlg.show()
    }

    private fun save() {
        layout.sensitivity = Prefs.sensitivity
        Store.writeLayout(Prefs.activeLayout, layout.toJson())
        // сразу пишем и версию для движка — её забирает экспорт инстанса
        Store.writeLayout(Prefs.activeLayout + ".pojav", LayoutExport.toPojav(layout))
        Logx.i("раскладка сохранена: ${Prefs.activeLayout}, кнопок ${layout.buttons.size}")
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    override fun onBackPressed() {
        save()
        super.onBackPressed()
    }
}
