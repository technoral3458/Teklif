package com.teknoral.parametrik.ui.nav

import android.net.Uri

object Routes {
    const val SERVER = "sunucu"
    const val LOGIN = "giris"
    const val JOBS = "isler"
    const val CAMERA = "kamera"
    const val CROP = "kirp/{uri}"
    const val PARAMS = "parametre/{jobId}"
    const val RESULT = "sonuc/{jobId}"

    fun crop(uri: Uri): String = "kirp/${Uri.encode(uri.toString())}"
    fun params(jobId: Long): String = "parametre/$jobId"
    fun result(jobId: Long): String = "sonuc/$jobId"
}
