package ru.psina.mobile.ui

import android.app.Activity
import android.view.View

/** Базовый экран: ленивая постройка вида и хук обновления. */
abstract class Screen(protected val act: Activity) {
    abstract fun view(): View
    open fun onShown() {}
}
