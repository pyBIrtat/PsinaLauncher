package ru.psina.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import okhttp3.Request
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Net
import ru.psina.mobile.core.Paths
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Логотипы клиентов: память -> диск -> сеть (jsdelivr, затем raw github). */
object LogoLoader {

    private val mem = ConcurrentHashMap<String, Bitmap>()
    private val main = Handler(Looper.getMainLooper())
    private val client = okhttp3.OkHttpClient()

    private fun dir(): File = File(Paths.root, "logos").apply { mkdirs() }

    fun file(mc: String, id: String): File = File(dir(), "${Paths.sanitize(mc)}__${Paths.sanitize(id)}.png")

    fun load(mc: String, id: String, into: ImageView) {
        val key = "$mc/$id"
        mem[key]?.let { into.setImageBitmap(it); return }

        val f = file(mc, id)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let {
                mem[key] = it
                into.setImageBitmap(it)
                return
            }
        }

        val mirrors = listOf(
            "https://cdn.jsdelivr.net/gh/pyBIrtat/PsinaLauncher@main/clients/$mc/$id.png",
            "https://raw.githubusercontent.com/pyBIrtat/PsinaLauncher/main/clients/$mc/$id.png"
        )
        Thread {
            for (u in mirrors) {
                try {
                    val req = Request.Builder().url(u).header("User-Agent", Net.UA).build()
                    client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) {
                            val bytes = r.body?.bytes() ?: continue
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                            f.writeBytes(bytes)
                            mem[key] = bmp
                            main.post { into.setImageBitmap(bmp) }
                            return@Thread
                        }
                    }
                } catch (e: Exception) {
                    Logx.i("логотип $key: ${e.message}")
                }
            }
        }.start()
    }
}
