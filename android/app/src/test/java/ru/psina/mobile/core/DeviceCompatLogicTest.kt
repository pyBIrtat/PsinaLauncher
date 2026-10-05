package ru.psina.mobile.core

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatLogicTest {

    private fun report(
        sdk: Int = 30,
        abis: List<String> = listOf("arm64-v8a"),
        ramMb: Long = 4096,
        freeMb: Long = 2048
    ) = DeviceCompat.Report(sdk, abis, ramMb, freeMb, abis.contains("arm64-v8a"))

    @Test fun `старый Android блокируется`() {
        val spec = AndroidSpec(status = Support.READY, minAndroidApi = 33)
        val err = DeviceCompat.hardBlocker(report(sdk = 26), spec, 0)
        assertTrue(err is LaunchError.UnsupportedAndroid)
    }

    @Test fun `не arm64 блокируется если требуется`() {
        val spec = AndroidSpec(status = Support.READY, architecture = "arm64-v8a")
        val err = DeviceCompat.hardBlocker(report(abis = listOf("armeabi-v7a")), spec, 0)
        assertTrue(err is LaunchError.UnsupportedArch)
    }

    @Test fun `мало места блокируется`() {
        val spec = AndroidSpec(status = Support.READY, estimatedSizeMb = 2000)
        val err = DeviceCompat.hardBlocker(report(freeMb = 500), spec, 2000)
        assertTrue(err is LaunchError.NotEnoughStorage)
    }

    @Test fun `совместимый телефон проходит без ошибок`() {
        val spec = AndroidSpec(status = Support.READY, minAndroidApi = 26, architecture = "arm64-v8a", estimatedSizeMb = 100)
        assertNull(DeviceCompat.hardBlocker(report(), spec, 100))
    }

    @Test fun `мало ОЗУ это предупреждение а не блокировка`() {
        val spec = AndroidSpec(status = Support.READY, requiredMemoryMb = 6000)
        val err = DeviceCompat.hardBlocker(report(ramMb = 3000), spec, 0)
        assertNull(err)
        assertTrue(DeviceCompat.softWarnings(report(ramMb = 3000), spec).isNotEmpty())
    }
}
