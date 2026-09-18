package com.teknoral.parametrik.data.remote.dto

import com.teknoral.parametrik.domain.model.Geometry
import com.teknoral.parametrik.domain.model.Job
import com.teknoral.parametrik.domain.model.JobDetail
import com.teknoral.parametrik.domain.model.JobSummary
import com.teknoral.parametrik.domain.model.Part
import com.teknoral.parametrik.domain.model.SourceKind

fun JobDto.toDomain(): Job = Job(
    id = id,
    name = name.ifBlank { origName.ifBlank { "İş #$id" } },
    sourceKind = SourceKind.from(srcKind),
    origName = origName,
    triCount = triCount,
    status = status.ifBlank { if (panelCount > 0) "Hazır" else "Yeni" },
    panelCount = panelCount,
    sheetCount = sheetCount,
    thickness = thickness,
    gap = gap,
    createdAt = createdAt
)

fun SummaryDto.toDomain(): JobSummary = JobSummary(
    panelCount = panelCount,
    thickness = thickness,
    gap = gap,
    pitch = if (pitch > 0.0) pitch else thickness + gap,
    stackLen = stackLen,
    profileW = profileW,
    profileH = profileH,
    cutLenM = cutLenM,
    sheetCount = sheetCount,
    holeCount = holeCount,
    holeDia = holeDia,
    frameCount = frameCount,
    frameT = frameT,
    frameH = frameH,
    frameLen = frameLen,
    warnings = warnings
)

fun JobDetailDto.toDomain(): JobDetail = JobDetail(
    job = Job(
        id = id,
        name = name.ifBlank { origName.ifBlank { "İş #$id" } },
        sourceKind = SourceKind.from(srcKind),
        origName = origName,
        triCount = triCount,
        status = status.ifBlank { if (panelCount > 0) "Hazır" else "Yeni" },
        panelCount = panelCount,
        sheetCount = sheetCount,
        thickness = thickness,
        gap = gap,
        createdAt = createdAt
    ),
    axis = axis,
    scale = scale,
    targetLen = targetLen,
    simplifyTol = simplifyTol,
    holeCount = holeCount,
    holeDia = holeDia,
    frameCount = frameCount,
    frameT = frameT,
    frameH = frameH,
    frameFit = frameFit,
    sheetW = sheetW,
    sheetH = sheetH,
    partGap = partGap,
    imgW = imgW,
    imgH = imgH,
    imgDepth = imgDepth,
    minDepth = minDepth,
    orient = orient,
    shapeMode = shapeMode,
    invert = invert != 0,
    smooth = smooth,
    normalize = normalize != 0,
    summary = summary?.toDomain()
)

fun Geometry3dDto.toDomain(): Geometry {
    val mapped = parts.mapNotNull { part ->
        val loops = part.l.mapNotNull { loop ->
            if (loop.size < 3) return@mapNotNull null
            val flat = FloatArray(loop.size * 2)
            var i = 0
            for (pt in loop) {
                if (pt.size < 2) return@mapNotNull null
                flat[i++] = pt[0]
                flat[i++] = pt[1]
            }
            flat
        }
        if (loops.isEmpty()) null
        else Part(orientation = part.o, position = part.p, thickness = part.t, loops = loops)
    }
    val box = bb?.takeIf { it.size >= 6 }?.let { floatArrayOf(it[0], it[1], it[2], it[3], it[4], it[5]) }
    return Geometry(parts = mapped, bb = box, panelCount = np, frameCount = nf)
}
