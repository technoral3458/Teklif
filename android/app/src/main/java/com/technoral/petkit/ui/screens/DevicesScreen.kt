package com.technoral.petkit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technoral.petkit.data.Cihaz
import com.technoral.petkit.ui.BosDurum
import com.technoral.petkit.ui.NotSeridi

@Composable
fun CihazlarEkrani(
    cihazlar: List<Cihaz>,
    kullaniciAdi: String?,
    cihazSecildi: (Cihaz) -> Unit
) {
    if (cihazlar.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            BosDurum(
                "Hesabınıza bağlı cihaz bulunamadı.",
                Icons.Filled.Devices
            )
            NotSeridi(
                "Sunucuya bağlanıldı, hesap okundu - hesapta kayıtlı cihaz yok.\n\n" +
                        "Besleyicinin önce resmi Petkit uygulamasıyla Wi-Fi'ye alınıp bu " +
                        "hesaba bağlanması (eşleştirilmesi) gerekir; hesap açmak tek başına " +
                        "yetmez. Bu uygulama cihaz eşleştirme yapamaz, yalnızca hesaba " +
                        "bağlı cihazları yönetir.\n\n" +
                        "Resmi uygulamada eşleştirme sırasında \"Cihazın mevcut bölgede " +
                        "kullanımı desteklenmemektedir\" hatası alıyorsanız cihaz bölge " +
                        "kilitlidir: başka bir pazar için üretilmiş. O durumda hesabı " +
                        "cihazın bölgesinde açmanız ya da satıcıyla görüşmeniz gerekir - " +
                        "yazılımla çözülebilecek bir şey değil.\n\n" +
                        "Cihazı resmi uygulamada görüyorsanız: yenile düğmesine dokunun, " +
                        "olmazsa çıkış yapıp doğru bölge sunucusuyla girin, yine olmazsa " +
                        "konsol simgesinden \"Cihaz listesi\" ve \"Aile listesi\" " +
                        "şablonlarını çalıştırıp ham yanıtı iletin."
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (kullaniciAdi != null) {
                Text(
                    "Merhaba, $kullaniciAdi",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)
                )
            }
            Text(
                "${cihazlar.size} cihaz bulundu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp)
            )
        }
        items(cihazlar) { cihaz ->
            CihazKarti(cihaz) { cihazSecildi(cihaz) }
        }
        item {
            Spacer(Modifier.height(6.dp))
            NotSeridi(
                "Besleyici cihazlar (YumShare / Fresh Element) tam olarak desteklenir. " +
                        "Diğer cihaz türlerinde durum bilgisi ve API konsolu çalışır, " +
                        "özel denetimler sınırlı olabilir."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CihazKarti(cihaz: Cihaz, tiklandi: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { tiklandi() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Pets, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cihaz.ad, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    cihaz.tipEtiketi,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Kimlik: ${cihaz.id}" + (cihaz.seriNo?.let { " · SN: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "aç")
        }
    }
}
