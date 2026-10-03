package ru.psina.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.psina.mobile.core.Installer
import ru.psina.mobile.core.Sha256

class UtilTest {

    @Test
    fun sha256_of_known_string() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            Sha256.of("hello")
        )
    }

    @Test
    fun file_name_from_url_strips_query_and_encodes() {
        assertEquals(
            "sodium-fabric-0.6.13+mc1.21.4.jar",
            Installer.fileNameOf("https://cdn.modrinth.com/data/x/versions/y/sodium-fabric-0.6.13%2Bmc1.21.4.jar?mr_loader=fabric")
        )
    }

    @Test
    fun file_name_plain() {
        assertEquals("rockstar-1.21.11-b8b7aa07.jar",
            Installer.fileNameOf("https://github.com/pyBIrtat/PsinaLauncher/releases/download/v1/rockstar-1.21.11-b8b7aa07.jar"))
    }

    @Test
    fun file_name_never_blank() {
        assertEquals("file.jar", Installer.fileNameOf(""))
    }
}
