package ru.psina.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.psina.mobile.core.AndroidCompat
import ru.psina.mobile.core.Engine
import ru.psina.mobile.core.ManifestRepo
import ru.psina.mobile.core.Support

class AndroidCompatTest {

    private val sample = """
    {
      "versions": ["1.21.4"],
      "clients": [
        {"id":"meow","name":"Meow","mc":"1.21.4","jar":"","portable":"meow","zip":"https://x/meow.zip",
         "android":{"status":"experimental",
                    "modsFromZip":["run/mods/meow-offline.jar","run/mods/voicechat.jar"],
                    "modsExclude":["voicechat"],
                    "jvmArgs":["-Ddeluxe.username={nick}"]}},
        {"id":"nursultan","name":"Nursultan","mc":"1.21.11","jar":"","portable":"nursultan",
         "android":{"status":"pc","notes":"свой бутстрап Main"}},
        {"id":"rockstar","name":"Rockstar","mc":"1.21.11","jar":"https://x/r.jar"}
      ]
    }
    """

    @Test
    fun parses_android_block() {
        val m = ManifestRepo.parse(sample)
        val meow = m.clients.first { it.id == "meow" }
        val spec = AndroidCompat.specOf(meow)
        assertEquals(Support.EXPERIMENTAL, spec.status)
        assertEquals(2, spec.modsFromZip.size)
        assertEquals(listOf("voicechat"), spec.modsExclude)
        assertEquals("-Ddeluxe.username={nick}", spec.jvmArgs.single())
        assertTrue(spec.isPlayable)
    }

    @Test
    fun pc_block_is_not_playable() {
        val m = ManifestRepo.parse(sample)
        val nur = m.clients.first { it.id == "nursultan" }
        val spec = AndroidCompat.specOf(nur)
        assertEquals(Support.PC_ONLY, spec.status)
        assertEquals("свой бутстрап Main", spec.notes)
        assertFalse(nur.playableOnPhone)
    }

    @Test
    fun libs_from_zip_are_separate_from_mods() {
        val json = """{"clients":[{"id":"destra","name":"Destra","mc":"1.21.4","jar":"",
            "portable":"destra","zip":"https://x/d.zip",
            "android":{"status":"experimental","modsFromZip":["run/mods/destra-offline.jar"],
                        "libsFromZip":["runtime/compatibility/offline-compatibility.jar"]}}]}"""
        val spec = AndroidCompat.specOf(ManifestRepo.parse(json).clients.single())
        assertEquals(1, spec.modsFromZip.size)
        assertEquals(1, spec.libsFromZip.size)
        assertEquals("runtime/compatibility/offline-compatibility.jar", spec.libsFromZip.single())
    }

    @Test
    fun portable_without_block_defaults_to_pc_only() {
        val json = """{"clients":[{"id":"x","name":"X","mc":"1.21.4","jar":"","portable":"x"}]}"""
        val c = ManifestRepo.parse(json).clients.single()
        assertNull(c.android)
        assertEquals(Support.PC_ONLY, c.support)
        assertFalse(c.playableOnPhone)
    }

    @Test
    fun fabric_client_without_block_is_ready() {
        val m = ManifestRepo.parse(sample)
        val rock = m.clients.first { it.id == "rockstar" }
        assertEquals(Support.READY, rock.support)
        assertTrue(rock.playableOnPhone)
    }

    @Test
    fun nick_substitution_and_offline_uuid_are_stable() {
        val a = Engine.offlineUuid("Litvin_Tatarstan")
        val b = Engine.offlineUuid("Litvin_Tatarstan")
        assertEquals(a, b)
        assertEquals(36, a.length)
        assertFalse(a == Engine.offlineUuid("other"))
    }
}