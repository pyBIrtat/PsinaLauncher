package ru.psina.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import okhttp3.Request
import ru.psina.mobile.core.Net
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.Sha256
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Загрузчик иконок по URL с кэшем в памяти и на диске. */
object IconLoader {

    private val mem = ConcurrentHashMap<String, Bitmap>()
    private val main = Handler(Looper.getMainLooper())
    private val client = okhttp3.OkHttpClient()

    private fun dir(): File = File(Paths.root, "icons").apply { mkdirs() }

    fun load(url: String, into: ImageView) {
        mem[url]?.let { into.setImageBitmap(it); return }
        val f = File(dir(), Sha256.of(url).take(32) + ".img")
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let {
                mem[url] = it; into.setImageBitmap(it); return
            }
        }
        Thread {
            try {
                val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@Thread
                    val bytes = r.body?.bytes() ?: return@Thread
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                    f.writeBytes(bytes)
                    mem[url] = bmp
                    main.post { into.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {
            }
        }.start()
    }
}
