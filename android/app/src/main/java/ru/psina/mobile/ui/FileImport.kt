package ru.psina.mobile.ui

/** Мостик для импорта файла раскладки через SAF (ACTION_OPEN_DOCUMENT). */
object FileImport {
    const val REQ = 4701
    var pending: ((String) -> Unit)? = null
}
