package ru.psina.mobile.core

import java.io.File

/**
 * Установка клиента: jar + дополнительные моды в instance/mods,
 * плюс инфраструктурные моды (fabric-api, viafabricplus) с Modrinth,
 * если манифест помечает клиент как requires.
 */
object Installer {

    data class Progress(val stage: String, val percent: Int, val detail: String = "")

    /** Итог установки — что реально легло в инстанс. */
    data class Result(
        val clientId: String,
        val mc: String,
        val files: List<File>,
        val bytes: Long
    )

    /**
     * Ставит клиента. onProgress вызывается часто; возврат из корутины —
     * через обычный callback, чтобы не тащить корутины в ядро.
     */
    fun install(
        client: ManifestRepo.Client,
        onProgress: (Progress) -> Unit
    ): Result {
        val mods = Paths.modsDir(client.mc)
        val installed = mutableListOf<File>()
        var total = 0L

        // 1) основной jar клиента (если не портативка)
        if (!client.isPortable) {
            if (client.jar.isBlank()) throw IllegalStateException("в манифесте нет ссылки на jar для ${client.id}")
            onProgress(Progress("Скачиваем ${client.name}", 5))
            val name = fileNameOf(client.jar)
            val dst = File(mods, name)
            Net.download(client.jar, dst, client.sha256) { done, len ->
                val pct = if (len > 0) (done * 60 / len).toInt() else 0
                onProgress(Progress("Скачиваем ${client.name}", 5 + pct, "${done / 1048576} МБ"))
            }
            installed.add(dst); total += dst.length()
        } else {
            onProgress(Progress("Портативка ${client.name}", 10, "нужен движок с поддержкой портативок"))
        }

        // 2) дополнительные моды клиента
        val extras = client.extra
        extras.forEachIndexed { i, e ->
            val base = 65 + (i * 20 / extras.size.coerceAtLeast(1))
            onProgress(Progress("Доп. мод ${e.name}", base))
            val dst = File(mods, e.name)
            Net.download(e.url, dst, e.sha256) { done, len ->
                val pct = if (len > 0) (done * 18 / len).toInt() else 0
                onProgress(Progress("Доп. мод ${e.name}", base + pct))
            }
            installed.add(dst); total += dst.length()
        }

        // 3) инфраструктурные моды по requires (Modrinth)
        client.requires.forEachIndexed { i, project ->
            onProgress(Progress("Инфра-мод $project", 88 + i * 4))
            try {
                val url = Modrinth.latestFile(project, client.mc)
                if (url == null) {
                    Logx.i("Modrinth: $project для ${client.mc} не найден — пропуск")
                } else {
                    val dst = File(mods, fileNameOf(url))
                    if (!dst.exists()) {
                        Net.download(url, dst, null)
                        installed.add(dst); total += dst.length()
                    }
                }
            } catch (e: Exception) {
                Logx.e("не удалось добавить $project", e)
            }
        }

        // 4) базовые моды, без которых Fabric-клиент не стартует
        ensureFabricApi(client.mc, mods, installed)

        onProgress(Progress("Готово", 100))
        Store.markInstalled(client.id, client.mc, if (client.isPortable) null else installed.firstOrNull()?.name)
        return Result(client.id, client.mc, installed, total)
    }

    /** fabric-api обязателен для любого Fabric-клиента — ставим, если его нет. */
    private fun ensureFabricApi(mc: String, mods: File, out: MutableList<File>) {
        val has = mods.listFiles()?.any { it.name.contains("fabric-api") } == true
        if (has) return
        try {
            val url = Modrinth.latestFile("fabric-api", mc) ?: return
            val dst = File(mods, fileNameOf(url))
            Net.download(url, dst, null)
            out.add(dst)
            Logx.i("fabric-api добавлен: ${dst.name}")
        } catch (e: Exception) {
            Logx.e("fabric-api не добавлен", e)
        }
    }

    fun fileNameOf(url: String): String {
        var s = url.substringBefore('#').substringBefore('?')
        s = s.substringAfterLast('/')
        s = try {
            java.net.URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) { s }
        s = s.replace(Regex("[^A-Za-z0-9._+\\-]"), "_")
        return s.ifBlank { "file.jar" }
    }
}
