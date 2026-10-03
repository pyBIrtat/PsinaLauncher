package ru.psina.mobile

import android.app.Application
import ru.psina.mobile.core.Logx
import ru.psina.mobile.core.Paths
import ru.psina.mobile.core.Prefs

class PsinaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Paths.init(this)
        Prefs.init(this)
        Logx.init(Paths.logs)
        Logx.i("Psina Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Logx.i("Данные: ${Paths.root}")
    }

    companion object {
        lateinit var instance: PsinaApp
            private set
    }
}
