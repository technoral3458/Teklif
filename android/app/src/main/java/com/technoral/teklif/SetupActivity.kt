package com.technoral.teklif

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.technoral.teklif.databinding.ActivitySetupBinding

/**
 * Uygulamanin hangi siteyi acacagini belirlemek icin kullanilan ekran.
 * Ilk kurulumda otomatik acilir, sonrasinda uygulama simgesine basili tutarak
 * cikan "Adres ayarlari" kisayolundan tekrar acilabilir.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.urlInput.setText(Prefs.siteUrl(this))
        binding.saveButton.setOnClickListener { save() }
    }

    private fun save() {
        val normalized = Prefs.normalizeUrl(binding.urlInput.text?.toString().orEmpty())
        if (normalized == null) {
            binding.urlInput.error = getString(R.string.setup_invalid_url)
            return
        }

        Prefs.setSiteUrl(this, normalized)
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
