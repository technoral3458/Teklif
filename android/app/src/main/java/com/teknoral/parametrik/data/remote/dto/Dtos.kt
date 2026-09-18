package com.teknoral.parametrik.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JobDto(
    val id: Long,
    val name: String = "",
    @SerialName("src_kind") val srcKind: String = "mesh",
    @SerialName("orig_name") val origName: String = "",
    @SerialName("tri_count") val triCount: Int = 0,
    val status: String = "",
    @SerialName("panel_count") val panelCount: Int = 0,
    @SerialName("sheet_count") val sheetCount: Int = 0,
    val thickness: Double = 0.0,
    val gap: Double = 0.0,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class SummaryDto(
    @SerialName("panel_count") val panelCount: Int = 0,
    val thickness: Double = 0.0,
    val gap: Double = 0.0,
    val pitch: Double = 0.0,
    @SerialName("stack_len") val stackLen: Double = 0.0,
    @SerialName("profile_w") val profileW: Double = 0.0,
    @SerialName("profile_h") val profileH: Double = 0.0,
    @SerialName("cut_len_m") val cutLenM: Double = 0.0,
    @SerialName("sheet_count") val sheetCount: Int = 0,
    @SerialName("hole_count") val holeCount: Int = 0,
    @SerialName("hole_dia") val holeDia: Double = 0.0,
    @SerialName("frame_count") val frameCount: Int = 0,
    @SerialName("frame_t") val frameT: Double = 0.0,
    @SerialName("frame_h") val frameH: Double = 0.0,
    @SerialName("frame_len") val frameLen: Double = 0.0,
    val warnings: List<String> = emptyList()
)

@Serializable
data class JobDetailDto(
    val id: Long,
    val name: String = "",
    @SerialName("src_kind") val srcKind: String = "mesh",
    @SerialName("orig_name") val origName: String = "",
    @SerialName("tri_count") val triCount: Int = 0,
    val status: String = "",
    @SerialName("panel_count") val panelCount: Int = 0,
    @SerialName("sheet_count") val sheetCount: Int = 0,
    val thickness: Double = 18.0,
    val gap: Double = 6.0,
    @SerialName("created_at") val createdAt: String = "",

    val axis: String = "z",
    val scale: Double = 1.0,
    @SerialName("target_len") val targetLen: Double = 0.0,
    @SerialName("simplify_tol") val simplifyTol: Double = 0.15,

    @SerialName("hole_count") val holeCount: Int = 0,
    @SerialName("hole_dia") val holeDia: Double = 10.0,
    @SerialName("frame_count") val frameCount: Int = 0,
    @SerialName("frame_t") val frameT: Double = 18.0,
    @SerialName("frame_h") val frameH: Double = 120.0,
    @SerialName("frame_fit") val frameFit: Double = 0.2,
    @SerialName("sheet_w") val sheetW: Double = 2100.0,
    @SerialName("sheet_h") val sheetH: Double = 2800.0,
    @SerialName("part_gap") val partGap: Double = 15.0,

    @SerialName("img_w") val imgW: Double = 1200.0,
    @SerialName("img_h") val imgH: Double = 800.0,
    @SerialName("img_depth") val imgDepth: Double = 180.0,
    @SerialName("min_depth") val minDepth: Double = 40.0,
    val orient: String = "v",
    @SerialName("shape_mode") val shapeMode: String = "single",
    val invert: Int = 0,
    val smooth: Double = 1.0,
    val normalize: Int = 1,

    val summary: SummaryDto? = null
)

@Serializable
data class PartDto(
    /** 0 = panel, 1 = kayıt (çerçeve) */
    val o: Int = 0,
    /** ekstrüzyon başlangıcı */
    val p: Float = 0f,
    /** kalınlık */
    val t: Float = 0f,
    /** kapalı konturlar: [[[u, v], ...], ...] */
    val l: List<List<List<Float>>> = emptyList()
)

@Serializable
data class Geometry3dDto(
    val parts: List<PartDto> = emptyList(),
    val bb: List<Float>? = null,
    val np: Int = 0,
    val nf: Int = 0
)
