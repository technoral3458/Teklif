package com.technoral.servis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.ServiceReport
import com.technoral.servis.data.ServiceStatus
import com.technoral.servis.ui.theme.StatusColors
import com.technoral.servis.util.asDate

fun statusPalette(status: ServiceStatus): Pair<Color, Color> = when (status) {
    ServiceStatus.COZULDU -> StatusColors.success to StatusColors.successBg
    ServiceStatus.PARCA_BEKLIYOR -> StatusColors.warning to StatusColors.warningBg
    ServiceStatus.GECICI_COZUM -> StatusColors.warning to StatusColors.warningBg
    ServiceStatus.TEKRAR_ZIYARET -> StatusColors.danger to StatusColors.dangerBg
    ServiceStatus.ACIK -> StatusColors.info to StatusColors.infoBg
    ServiceStatus.TASLAK -> StatusColors.neutral to StatusColors.neutralBg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListItem(
    report: ServiceReport,
    customerName: String,
    machineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (color, background) = statusPalette(report.status)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        report.reportNo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        report.serviceDate.asDate(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (report.mailedAt != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Mail,
                            "Mail gönderildi",
                            Modifier.size(13.dp),
                            tint = StatusColors.success,
                        )
                    }
                    if (report.photos.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${report.photos.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    customerName.ifBlank { "Müşteri seçilmedi" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(machineName, report.type.label).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (report.faultDescription.isNotBlank()) {
                    Text(
                        report.faultDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            StatusBadge(report.status.label, color, background)
        }
    }
}
