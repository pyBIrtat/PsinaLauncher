package ru.psina.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestAndroidFieldsTest {

    private val json = """
    {
      "versions": ["1.21.11"],
      "clients": [{
        "id": "test", "name": "Test", "mc": "1.21.11", "jar": "x.jar",
        "android": {
          "status": "experimental", "minAndroidApi": 28, "architecture": "arm64-v8a",
          "requiredMemoryMb": 3000, "estimatedSizeMb": 250,
          "requiredEngine": "com.movtery.zalithlauncher"
        }
      }]
    }
    """.trimIndent()

    @Test fun `новые поля android читаются`() {
        val m = ManifestRepo.parse(json)
        val spec = m.clients.first().android!!
        assertEquals(28, spec.minAndroidApi)
        assertEquals("arm64-v8a", spec.architecture)
        assertEquals(3000L, spec.requiredMemoryMb)
        assertEquals(250L, spec.estimatedSizeMb)
        assertEquals("com.movtery.zalithlauncher", spec.requiredEngine)
    }

    @Test fun `манифест без блока android не ломается`() {
        val m = ManifestRepo.parse("""{"versions":["1.0"],"clients":[{"id":"a","mc":"1.0","jar":"a.jar"}]}""")
        val spec = AndroidCompat.specOf(m.clients.first())
        assertEquals(0, spec.minAndroidApi)
        assertEquals(Support.READY, spec.status)
        assertTrue(spec.isPlayable)
    }

    @Test fun `частичный блок android получает нули по умолчанию`() {
        val m = ManifestRepo.parse(
            """{"clients":[{"id":"b","mc":"1.0","jar":"b.jar","android":{"status":"ok","minAndroidApi":26}}]}"""
        )
        val spec = AndroidCompat.specOf(m.clients.first())
        assertEquals(26, spec.minAndroidApi)
        assertEquals(0L, spec.requiredMemoryMb)
        assertEquals(0L, spec.estimatedSizeMb)
        assertEquals(null, spec.requiredEngine)
    }
}
