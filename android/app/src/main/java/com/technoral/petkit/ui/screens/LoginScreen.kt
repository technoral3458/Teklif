package com.technoral.petkit.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.technoral.petkit.data.PetkitBolge
import com.technoral.petkit.ui.NotSeridi

@Composable
fun GirisEkrani(
    yukleniyor: Boolean,
    girisIstegi: (String, String, PetkitBolge) -> Unit
) {
    var eposta by remember { mutableStateOf("") }
    var sifre by remember { mutableStateOf("") }
    var sifreGoster by remember { mutableStateOf(false) }
    var bolge by remember { mutableStateOf(PetkitBolge.VARSAYILAN) }
    var menuAcik by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Icon(
            Icons.Filled.Pets, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Petkit Türkçe",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "YumShare Dual-Hopper 2 ve diğer Petkit cihazları için Türkçe denetim",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = eposta,
            onValueChange = { eposta = it },
            label = { Text("Petkit e-posta adresi veya telefon") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = sifre,
            onValueChange = { sifre = it },
            label = { Text("Şifre") },
            singleLine = true,
            visualTransformation = if (sifreGoster) androidx.compose.ui.text.input.VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { sifreGoster = !sifreGoster }) {
                    Icon(
                        if (sifreGoster) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (sifreGoster) "şifreyi gizle" else "şifreyi göster"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        // Bölge (sunucu) seçimi
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Hesabınızın bulunduğu bölge",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { menuAcik = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Public, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(bolge.ad, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            DropdownMenu(expanded = menuAcik, onDismissRequest = { menuAcik = false }) {
                PetkitBolge.HEPSI.forEach { b ->
                    DropdownMenuItem(
                        text = { Text(b.ad) },
                        onClick = {
                            bolge = b
                            menuAcik = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { girisIstegi(eposta, sifre, bolge) },
            enabled = !yukleniyor,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (yukleniyor) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Giriş yapılıyor...")
            } else {
                Icon(Icons.Filled.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("Giriş yap")
            }
        }

        Spacer(Modifier.height(20.dp))
        NotSeridi(
            "Petkit hesabınızın kendi e-posta ve şifresiyle giriş yapın - bu uygulama " +
                    "doğrudan Petkit bulut sunucusuna bağlanır, araya başka bir sunucu girmez. " +
                    "Giriş bilgileri yalnızca telefonunuzda saklanır.\n\n" +
                    "Türkiye'deki hesaplar için \"Türkiye / Avrupa / ABD\" seçeneği doğrudur. " +
                    "\"Hesap bulunamadı\" hatası alırsanız diğer bölgeleri deneyin."
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            Text(
                "Sürüm 1.0.0 · Resmi olmayan istemci",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
