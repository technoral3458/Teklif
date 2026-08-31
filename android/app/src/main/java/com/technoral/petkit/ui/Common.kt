package com.technoral.petkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions

/** Başlıklı içerik kartı. */
@Composable
fun BolumKarti(
    baslik: String,
    ikon: ImageVector? = null,
    altBaslik: String? = null,
    icerik: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ikon != null) {
                    Icon(ikon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(baslik, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (altBaslik != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    altBaslik,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            icerik()
        }
    }
}

/** Etiket - değer satırı. */
@Composable
fun BilgiSatiri(etiket: String, deger: String?, ikon: ImageVector? = null, vurgu: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ikon != null) {
            Icon(
                ikon, null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            etiket,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            deger ?: "bilinmiyor",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (vurgu) FontWeight.Bold else FontWeight.Medium,
            color = if (vurgu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Aç/kapa ayar satırı. */
@Composable
fun AyarAnahtari(
    baslik: String,
    aciklama: String?,
    acik: Boolean?,
    etkin: Boolean = true,
    degisti: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(baslik, style = MaterialTheme.typography.bodyLarge)
            if (aciklama != null) {
                Text(
                    aciklama,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (acik == null) {
                Text(
                    "Bu cihazda okunamadı - dokunarak yine de gönderebilirsiniz.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = acik == true, onCheckedChange = degisti, enabled = etkin)
    }
}

/** Artı/eksi düğmeli sayı seçici. */
@Composable
fun SayiSecici(
    etiket: String,
    deger: Int,
    enAz: Int = 0,
    enCok: Int = 50,
    altYazi: String? = null,
    degisti: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(etiket, style = MaterialTheme.typography.bodyLarge)
        if (altYazi != null) {
            Text(
                altYazi,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { degisti((deger - 1).coerceAtLeast(enAz)) },
                enabled = deger > enAz
            ) { Icon(Icons.Filled.Remove, "azalt") }
            Text(
                deger.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            FilledTonalIconButton(
                onClick = { degisti((deger + 1).coerceAtMost(enCok)) },
                enabled = deger < enCok
            ) { Icon(Icons.Filled.Add, "arttır") }
        }
    }
}

/** Sayı giriş alanı. */
@Composable
fun SayiAlani(etiket: String, deger: String, degisti: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = deger,
        onValueChange = { yeni -> degisti(yeni.filter { it.isDigit() }.take(4)) },
        label = { Text(etiket) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Katlanabilir ham JSON gösterici. */
@Composable
fun HamJsonKarti(baslik: String, json: String, baslangictaAcik: Boolean = false) {
    var acik by remember { mutableStateOf(baslangictaAcik) }
    val pano = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(baslik, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { pano.setText(AnnotatedString(json)) }) {
                    Icon(Icons.Filled.ContentCopy, "kopyala", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { acik = !acik }) {
                    Icon(
                        if (acik) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        if (acik) "kapat" else "aç"
                    )
                }
            }
            if (acik) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        json,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/** Kısa açıklama/uyarı şeridi. */
@Composable
fun NotSeridi(metin: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Info, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.width(8.dp))
        Text(
            metin,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            overflow = TextOverflow.Visible
        )
    }
}

/** Boş liste durumu. */
@Composable
fun BosDurum(metin: String, ikon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            ikon, null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            metin,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
