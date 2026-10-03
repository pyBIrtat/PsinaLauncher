package ru.psina.mobile.controls

import org.json.JSONArray
import org.json.JSONObject

/**
 * Своя модель раскладки кнопок.
 *
 * Позиции хранятся долями экрана (fx, fy в 0..1) — раскладка одинаково
 * выглядит на любом телефоне. Размеры — в dp.
 */
data class CtrlButton(
    val id: String,
    var label: String,
    var keycodes: MutableList<Int> = mutableListOf(),
    var fx: Float = 0.2f,
    var fy: Float = 0.7f,
    var w: Float = 50f,
    var h: Float = 50f,
    var toggle: Boolean = false,
    var opacity: Float = 0.85f,
    var bgColor: Long = 0x4D000000L,
    var strokeColor: Long = 0xFFFFFFFFL,
    var strokeWidth: Float = 0f,
    var cornerRadius: Float = 8f,
    var displayInGame: Boolean = true,
    var displayInMenu: Boolean = false,
    var kind: String = KIND_BUTTON,     // button | joystick
    var forwardLock: Boolean = false,
    var absolute: Boolean = false,
    var special: Boolean = false        // кнопка-действие движка (мышь, клавиатура)
) {
    companion object {
        const val KIND_BUTTON = "button"
        const val KIND_JOYSTICK = "joystick"
    }
}

data class CtrlLayout(
    var name: String = "default",
    var sensitivity: Float = 1.35f,
    var buttons: MutableList<CtrlButton> = mutableListOf()
) {

    fun toJson(): String {
        val o = JSONObject()
        o.put("psina", 1)
        o.put("name", name)
        o.put("sensitivity", sensitivity.toDouble())
        val arr = JSONArray()
        buttons.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id); put("label", b.label)
                put("keycodes", JSONArray(b.keycodes.toList()))
                put("fx", b.fx.toDouble()); put("fy", b.fy.toDouble())
                put("w", b.w.toDouble()); put("h", b.h.toDouble())
                put("toggle", b.toggle); put("opacity", b.opacity.toDouble())
                put("bg", b.bgColor); put("stroke", b.strokeColor)
                put("strokeWidth", b.strokeWidth.toDouble()); put("corner", b.cornerRadius.toDouble())
                put("displayInGame", b.displayInGame); put("displayInMenu", b.displayInMenu)
                put("kind", b.kind); put("forwardLock", b.forwardLock); put("absolute", b.absolute)
                put("special", b.special)
            })
        }
        o.put("buttons", arr)
        return o.toString(2)
    }

    companion object {

        fun fromJson(json: String): CtrlLayout {
            val o = JSONObject(json)
            val l = CtrlLayout(
                name = o.optString("name", "default"),
                sensitivity = o.optDouble("sensitivity", 1.35).toFloat()
            )
            val arr = o.optJSONArray("buttons") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val kc = b.optJSONArray("keycodes")?.let { a ->
                    (0 until a.length()).map { a.getInt(it) }.toMutableList()
                } ?: mutableListOf()
                l.buttons.add(
                    CtrlButton(
                        id = b.optString("id", "b$i"),
                        label = b.optString("label", "?"),
                        keycodes = kc,
                        fx = b.optDouble("fx", 0.2).toFloat(),
                        fy = b.optDouble("fy", 0.7).toFloat(),
                        w = b.optDouble("w", 50.0).toFloat(),
                        h = b.optDouble("h", 50.0).toFloat(),
                        toggle = b.optBoolean("toggle", false),
                        opacity = b.optDouble("opacity", 0.85).toFloat(),
                        bgColor = b.optLong("bg", 0x4D000000L),
                        strokeColor = b.optLong("stroke", 0xFFFFFFFFL),
                        strokeWidth = b.optDouble("strokeWidth", 0.0).toFloat(),
                        cornerRadius = b.optDouble("corner", 8.0).toFloat(),
                        displayInGame = b.optBoolean("displayInGame", true),
                        displayInMenu = b.optBoolean("displayInMenu", false),
                        kind = b.optString("kind", CtrlButton.KIND_BUTTON),
                        forwardLock = b.optBoolean("forwardLock", false),
                        absolute = b.optBoolean("absolute", false),
                        special = b.optBoolean("special", false)
                    )
                )
            }
            return l
        }

        /**
         * Стандартная раскладка: WASD-крестовина слева, прыжок/присед/инвентарь
         * справа, чат/F5 сверху, мышь и клавиатура — спец-кнопки движка.
         */
        fun default(sensitivity: Float = 1.35f): CtrlLayout {
            val l = CtrlLayout("default", sensitivity)
            fun b(label: String, kc: Int, fx: Float, fy: Float, w: Float = 50f, h: Float = 50f,
                  toggle: Boolean = false, special: Boolean = false): CtrlButton =
                CtrlButton(
                    id = label.lowercase().replace(Regex("[^a-z0-9]+"), "_") + "_" + (l.buttons.size + 1),
                    label = label,
                    keycodes = if (special) mutableListOf(kc) else mutableListOf(kc),
                    fx = fx, fy = fy, w = w, h = h, toggle = toggle, special = special
                )

            // спец-кнопки движка
            l.buttons.add(b("МЫШЬ", KEY_VIRTUALMOUSE, 0.86f, 0.06f, 46f, 46f, special = true))
            l.buttons.add(b("ЛКМ", KEY_MOUSEPRI, 0.86f, 0.62f, 58f, 58f, special = true))
            l.buttons.add(b("ПКМ", KEY_MOUSESEC, 0.72f, 0.72f, 52f, 52f, special = true))
            l.buttons.add(b("КЛАВ", KEY_KEYBOARD, 0.06f, 0.06f, 46f, 46f, special = true))
            l.buttons.add(b("МЕНЮ", KEY_TOGGLECTRL, 0.72f, 0.06f, 46f, 46f, special = true))

            // движение
            l.buttons.add(b("W", KEY_W, 0.18f, 0.66f))
            l.buttons.add(b("A", KEY_A, 0.06f, 0.78f))
            l.buttons.add(b("S", KEY_S, 0.18f, 0.80f))
            l.buttons.add(b("D", KEY_D, 0.30f, 0.78f))

            // действия
            l.buttons.add(b("ПРЫЖ", KEY_SPACE, 0.86f, 0.42f, 58f, 58f))
            val shift = b("ШИФТ", KEY_LSHIFT, 0.30f, 0.66f, 50f, 44f, toggle = true)
            l.buttons.add(shift)
            l.buttons.add(b("ИНВ", KEY_E, 0.72f, 0.42f, 48f, 48f))
            l.buttons.add(b("ЧАТ", KEY_T, 0.06f, 0.18f, 52f, 40f))
            l.buttons.add(b("ТАБ", KEY_TAB, 0.24f, 0.18f, 52f, 40f))
            l.buttons.add(b("F5", KEY_F5, 0.42f, 0.18f, 44f, 40f))
            return l
        }

        // GLFW-коды (совпадают с LWJGL, который использует Java-Minecraft)
        const val KEY_SPACE = 32
        const val KEY_A = 65
        const val KEY_D = 68
        const val KEY_E = 69
        const val KEY_S = 83
        const val KEY_T = 84
        const val KEY_W = 87
        const val KEY_TAB = 258
        const val KEY_LSHIFT = 340
        const val KEY_F5 = 294

        // спец-кнопки движка (как в Pojav)
        const val KEY_KEYBOARD = -1
        const val KEY_TOGGLECTRL = -2
        const val KEY_MOUSEPRI = -3
        const val KEY_MOUSESEC = -4
        const val KEY_VIRTUALMOUSE = -5
        const val KEY_MOUSEMID = -6
        const val KEY_SCROLLUP = -7
        const val KEY_SCROLLDOWN = -8
        const val KEY_MENU = -9

        val SPECIAL_LABELS = mapOf(
            KEY_KEYBOARD to "Клавиатура",
            KEY_TOGGLECTRL to "Скрыть кнопки",
            KEY_MOUSEPRI to "ЛКМ",
            KEY_MOUSESEC to "ПКМ",
            KEY_VIRTUALMOUSE to "Курсор",
            KEY_MOUSEMID to "СКМ",
            KEY_SCROLLUP to "Скролл ↑",
            KEY_SCROLLDOWN to "Скролл ↓",
            KEY_MENU to "Меню"
        )

        val KEY_NAMES: Map<Int, String> = mapOf(
            32 to "SPACE", 65 to "A", 66 to "B", 67 to "C", 68 to "D", 69 to "E", 70 to "F", 71 to "G",
            72 to "H", 73 to "I", 74 to "J", 75 to "K", 76 to "L", 77 to "M", 78 to "N", 79 to "O",
            80 to "P", 81 to "Q", 82 to "R", 83 to "S", 84 to "T", 85 to "U", 86 to "V", 87 to "W",
            88 to "X", 89 to "Y", 90 to "Z", 48 to "0", 49 to "1", 50 to "2", 51 to "3", 52 to "4",
            53 to "5", 54 to "6", 55 to "7", 56 to "8", 57 to "9", 258 to "TAB", 256 to "ESC",
            257 to "ENTER", 259 to "BACKSPACE", 340 to "SHIFT", 341 to "CTRL", 342 to "ALT",
            290 to "F1", 291 to "F2", 292 to "F3", 293 to "F4", 294 to "F5", 295 to "F6", 296 to "F7",
            297 to "F8", 298 to "F9", 299 to "F10", 300 to "F11", 301 to "F12", 44 to ",", 46 to "."
        )

        fun keyName(kc: Int): String = SPECIAL_LABELS[kc] ?: KEY_NAMES[kc] ?: "код $kc"
    }
}
