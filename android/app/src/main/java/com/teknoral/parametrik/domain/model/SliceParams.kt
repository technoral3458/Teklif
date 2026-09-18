package com.teknoral.parametrik.domain.model

import com.teknoral.parametrik.core.Numbers

/** 3B model modu dilimleme parametreleri (POST /parametric/{id}/slice). */
data class MeshSliceParams(
    val axis: String = "z",
    val thickness: Double = 18.0,
    val gap: Double = 6.0,
    val scale: Double = 1.0,
    val targetLen: Double = 0.0,
    val simplifyTol: Double = 0.15,
    val holeCount: Int = 0,
    val holeDia: Double = 10.0,
    val frameCount: Int = 0,
    val frameT: Double = 18.0,
    val frameH: Double = 120.0,
    val frameFit: Double = 0.2,
    val sheetW: Double = 2100.0,
    val sheetH: Double = 2800.0,
    val partGap: Double = 15.0
) {
    fun toFields(): Map<String, String> = buildMap {
        put("axis", axis)
        put("thickness", Numbers.toServer(thickness))
        put("gap", Numbers.toServer(gap))
        put("scale", Numbers.toServer(scale))
        put("target_len", Numbers.toServer(targetLen))
        put("simplify_tol", Numbers.toServer(simplifyTol))
        put("hole_count", holeCount.toString())
        put("hole_dia", Numbers.toServer(holeDia))
        put("frame_count", frameCount.toString())
        put("frame_t", Numbers.toServer(frameT))
        put("frame_h", Numbers.toServer(frameH))
        put("frame_fit", Numbers.toServer(frameFit))
        put("sheet_w", Numbers.toServer(sheetW))
        put("sheet_h", Numbers.toServer(sheetH))
        put("part_gap", Numbers.toServer(partGap))
    }
}

/** Resim (kabartma) modu dilimleme parametreleri (POST /parametric/{id}/slice-image). */
data class ImageSliceParams(
    val imgW: Double = 1200.0,
    val imgH: Double = 800.0,
    val imgDepth: Double = 180.0,
    val minDepth: Double = 40.0,
    val thickness: Double = 18.0,
    val gap: Double = 6.0,
    val orient: String = "v",
    val shapeMode: String = "single",
    val invert: Boolean = false,
    val normalize: Boolean = true,
    val smooth: Double = 1.0,
    val simplifyTol: Double = 0.3,
    val holeCount: Int = 0,
    val holeDia: Double = 10.0,
    val frameCount: Int = 0,
    val frameT: Double = 18.0,
    val frameH: Double = 120.0,
    val frameFit: Double = 0.2,
    val sheetW: Double = 2100.0,
    val sheetH: Double = 2800.0,
    val partGap: Double = 15.0
) {
    /** Paneller bu uzunluk boyunca dizilir. */
    val stackSpan: Double get() = if (orient == "h") imgH else imgW

    fun toFields(): Map<String, String> = buildMap {
        put("img_w", Numbers.toServer(imgW))
        put("img_h", Numbers.toServer(imgH))
        put("img_depth", Numbers.toServer(imgDepth))
        put("min_depth", Numbers.toServer(minDepth))
        put("thickness", Numbers.toServer(thickness))
        put("gap", Numbers.toServer(gap))
        put("orient", orient)
        put("shape_mode", shapeMode)
        // Checkbox mantığı: kapalıysa alan hiç gönderilmez.
        if (invert) put("invert", "1")
        if (normalize) put("normalize", "1")
        put("smooth", Numbers.toServer(smooth))
        put("simplify_tol", Numbers.toServer(simplifyTol))
        put("hole_count", holeCount.toString())
        put("hole_dia", Numbers.toServer(holeDia))
        put("frame_count", frameCount.toString())
        put("frame_t", Numbers.toServer(frameT))
        put("frame_h", Numbers.toServer(frameH))
        put("frame_fit", Numbers.toServer(frameFit))
        put("sheet_w", Numbers.toServer(sheetW))
        put("sheet_h", Numbers.toServer(sheetH))
        put("part_gap", Numbers.toServer(partGap))
    }
}
