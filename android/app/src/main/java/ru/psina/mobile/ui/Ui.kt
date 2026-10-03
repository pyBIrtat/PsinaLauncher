package ru.psina.mobile.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import ru.psina.mobile.R

/** Хелперы для построения интерфейса кодом (без XML-разметок). */
object Ui {

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    fun color(ctx: Context, res: Int): Int = ContextCompat.getColor(ctx, res)

    fun text(ctx: Context, s: CharSequence, sizeSp: Float = 15f, colorRes: Int = R.color.text): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color(ctx, colorRes))
        }

    fun title(ctx: Context, s: CharSequence): TextView =
        text(ctx, s, 17f, R.color.text).apply { setTypeface(typeface, Typeface.BOLD) }

    fun sub(ctx: Context, s: CharSequence): TextView = text(ctx, s, 12f, R.color.muted)

    fun section(ctx: Context, s: CharSequence): TextView =
        text(ctx, s, 11.5f, R.color.muted).apply {
            letterSpacing = 0.09f
            isAllCaps = true
            setPadding(dp(ctx, 4f), dp(ctx, 14f), 0, dp(ctx, 6f))
        }

    fun button(ctx: Context, label: CharSequence, primary: Boolean = false): Button =
        Button(ctx).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            minHeight = dp(ctx, 42f)
            minimumHeight = dp(ctx, 42f)
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
            background = rounded(
                if (primary) color(ctx, R.color.accent) else color(ctx, R.color.card2),
                if (primary) color(ctx, R.color.accent) else color(ctx, R.color.line2), 12f, ctx
            )
            setTextColor(if (primary) color(ctx, R.color.ink) else color(ctx, R.color.text))
        }

    fun rounded(fill: Int, stroke: Int, radiusDp: Float, ctx: Context, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(ctx, strokeDp), stroke)
        }

    fun card(ctx: Context, children: List<View>, padDp: Float = 12f): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(ctx, R.color.card), color(ctx, R.color.line), 14f, ctx)
            val p = dp(ctx, padDp)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 10f)
            layoutParams = lp
            children.forEach { addView(it) }
        }

    fun hbox(ctx: Context, gapDp: Float = 8f): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (gapDp > 0) {
                // отступы между детьми ставим вручную при добавлении
            }
        }

    fun row(ctx: Context, vararg children: View): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        children.forEachIndexed { i, v ->
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (i > 0) lp.marginStart = dp(ctx, 8f)
            box.addView(v, lp)
        }
        return box
    }

    fun space(ctx: Context, h: Float = 8f): View =
        View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(ctx, h)) }

    fun filler(ctx: Context): View =
        View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

    fun hline(ctx: Context): View =
        View(ctx).apply {
            setBackgroundColor(color(ctx, R.color.line))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1f))
        }

    fun field(ctx: Context, label: String, value: String, hint: String = "",
              numeric: Boolean = false): EditText =
        EditText(ctx).apply {
            setText(value)
            this.hint = hint
            textSize = 15f
            setTextColor(color(ctx, R.color.text))
            setHintTextColor(color(ctx, R.color.muted))
            background = rounded(color(ctx, R.color.bg2), color(ctx, R.color.line2), 11f, ctx)
            setPadding(dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f))
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
            tag = label
        }

    fun labeled(ctx: Context, label: String, view: View): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(sub(ctx, label).apply { setPadding(0, dp(ctx, 8f), 0, dp(ctx, 4f)) })
            addView(view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

    fun progress(ctx: Context): ProgressBar =
        ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(color(ctx, R.color.accent))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 6f))
        }

    fun scroll(ctx: Context, content: View): ScrollView =
        ScrollView(ctx).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

    fun column(ctx: Context, padDp: Float = 12f): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, padDp)
            setPadding(p, p, p, dp(ctx, 24f))
        }

    fun image(ctx: Context, wDp: Float, hDp: Float): ImageView =
        ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(color(ctx, R.color.bg2), color(ctx, R.color.line), 6f, ctx)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, wDp), dp(ctx, hDp))
        }

    // ---------- диалоги ----------

    fun info(ctx: Context, title: String, message: String) {
        AlertDialog.Builder(ctx, R.style.Theme_Psina_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ок", null)
            .show()
    }

    fun confirm(ctx: Context, title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(ctx, R.style.Theme_Psina_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Да") { _, _ -> onYes() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun prompt(ctx: Context, title: String, value: String, onOk: (String) -> Unit) {
        val input = EditText(ctx).apply {
            setText(value)
            setTextColor(color(ctx, R.color.text))
            background = rounded(color(ctx, R.color.bg2), color(ctx, R.color.line2), 11f, ctx)
            setPadding(dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f))
        }
        val wrap = FrameLayout(ctx).apply { setPadding(dp(ctx, 20f), dp(ctx, 4f), dp(ctx, 20f), 0); addView(input) }
        AlertDialog.Builder(ctx, R.style.Theme_Psina_Dialog)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Ок") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    /** Карточка клиента: логотип, название, подпись, статус. */
    fun statusDot(ctx: Context, installed: Boolean, partial: Boolean = false): TextView =
        text(
            ctx,
            if (partial) "◐" else if (installed) "●" else "○",
            16f,
            if (partial) R.color.warn else if (installed) R.color.accent else R.color.muted
        )
}
