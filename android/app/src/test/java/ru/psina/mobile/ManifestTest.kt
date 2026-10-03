package ru.psina.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.psina.mobile.core.ManifestRepo

class ManifestTest {

    private val sample = """
    {
      "versions": ["1.21.4", "1.21.11", "26.2"],
      "clients": [
        {"id":"rockstar","name":"Rockstar SunShine","mc":"1.21.11","jar":"https://x/rockstar.jar",
         "sha256":"abc","logo":"rockstar"},
        {"id":"dimasik","name":"Dimasik","mc":"26.2","jar":"https://x/dimasik.jar",
         "extra":[{"name":"dimasik-luaj.jar","url":"https://x/luaj.jar","sha256":"def"}]},
        {"id":"nursultan","name":"Nursultan","mc":"1.21.11","jar":"","portable":"nursultan"}
      ]
    }
    """

    @Test
    fun parses_versions_and_clients() {
        val m = ManifestRepo.parse(sample)
        assertEquals(listOf("1.21.4", "1.21.11", "26.2"), m.versions)
        assertEquals(3, m.clients.size)
        assertEquals("Rockstar SunShine", m.clients[0].name)
        assertEquals("abc", m.clients[0].sha256)
    }

    @Test
    fun parses_extras_and_portable() {
        val m = ManifestRepo.parse(sample)
        val dimasik = m.clients.first { it.id == "dimasik" }
        assertEquals(1, dimasik.extra.size)
        assertEquals("dimasik-luaj.jar", dimasik.extra[0].name)
        assertEquals("def", dimasik.extra[0].sha256)

        val nursultan = m.clients.first { it.id == "nursultan" }
        assertTrue(nursultan.isPortable)
        assertEquals("nursultan", nursultan.portable)
    }

    @Test
    fun groups_by_version() {
        val m = ManifestRepo.parse(sample)
        val byVer = m.byVersion()
        assertEquals(0, byVer["1.21.4"]!!.size)
        assertEquals(2, byVer["1.21.11"]!!.size)
        assertEquals(1, byVer["26.2"]!!.size)
    }

    @Test
    fun falls_back_to_client_versions_when_missing() {
        val json = """{"clients":[{"id":"a","name":"A","mc":"1.20","jar":"x"}]}"""
        val m = ManifestRepo.parse(json)
        assertEquals(listOf("1.20"), m.versions)
        assertNotNull(m.clients[0])
    }
}
