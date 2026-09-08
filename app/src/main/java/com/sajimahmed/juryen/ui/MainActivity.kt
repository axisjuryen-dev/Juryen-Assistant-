package com.sajimahmed.juryen.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sajimahmed.juryen.databinding.ActivityMainBinding
import com.sajimahmed.juryen.service.JuryenListenerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startListenerService()
        } else {
            val recordAudioDenied = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED

            if (recordAudioDenied) {
                Toast.makeText(
                    this,
                    "Microphone permission is blocked. Opening app settings — please enable it manually.",
                    Toast.LENGTH_LONG
                ).show()
                openAppSettings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedKey = getSharedPreferences("juryen_settings", MODE_PRIVATE).getString("anthropic_api_key", "")
        binding.etApiKey.setText(savedKey)
        binding.btnGrantPermissions.setOnClickListener { saveApiKey(); requestNeededPermissions() }
        binding.btnOpenAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnStart.setOnClickListener {
            saveApiKey()
            if (hasRecordAudioPermission()) {
                startListenerService()
            } else {
                requestNeededPermissions()
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun saveApiKey() {
        getSharedPreferences("juryen_settings", MODE_PRIVATE).edit()
            .putString("anthropic_api_key", binding.etApiKey.text.toString().trim())
            .apply()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun startListenerService() {
        val intent = Intent(this, JuryenListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.tvStatus.text = "Juryen is active. Say \"Juryen\" followed by a command."
    }
}
