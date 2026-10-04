package ru.psina.mobile.controls

import org.json.JSONArray
import org.json.JSONObject

/**
 * Экспорт раскладки в формат v8 (PojavLauncher и совместимые движки).
 *
 * Формат — то, что читают Zalith / Mojo / Amethyst:
 * {
 *   "version": 8, "scaledAt": 100,
 *   "mControlDataList": [ { name, keycodes[4], dynamicX, dynamicY, width, height,
 *                           isToggle, opacity, bgColor, strokeColor, strokeWidth,
 *                           cornerRadius, isSwipeable, displayInGame, displayInMenu,
 *                           passThruEnabled } ],
 *   "mDrawerDataList": [],
 *   "mJoystickDataList": [ { ...те же поля..., forwardLock, absolute } ]
 * }
 *
 * Позиции движок принимает как выражения над ${screen_width}/${screen_height},
 * поэтому доли экрана превращаем в "0.18 * ${screen_width}".
 */
object LayoutExport {

    const val POJAV_VERSION = 8

    fun toPojav(layout: CtrlLayout): String {
        val root = JSONObject()
        root.put("version", POJAV_VERSION)
        root.put("scaledAt", 100)

        val buttons = JSONArray()
        val joysticks = JSONArray()

        layout.buttons.forEach { b ->
            val o = buttonJson(b)
            if (b.kind == CtrlButton.KIND_JOYSTICK) {
                o.put("forwardLock", b.forwardLock)
                o.put("absolute", b.absolute)
                joysticks.put(o)
            } else {
                buttons.put(o)
            }
        }

        root.put("mControlDataList", buttons)
        root.put("mDrawerDataList", JSONArray())
        root.put("mJoystickDataList", joysticks)
        return root.toString(2)
    }

    private fun buttonJson(b: CtrlButton): JSONObject {
        val kc = IntArray(4) { -1 }
        b.keycodes.take(4).forEachIndexed { i, v -> kc[i] = v }
        return JSONObject().apply {
            put("name", b.label)
            put("keycodes", JSONArray(kc.toList()))
            put("dynamicX", "${b.fx} * \${screen_width}")
            put("dynamicY", "${b.fy} * \${screen_height}")
            put("width", b.w.toDouble())
            put("height", b.h.toDouble())
            put("isToggle", b.toggle)
            put("opacity", b.opacity.toDouble())
            put("bgColor", b.bgColor)
            put("strokeColor", b.strokeColor)
            put("strokeWidth", b.strokeWidth.toDouble())
            put("cornerRadius", b.cornerRadius.toDouble())
            put("isSwipeable", true)
            put("displayInGame", b.displayInGame)
            put("displayInMenu", b.displayInMenu)
            put("passThruEnabled", b.special && b.keycodes.firstOrNull() == CtrlLayout.KEY_VIRTUALMOUSE)
        }
    }

    /** Читает раскладку в формате v8 (или нашу) — для импорта из файла. */
    fun fromAny(json: String): CtrlLayout {
        val o = JSONObject(json)
        // наша раскладка?
        if (o.has("psina")) return CtrlLayout.fromJson(json)

        val layout = CtrlLayout(o.optString("name", "imported"), 1.35f)
        val v = o.optInt("version", 8)
        if (v != POJAV_VERSION) {
            // более старые версии тоже разложены в те же списки — читаем как есть
        }

        fun readList(key: String, joystick: Boolean) {
            val arr = o.optJSONArray(key) ?: return
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val kc = b.optJSONArray("keycodes")?.let { a ->
                    val raw = (0 until a.length()).map { a.getInt(it) }
                    // -1 — это и паддинг, и спец-кнопка «Клавиатура» (SPECIALBTN_KEYBOARD).
                    // Если ВСЕ значения -1, значит это клавиатура; иначе -1 — только паддинг.
                    if (raw.isNotEmpty() && raw.all { it == -1 }) mutableListOf(-1)
                    else raw.filter { it != -1 }.toMutableList()
                } ?: mutableListOf()
                layout.buttons.add(
                    CtrlButton(
                        id = "imp_$key$i",
                        label = b.optString("name", "?"),
                        keycodes = kc,
                        fx = parseDynamic(b.optString("dynamicX"), 0.2f),
                        fy = parseDynamic(b.optString("dynamicY"), 0.7f),
                        w = b.optDouble("width", 50.0).toFloat(),
                        h = b.optDouble("height", 50.0).toFloat(),
                        toggle = b.optBoolean("isToggle", false),
                        opacity = b.optDouble("opacity", 0.85).toFloat(),
                        bgColor = b.optLong("bgColor", 0x4D000000L),
                        strokeColor = b.optLong("strokeColor", 0xFFFFFFFFL),
                        strokeWidth = b.optDouble("strokeWidth", 0.0).toFloat(),
                        cornerRadius = b.optDouble("cornerRadius", 8.0).toFloat(),
                        displayInGame = b.optBoolean("displayInGame", true),
                        displayInMenu = b.optBoolean("displayInMenu", false),
                        kind = if (joystick) CtrlButton.KIND_JOYSTICK else CtrlButton.KIND_BUTTON,
                        forwardLock = b.optBoolean("forwardLock", false),
                        absolute = b.optBoolean("absolute", false),
                        special = kc.firstOrNull()?.let { it < 0 } ?: false
                    )
                )
            }
        }

        readList("mControlDataList", false)
        readList("mJoystickDataList", true)
        if (layout.buttons.isEmpty()) readList("mDrawerDataList", false)
        return layout
    }

    /** "0.18 * ${screen_width}" -> 0.18 ; "${margin} * 3 + ${width} * 2" -> не парсится, берём фолбэк. */
    private fun parseDynamic(expr: String, fallback: Float): Float {
        if (expr.isBlank()) return fallback
        val m = Regex("^\\s*([0-9]*\\.?[0-9]+)\\s*\\*").find(expr)
        if (m != null) return m.groupValues[1].toFloatOrNull() ?: fallback
        return expr.trim().toFloatOrNull() ?: fallback
    }
}
