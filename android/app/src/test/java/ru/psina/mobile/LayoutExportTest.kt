package ru.psina.mobile

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.psina.mobile.controls.CtrlButton
import ru.psina.mobile.controls.CtrlLayout
import ru.psina.mobile.controls.LayoutExport

class LayoutExportTest {

    @Test
    fun exports_v8_structure() {
        val l = CtrlLayout.default(1.4f)
        val json = JSONObject(LayoutExport.toPojav(l))

        assertEquals(8, json.getInt("version"))
        assertEquals(100, json.getInt("scaledAt"))
        assertTrue(json.getJSONArray("mControlDataList").length() > 0)
        assertTrue(json.has("mDrawerDataList"))
        assertTrue(json.has("mJoystickDataList"))
    }

    @Test
    fun button_has_required_fields() {
        val l = CtrlLayout("t", 1.2f)
        l.buttons.add(CtrlButton(id = "b1", label = "W", keycodes = mutableListOf(87), fx = 0.2f, fy = 0.7f))
        val json = JSONObject(LayoutExport.toPojav(l))
        val b = json.getJSONArray("mControlDataList").getJSONObject(0)

        assertEquals("W", b.getString("name"))
        assertEquals(4, b.getJSONArray("keycodes").length())
        assertEquals(87, b.getJSONArray("keycodes").getInt(0))
        assertEquals(-1, b.getJSONArray("keycodes").getInt(3))
        assertTrue(b.getString("dynamicX").contains("screen_width"))
        assertTrue(b.getString("dynamicY").contains("screen_height"))
        assertTrue(b.has("opacity"))
        assertTrue(b.has("passThruEnabled"))
    }

    @Test
    fun joystick_goes_to_joystick_list() {
        val l = CtrlLayout("t", 1f)
        l.buttons.add(CtrlButton(id = "j", label = "ДЖОЙ", kind = CtrlButton.KIND_JOYSTICK, absolute = true))
        val json = JSONObject(LayoutExport.toPojav(l))
        assertEquals(0, json.getJSONArray("mControlDataList").length())
        assertEquals(1, json.getJSONArray("mJoystickDataList").length())
        assertTrue(json.getJSONArray("mJoystickDataList").getJSONObject(0).getBoolean("absolute"))
    }

    @Test
    fun roundtrip_own_format() {
        val l = CtrlLayout.default(1.5f)
        val back = LayoutExport.fromAny(l.toJson())
        assertEquals(l.buttons.size, back.buttons.size)
        assertEquals(1.5f, back.sensitivity, 0.001f)
    }

    @Test
    fun imports_pojav_layout() {
        val pojav = """
        {"version":8,"scaledAt":100,"mControlDataList":[
          {"name":"W","keycodes":[87,-1,-1,-1],"dynamicX":"0.2 * ${'$'}{screen_width}",
           "dynamicY":"0.7 * ${'$'}{screen_height}","width":50,"height":50,"opacity":1,
           "isToggle":false,"bgColor":1,"strokeColor":2,"strokeWidth":0,"cornerRadius":8,
           "displayInGame":true,"displayInMenu":false,"passThruEnabled":false}],
         "mDrawerDataList":[],"mJoystickDataList":[]}
        """.trimIndent()
        val l = LayoutExport.fromAny(pojav)
        assertEquals(1, l.buttons.size)
        assertEquals("W", l.buttons[0].label)
        assertEquals(87, l.buttons[0].keycodes[0])
        // паддинг -1 не должен превратиться в лишние коды
        assertEquals(1, l.buttons[0].keycodes.size)
        assertEquals(0.2f, l.buttons[0].fx, 0.001f)
        assertEquals(0.7f, l.buttons[0].fy, 0.001f)
    }

    @Test
    fun pojav_keyboard_special_is_kept() {
        // Спец-кнопка «Клавиатура» в Pojav — это keycodes [-1,-1,-1,-1],
        // то же значение, что и паддинг. Не должны её потерять.
        val pojav = """
        {"version":8,"mControlDataList":[
          {"name":"КЛАВ","keycodes":[-1,-1,-1,-1],"dynamicX":"0.06 * ${'$'}{screen_width}",
           "dynamicY":"0.06 * ${'$'}{screen_height}","width":46,"height":46}],
         "mJoystickDataList":[],"mDrawerDataList":[]}
        """.trimIndent()
        val l = LayoutExport.fromAny(pojav)
        assertEquals(1, l.buttons.size)
        assertTrue(l.buttons[0].special)
        assertEquals(listOf(-1), l.buttons[0].keycodes)
    }

    @Test
    fun own_format_roundtrip_keeps_keyboard() {
        val l = CtrlLayout.default(1.35f)
        val back = LayoutExport.fromAny(l.toJson())
        val kb = l.buttons.first { it.keycodes == listOf(-1) }
        val kb2 = back.buttons.first { it.keycodes == listOf(-1) }
        assertEquals(kb.label, kb2.label)
        assertTrue(kb2.special)
    }
}
