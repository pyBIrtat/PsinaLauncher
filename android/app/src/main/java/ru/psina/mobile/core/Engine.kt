package ru.psina.mobile.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Работа с движком Java-Minecraft на Android.
 *
 * Наше приложение готовит инстанс (mods, controlmap, конфиги) и передаёт
 * запуск установленному движку. Если движка нет — предлагаем экспорт
 * инстанса архивом, который можно закинуть в любой движок вручную.
 */
object Engine {

    data class Known(
        val pkg: String,
        val title: String,
        val launchActivity: String? = null
    )

    val known = listOf(
        Known("com.movtery.zalithlauncher", "Zalith Launcher"),
        Known("org.angelauramc.amethyst", "Amethyst"),
        Known("net.kdt.pojavlaunch", "PojavLauncher"),
        Known("com.mojang.launcher", "Minecraft (официальный)")
    )

    /** Установленные движки на устройстве. */
    fun installed(ctx: Context): List<Known> {
        val pm = ctx.packageManager
        return known.filter { k ->
            try { pm.getPackageInfo(k.pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
        }
    }

    /**
     * Пытается запустить движок. Возвращает true, если удалось отдать Intent.
     * Движки на Android обычно ждут ARG-строку запуска; передаём её через
     * extras + ACTION_VIEW на общий intent. Разные движки по-разному — поэтому
     * пробуем несколько форм и, если не вышло, отдаём файл инстанса.
     */
    fun launch(ctx: Context, engine: Known, instanceDir: File, mc: String, nickname: String, ramGb: Int): Boolean {
        val args = buildArgs(instanceDir, mc, nickname, ramGb)
        Logx.i("launch ${engine.pkg}: ${args.take(160)}")

        // 1) явный запуск активности движка с extras
        engine.launchActivity?.let { act ->
            try {
                val i = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(engine.pkg, act)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("javaArgs", args)
                    putExtra("psina_args", args)
                    putExtra("psina_instance", instanceDir.absolutePath)
                }
                ctx.startActivity(i)
                return true
            } catch (e: Exception) {
                Logx.i("явный запуск не сработал: ${e.message}")
            }
        }

        // 2) общий запуск по пакету
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(engine.pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i.putExtra("javaArgs", args)
                i.putExtra("psina_args", args)
                ctx.startActivity(i)
                return true
            }
        } catch (e: Exception) {
            Logx.i("launch intent не сработал: ${e.message}")
        }
        return false
    }

    /** Строка аргументов запуска, которую понимает движок. */
    fun buildArgs(instanceDir: File, mc: String, nickname: String, ramGb: Int): String {
        val sb = StringBuilder()
        sb.append("-Xmx${ramGb}G -Xms${(ramGb / 2).coerceAtLeast(1)}G ")
        sb.append("-Djava.library.path=${instanceDir.absolutePath}/natives ")
        sb.append("-Dorg.lwjgl.librarypath=${instanceDir.absolutePath}/natives ")
        sb.append("-Dfabric.gameJarPath=${instanceDir.absolutePath}/versions/$mc/$mc.jar ")
        sb.append("--gameDir ${instanceDir.absolutePath} ")
        sb.append("--username $nickname ")
        sb.append("--version $mc ")
        return sb.toString().trim()
    }

    /** Отдаёт zip инстанса через «Поделиться» — закинуть в любой движок. */
    fun share(ctx: Context, zip: File, title: String) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", zip)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Отправить инстанс").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Открывает ссылку в браузере. */
    fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Logx.e("не удалось открыть $url", e)
        }
    }
}
