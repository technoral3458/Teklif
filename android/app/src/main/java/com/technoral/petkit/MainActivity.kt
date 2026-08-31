package com.technoral.petkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.technoral.petkit.ui.AppViewModel
import com.technoral.petkit.ui.Ekran
import com.technoral.petkit.ui.screens.AyarlarSekmesi
import com.technoral.petkit.ui.screens.BeslemeSekmesi
import com.technoral.petkit.ui.screens.CihazSekmesi
import com.technoral.petkit.ui.screens.CihazlarEkrani
import com.technoral.petkit.ui.screens.DurumSekmesi
import com.technoral.petkit.ui.screens.GirisEkrani
import com.technoral.petkit.ui.screens.KayitlarSekmesi
import com.technoral.petkit.ui.screens.KonsolEkrani
import com.technoral.petkit.ui.screens.PlanSekmesi
import com.technoral.petkit.ui.theme.PetkitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PetkitTheme {
                Uygulama()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Uygulama(vm: AppViewModel = viewModel()) {
    val ui by vm.durum.collectAsState()
    var sekme by remember { mutableStateOf(CihazSekmesi.DURUM) }
    val bildirim = remember { SnackbarHostState() }

    LaunchedEffect(ui.hata, ui.bilgi) {
        val mesaj = ui.hata ?: ui.bilgi
        if (mesaj != null) {
            bildirim.showSnackbar(mesaj)
            vm.mesajlariKapat()
        }
    }

    BackHandler(enabled = ui.ekran != Ekran.GIRIS && ui.ekran != Ekran.CIHAZLAR) {
        when (ui.ekran) {
            Ekran.KONSOL -> vm.ekranaGit(if (ui.seciliCihaz != null) Ekran.CIHAZ else Ekran.CIHAZLAR)
            Ekran.CIHAZ -> vm.cihazdanCik()
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(bildirim) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Text(
                        when (ui.ekran) {
                            Ekran.GIRIS -> "Petkit Türkçe"
                            Ekran.CIHAZLAR -> "Cihazlarım"
                            Ekran.CIHAZ -> ui.seciliCihaz?.ad ?: "Cihaz"
                            Ekran.KONSOL -> "API Konsolu"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (ui.ekran == Ekran.CIHAZ || ui.ekran == Ekran.KONSOL) {
                        IconButton(onClick = {
                            if (ui.ekran == Ekran.KONSOL) {
                                vm.ekranaGit(if (ui.seciliCihaz != null) Ekran.CIHAZ else Ekran.CIHAZLAR)
                            } else {
                                vm.cihazdanCik()
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "geri") }
                    }
                },
                actions = {
                    if (ui.ekran == Ekran.CIHAZLAR) {
                        IconButton(onClick = { vm.cihazlariYukle() }) {
                            Icon(Icons.Filled.Refresh, "yenile")
                        }
                        IconButton(onClick = { vm.cikisYap() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "çıkış")
                        }
                    }
                    if (ui.ekran == Ekran.CIHAZ) {
                        IconButton(onClick = {
                            vm.cihazYenile()
                            vm.kayitlariYukle()
                        }) { Icon(Icons.Filled.Refresh, "yenile") }
                        IconButton(onClick = { vm.ekranaGit(Ekran.KONSOL) }) {
                            Icon(Icons.Filled.Terminal, "API konsolu")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (ui.ekran == Ekran.CIHAZ) {
                NavigationBar {
                    CihazSekmesi.entries.forEach { s ->
                        NavigationBarItem(
                            selected = sekme == s,
                            onClick = {
                                sekme = s
                                if (s == CihazSekmesi.KAYITLAR) vm.kayitlariYukle(sessiz = true)
                            },
                            icon = {
                                Icon(
                                    when (s) {
                                        CihazSekmesi.DURUM -> Icons.Filled.Info
                                        CihazSekmesi.BESLEME -> Icons.Filled.Restaurant
                                        CihazSekmesi.PLAN -> Icons.Filled.Schedule
                                        CihazSekmesi.KAYITLAR -> Icons.Filled.History
                                        CihazSekmesi.AYARLAR -> Icons.Filled.Settings
                                    },
                                    s.etiket
                                )
                            },
                            label = { Text(s.etiket, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { bosluk ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(bosluk)
        ) {
            if (ui.yukleniyor) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxSize()) {
                when (ui.ekran) {
                    Ekran.GIRIS -> GirisEkrani(ui.yukleniyor) { k, s, b -> vm.girisYap(k, s, b) }

                    Ekran.CIHAZLAR -> CihazlarEkrani(
                        cihazlar = ui.cihazlar,
                        kullaniciAdi = ui.kullaniciAdi
                    ) { vm.cihazSec(it) }

                    Ekran.KONSOL -> KonsolEkrani(
                        cihazYolu = vm.seciliCihazYolu(),
                        cihazId = vm.seciliCihazId(),
                        bugun = vm.bugunKodu(),
                        sonuc = ui.konsolSonuc,
                        yukleniyor = ui.yukleniyor
                    ) { yol, par -> vm.hamCagri(yol, par) }

                    Ekran.CIHAZ -> {
                        val cihaz = ui.seciliCihaz
                        if (cihaz == null) {
                            Text("Cihaz seçilmedi.", Modifier.padding(24.dp))
                        } else {
                            when (sekme) {
                                CihazSekmesi.DURUM -> DurumSekmesi(
                                    cihaz = cihaz,
                                    ui = ui,
                                    hamJson = { vm.hamDetayJson() },
                                    nemAliciSifirla = { vm.nemAliciSifirla() }
                                )

                                CihazSekmesi.BESLEME -> BeslemeSekmesi(
                                    cihaz = cihaz,
                                    ui = ui,
                                    besle = { a, b -> vm.besle(a, b) },
                                    iptal = { vm.beslemeIptal() }
                                )

                                CihazSekmesi.PLAN -> PlanSekmesi(
                                    cihaz = cihaz,
                                    ui = ui,
                                    planKaydet = { sa, dk, a, b, gunler, ad ->
                                        vm.planKaydet(sa, dk, a, b, gunler, ad)
                                    },
                                    planSil = { vm.planSil(it) }
                                )

                                CihazSekmesi.KAYITLAR -> KayitlarSekmesi(
                                    ui = ui,
                                    gunEtiketi = vm.gunEtiketi(),
                                    gunDegistir = { vm.kayitGunuDegistir(it) }
                                )

                                CihazSekmesi.AYARLAR -> AyarlarSekmesi(
                                    cihaz = cihaz,
                                    ui = ui,
                                    ayarDegistir = { k, d, e -> vm.ayarDegistir(k, d, e) },
                                    hamAyarGonder = { vm.ayarHamGonder(it) },
                                    porsiyonGramAyarla = { vm.porsiyonGramAyarla(it) },
                                    adDegistir = { vm.adDegistir(it) },
                                    komutGonder = { t, kv, e -> vm.komutGonder(t, kv, e) },
                                    cikisYap = { vm.cikisYap() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
