package com.teknoral.parametrik.domain.logic

import com.teknoral.parametrik.domain.model.ImageSliceParams
import com.teknoral.parametrik.domain.model.MeshSliceParams

/** Doğrulama sonucu: [errors] varsa "Dilimle" kilitli, [warnings] yalnızca uyarı. */
data class ValidationResult(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ParamValidation {

    fun validate(p: MeshSliceParams): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (p.thickness <= 0.0) errors += "Panel kalınlığı 0'dan büyük olmalı."
        if (p.gap < 0.0) errors += "Panel arası boşluk negatif olamaz."
        if (p.targetLen <= 0.0 && p.scale <= 0.0) errors += "Ölçek 0'dan büyük olmalı."
        if (p.simplifyTol < 0.0) errors += "Sadeleştirme toleransı negatif olamaz."
        if (p.holeCount !in 0..4) errors += "Montaj deliği adedi 0 ile 4 arasında olmalı."
        if (p.holeCount > 0 && p.holeDia <= 0.0) errors += "Delik çapı 0'dan büyük olmalı."
        if (p.frameCount !in 0..4) errors += "Geçme kayıt adedi 0 ile 4 arasında olmalı."
        if (p.frameCount > 0 && (p.frameT <= 0.0 || p.frameH <= 0.0))
            errors += "Kayıt kalınlığı ve yüksekliği 0'dan büyük olmalı."
        if (p.sheetW <= 0.0 || p.sheetH <= 0.0) errors += "Plaka ölçüleri 0'dan büyük olmalı."
        if (p.partGap < 0.0) errors += "Parça arası boşluk negatif olamaz."

        if (p.frameCount > 0 && (p.frameFit < 0.1 || p.frameFit > 0.3))
            warnings += "Geçme payı için 0,1 – 0,3 mm önerilir."

        if (p.targetLen > 0.0) {
            val count = PanelEstimator.panelCount(p.targetLen, p.thickness, p.gap)
            if (count > PanelEstimator.MAX_PANELS)
                warnings += "Tahmini $count panel — sunucu sınırı ${PanelEstimator.MAX_PANELS}. " +
                    "Kalınlığı veya boşluğu artırın."
        }
        return ValidationResult(errors, warnings)
    }

    fun validate(p: ImageSliceParams): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (p.imgW <= 0.0) errors += "İşin eni 0'dan büyük olmalı."
        if (p.imgH <= 0.0) errors += "İşin yüksekliği 0'dan büyük olmalı."
        if (p.imgDepth <= 0.0) errors += "Derinlik 0'dan büyük olmalı."
        if (p.minDepth < 0.0) errors += "Taban derinliği negatif olamaz."
        if (p.minDepth >= p.imgDepth) errors += "Taban derinliği, en derin noktadan küçük olmalı."
        if (p.thickness <= 0.0) errors += "Panel kalınlığı 0'dan büyük olmalı."
        if (p.gap < 0.0) errors += "Panel arası boşluk negatif olamaz."
        if (p.smooth < 0.0 || p.smooth > 8.0) errors += "Yumuşatma 0 ile 8 arasında olmalı."
        if (p.simplifyTol < 0.0) errors += "Sadeleştirme toleransı negatif olamaz."
        if (p.holeCount !in 0..4) errors += "Montaj deliği adedi 0 ile 4 arasında olmalı."
        if (p.holeCount > 0 && p.holeDia <= 0.0) errors += "Delik çapı 0'dan büyük olmalı."
        if (p.frameCount !in 0..4) errors += "Geçme kayıt adedi 0 ile 4 arasında olmalı."
        if (p.frameCount > 0 && (p.frameT <= 0.0 || p.frameH <= 0.0))
            errors += "Kayıt kalınlığı ve yüksekliği 0'dan büyük olmalı."
        if (p.sheetW <= 0.0 || p.sheetH <= 0.0) errors += "Plaka ölçüleri 0'dan büyük olmalı."
        if (p.partGap < 0.0) errors += "Parça arası boşluk negatif olamaz."

        if (p.frameCount > 0 && (p.frameFit < 0.1 || p.frameFit > 0.3))
            warnings += "Geçme payı için 0,1 – 0,3 mm önerilir."

        val count = PanelEstimator.panelCount(p.stackSpan, p.thickness, p.gap)
        if (count > PanelEstimator.MAX_PANELS)
            warnings += "Tahmini $count panel — sunucu sınırı ${PanelEstimator.MAX_PANELS}. " +
                "Kalınlığı veya boşluğu artırın."

        return ValidationResult(errors, warnings)
    }
}
