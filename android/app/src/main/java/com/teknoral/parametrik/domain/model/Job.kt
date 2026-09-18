package com.teknoral.parametrik.domain.model

enum class SourceKind {
    MESH, IMAGE;

    val label: String get() = if (this == IMAGE) "Resim" else "3B Model"

    companion object {
        fun from(raw: String?): SourceKind =
            if (raw.equals("image", ignoreCase = true)) IMAGE else MESH
    }
}

/** İş listesindeki bir kayıt. */
data class Job(
    val id: Long,
    val name: String,
    val sourceKind: SourceKind,
    val origName: String,
    val triCount: Int,
    val status: String,
    val panelCount: Int,
    val sheetCount: Int,
    val thickness: Double,
    val gap: Double,
    val createdAt: String
) {
    val isSliced: Boolean get() = panelCount > 0
}

/** Dilimleme sonrası sunucunun döndürdüğü özet. */
data class JobSummary(
    val panelCount: Int,
    val thickness: Double,
    val gap: Double,
    val pitch: Double,
    val stackLen: Double,
    val profileW: Double,
    val profileH: Double,
    val cutLenM: Double,
    val sheetCount: Int,
    val holeCount: Int,
    val holeDia: Double,
    val frameCount: Int,
    val frameT: Double,
    val frameH: Double,
    val frameLen: Double,
    val warnings: List<String>
)

/** İş ayrıntısı: kayıtlı parametreler + özet. */
data class JobDetail(
    val job: Job,
    val axis: String,
    val scale: Double,
    val targetLen: Double,
    val simplifyTol: Double,
    val holeCount: Int,
    val holeDia: Double,
    val frameCount: Int,
    val frameT: Double,
    val frameH: Double,
    val frameFit: Double,
    val sheetW: Double,
    val sheetH: Double,
    val partGap: Double,
    val imgW: Double,
    val imgH: Double,
    val imgDepth: Double,
    val minDepth: Double,
    val orient: String,
    val shapeMode: String,
    val invert: Boolean,
    val smooth: Double,
    val normalize: Boolean,
    val summary: JobSummary?
)
