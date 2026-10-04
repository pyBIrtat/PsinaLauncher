package ru.psina.mobile.export

import org.json.JSONObject
import ru.psina.mobile.controls.CtrlLayout
import ru.psina.mobile.controls.LayoutExport
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.Prefs
import ru.psina.mobile.core.Store
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Экспорт инстанса в zip-архив, который можно открыть любым движком
 * Java-Minecraft на Android (или распаковать вручную).
 *
 * Внутри:
 *   mods/ <клиент>.jar       — клиент и его моды
 *   controlmap/<layout>.json — раскладка кнопок (формат v8 + наша)
 *   psina-instance.json     — метаданные: ник, ОЗУ, версия, сенса
 *   КАК-ЗАПУСТИТЬ.txt       — короткая инструкция
 */
object InstanceExporter {

    fun exportZip(mc: String, clientId: String, nickname: String, ramGb: Int): File {
        val inst = Paths.instanceDir(mc)
        val outDir = Paths.exports.apply { mkdirs() }
        val zip = File(outDir, "psina-$clientId-$mc.zip")
        zip.delete()

        val layoutId = Prefs.activeLayout
        // Наша раскладка читается как CtrlLayout; для движка отдаём её же в формате v8.
        val layout = Store.readLayout(layoutId)?.let {
            try { LayoutExport.fromAny(it) } catch (e: Exception) { CtrlLayout.default(Prefs.sensitivity) }
        } ?: CtrlLayout.default(Prefs.sensitivity)

        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            // mods
            val mods = Paths.modsDir(mc)
            mods.listFiles()?.filter { it.isFile }?.forEach { f ->
                addFile(zos, "mods/${f.name}", f)
            }
            // config
            val cfg = Paths.configDir(mc)
            cfg.listFiles()?.filter { it.isFile }?.forEach { f ->
                addFile(zos, "config/${f.name}", f)
            }
            // controlmap/<id>.json — формат движка (v8), его читают Zalith/Mojo/Pojav;
            // controlmap/<id>.psina.json — наша раскладка (для повторного импорта в Psina).
            addBytes(zos, "controlmap/$layoutId.json", LayoutExport.toPojav(layout).toByteArray(Charsets.UTF_8))
            Store.readLayout(layoutId)?.let {
                addBytes(zos, "controlmap/$layoutId.psina.json", it.toByteArray(Charsets.UTF_8))
            }

            // метаданные
            val meta = JSONObject().apply {
                put("client", clientId)
                put("mc", mc)
                put("nickname", nickname)
                put("ramGb", ramGb)
                put("sensitivity", Prefs.sensitivity.toDouble())
                put("layout", layoutId)
                put("exportedAt", System.currentTimeMillis())
                put("by", "Psina Mobile")
            }
            addBytes(zos, "psina-instance.json", meta.toString(2).toByteArray(Charsets.UTF_8))
            addBytes(zos, "КАК-ЗАПУСТИТЬ.txt", instructions(clientId, mc, layoutId).toByteArray(Charsets.UTF_8))
        }

        Logx.i("экспорт инстанса: ${zip.absolutePath} (${zip.length() / 1048576} МБ)")
        return zip
    }

    private fun instructions(clientId: String, mc: String, layoutId: String): String = """
        Psina Mobile — инстанс $clientId ($mc)
        ======================================

        1. Открой движок Java-Minecraft (Zalith / Amethyst / Mojo / Pojav).
        2. Создай инстанс под версию $mc с загрузчиком Fabric.
        3. Скопируй содержимое папки mods/ в папку mods/ инстанса.
        4. Раскладку кнопок controlmap/$layoutId.json импортируй
           через «Управление» -> «Импорт раскладки».
        5. Запусти игру.

        Ник: посмотри psina-instance.json, ОЗУ — там же.
        Сенса: подбирается в самой игре.
    """.trimIndent()

    private fun addFile(zos: ZipOutputStream, name: String, f: File) {
        zos.putNextEntry(ZipEntry(name))
        f.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun addBytes(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }
}
