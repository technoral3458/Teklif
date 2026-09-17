package com.technoral.servis.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.components.AppTextField
import com.technoral.servis.ui.components.SectionCard

/** İlk açılışta bir kez gösterilir: firma künyesi ve teknisyen bilgisi. */
@Composable
fun SetupScreen(vm: AppViewModel, onDone: () -> Unit) {
    val settings by vm.settings.collectAsState()

    var company by remember { mutableStateOf(settings.company.name) }
    var phone by remember { mutableStateOf(settings.company.phone) }
    var email by remember { mutableStateOf(settings.company.email) }
    var address by remember { mutableStateOf(settings.company.address) }
    var technician by remember { mutableStateOf(settings.technicianName) }
    var technicianPhone by remember { mutableStateOf(settings.technicianPhone) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(54.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Build,
                        null,
                        Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("TeknoServis'e hoş geldiniz", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Raporlarda görünecek bilgileri girerek başlayalım.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Firma bilgileri", subtitle = "Rapor başlığında ve mail imzasında görünür") {
            AppTextField(company, { company = it }, "Firma adı")
            AppTextField(phone, { phone = it }, "Telefon", keyboardType = KeyboardType.Phone)
            AppTextField(email, { email = it }, "E-posta", keyboardType = KeyboardType.Email)
            AppTextField(address, { address = it }, "Adres", singleLine = false, minLines = 2)
        }

        SectionCard(title = "Teknisyen", subtitle = "Yeni raporlarda otomatik doldurulur") {
            AppTextField(technician, { technician = it }, "Ad soyad")
            AppTextField(technicianPhone, { technicianPhone = it }, "Telefon", keyboardType = KeyboardType.Phone)
        }

        Text(
            "Mail gönderimi için SMTP ayarlarını sonradan Ayarlar > Mail Ayarları bölümünden girebilirsiniz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                vm.saveSettings(
                    settings.copy(
                        company = settings.company.copy(
                            name = company.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                        ),
                        technicianName = technician.trim(),
                        technicianPhone = technicianPhone.trim(),
                        setupDone = true,
                    )
                )
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = company.isNotBlank(),
        ) {
            Text("Başla")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }

        TextButton(
            onClick = {
                vm.saveSettings(settings.copy(setupDone = true))
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Şimdilik atla")
        }
        Spacer(Modifier.height(24.dp))
    }
}
