package com.teknoral.parametrik.ui.params

import com.teknoral.parametrik.core.Numbers
import com.teknoral.parametrik.domain.model.ImageSliceParams
import com.teknoral.parametrik.domain.model.JobDetail
import com.teknoral.parametrik.domain.model.MeshSliceParams

private fun num(text: String, fallback: Double = 0.0): Double = Numbers.parse(text) ?: fallback

/** 3B model modu form durumu — alanlar metin, çünkü kullanıcı virgülle yazıyor. */
data class MeshForm(
    val axis: String = "z",
    val thickness: String = "18",
    val gap: String = "6",
    val scale: String = "1",
    val targetLen: String = "0",
    val simplifyTol: String = "0,15",
    val holeCount: Int = 0,
    val holeDia: String = "10",
    val frameCount: Int = 0,
    val frameT: String = "18",
    val frameH: String = "120",
    val frameFit: String = "0,2",
    val sheetW: String = "2100",
    val sheetH: String = "2800",
    val partGap: String = "15"
) {
    fun toParams(): MeshSliceParams = MeshSliceParams(
        axis = axis,
        thickness = num(thickness),
        gap = num(gap),
        scale = num(scale, 1.0),
        targetLen = num(targetLen),
        simplifyTol = num(simplifyTol, 0.15),
        holeCount = holeCount,
        holeDia = num(holeDia, 10.0),
        frameCount = frameCount,
        frameT = num(frameT, 18.0),
        frameH = num(frameH, 120.0),
        frameFit = num(frameFit, 0.2),
        sheetW = num(sheetW, 2100.0),
        sheetH = num(sheetH, 2800.0),
        partGap = num(partGap, 15.0)
    )

    companion object {
        fun from(detail: JobDetail): MeshForm = MeshForm(
            axis = detail.axis.ifBlank { "z" },
            thickness = Numbers.toField(detail.job.thickness.takeIf { it > 0 } ?: 18.0),
            gap = Numbers.toField(detail.job.gap),
            scale = Numbers.toField(detail.scale.takeIf { it > 0 } ?: 1.0),
            targetLen = Numbers.toField(detail.targetLen),
            simplifyTol = Numbers.toField(detail.simplifyTol),
            holeCount = detail.holeCount,
            holeDia = Numbers.toField(detail.holeDia),
            frameCount = detail.frameCount,
            frameT = Numbers.toField(detail.frameT),
            frameH = Numbers.toField(detail.frameH),
            frameFit = Numbers.toField(detail.frameFit),
            sheetW = Numbers.toField(detail.sheetW),
            sheetH = Numbers.toField(detail.sheetH),
            partGap = Numbers.toField(detail.partGap)
        )
    }
}

/** Resim (kabartma) modu form durumu. */
data class ImageForm(
    val imgW: String = "1200",
    val imgH: String = "800",
    val imgDepth: String = "180",
    val minDepth: String = "40",
    val thickness: String = "18",
    val gap: String = "6",
    val orient: String = "v",
    val shapeMode: String = "single",
    val invert: Boolean = false,
    val normalize: Boolean = true,
    val smooth: String = "1",
    val simplifyTol: String = "0,3",
    val holeCount: Int = 0,
    val holeDia: String = "10",
    val frameCount: Int = 0,
    val frameT: String = "18",
    val frameH: String = "120",
    val frameFit: String = "0,2",
    val sheetW: String = "2100",
    val sheetH: String = "2800",
    val partGap: String = "15",
    val keepAspect: Boolean = true,
    val aspectRatio: Double = 1.5
) {
    fun toParams(): ImageSliceParams = ImageSliceParams(
        imgW = num(imgW),
        imgH = num(imgH),
        imgDepth = num(imgDepth),
        minDepth = num(minDepth),
        thickness = num(thickness),
        gap = num(gap),
        orient = orient,
        shapeMode = shapeMode,
        invert = invert,
        normalize = normalize,
        smooth = num(smooth),
        simplifyTol = num(simplifyTol, 0.3),
        holeCount = holeCount,
        holeDia = num(holeDia, 10.0),
        frameCount = frameCount,
        frameT = num(frameT, 18.0),
        frameH = num(frameH, 120.0),
        frameFit = num(frameFit, 0.2),
        sheetW = num(sheetW, 2100.0),
        sheetH = num(sheetH, 2800.0),
        partGap = num(partGap, 15.0)
    )

    /** En girilince yüksekliği oranı koruyarak günceller. */
    fun withWidth(value: String): ImageForm {
        val width = Numbers.parse(value)
        if (!keepAspect || width == null || width <= 0.0 || aspectRatio <= 0.0) {
            return copy(imgW = value)
        }
        return copy(imgW = value, imgH = Numbers.toField(width / aspectRatio))
    }

    /** Yükseklik girilince eni oranı koruyarak günceller. */
    fun withHeight(value: String): ImageForm {
        val height = Numbers.parse(value)
        if (!keepAspect || height == null || height <= 0.0 || aspectRatio <= 0.0) {
            return copy(imgH = value)
        }
        return copy(imgH = value, imgW = Numbers.toField(height * aspectRatio))
    }

    companion object {
        fun from(detail: JobDetail): ImageForm {
            val ratio = if (detail.imgH > 0) detail.imgW / detail.imgH else 1.5
            return ImageForm(
                imgW = Numbers.toField(detail.imgW),
                imgH = Numbers.toField(detail.imgH),
                imgDepth = Numbers.toField(detail.imgDepth),
                minDepth = Numbers.toField(detail.minDepth),
                thickness = Numbers.toField(detail.job.thickness.takeIf { it > 0 } ?: 18.0),
                gap = Numbers.toField(detail.job.gap),
                orient = detail.orient.ifBlank { "v" },
                shapeMode = detail.shapeMode.ifBlank { "single" },
                invert = detail.invert,
                normalize = detail.normalize,
                smooth = Numbers.toField(detail.smooth),
                simplifyTol = Numbers.toField(detail.simplifyTol),
                holeCount = detail.holeCount,
                holeDia = Numbers.toField(detail.holeDia),
                frameCount = detail.frameCount,
                frameT = Numbers.toField(detail.frameT),
                frameH = Numbers.toField(detail.frameH),
                frameFit = Numbers.toField(detail.frameFit),
                sheetW = Numbers.toField(detail.sheetW),
                sheetH = Numbers.toField(detail.sheetH),
                partGap = Numbers.toField(detail.partGap),
                keepAspect = true,
                aspectRatio = ratio
            )
        }
    }
}
