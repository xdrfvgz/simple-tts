package com.example.ttsapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ttsapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var ttsManager: TtsManager

    // File Chooser für das Auswählen des Modells
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadModel(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsManager = TtsManager(this)
        loadModelFromAssets()

        // Button zum Laden des Modells
        binding.loadModelButton.setOnClickListener { openModelChooser() }

        // Button zur Synthese des Textes
        binding.synthesizeButton.setOnClickListener { synthesizeText() }
    }

    // Öffnet den File Chooser zum Auswählen der Modelldatei
    private fun openModelChooser() {
        getContent.launch("application/octet-stream") // Filter für binäre Dateien wie ONNX-Modelle
    }

    // Lädt das Modell aus dem ausgewählten URI
    private fun loadModel(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.loadModelButton.isEnabled = false
                binding.modelStatus.text = "Lade Modell..."

                withContext(Dispatchers.IO) {
                    // Öffnen und Lesen der Datei aus dem URI über den ContentResolver
                    val modelBytes = contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw Exception("Konnte Modelldatei nicht lesen")

                    Log.d("MainActivity", "Model URI: $uri")
                    Log.d("MainActivity", "Model Size: ${modelBytes.size} bytes")

                    // Laden des Modells in die ONNX Runtime über TtsManager
                    ttsManager.loadModelFromBytes(modelBytes)
                }

                binding.modelStatus.text = "Modell geladen"
                binding.synthesizeButton.isEnabled = true
                Toast.makeText(this@MainActivity, "Modell erfolgreich geladen", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Fehler beim Laden des Modells", e)
                binding.modelStatus.text = "Fehler beim Laden des Modells"
                Toast.makeText(this@MainActivity, "Fehler beim Laden: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.loadModelButton.isEnabled = true
            }
        }
    }

    // Führt die Sprachsynthese durch und spielt das Audio ab
    private fun synthesizeText() {
        val text = binding.inputText.text.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "Bitte Text eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.synthesizeButton.isEnabled = false

                val audioData = withContext(Dispatchers.Default) {
                    ttsManager.synthesize(text)
                }

                if (audioData != null && audioData.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        ttsManager.playAudio(audioData)
                        Toast.makeText(this@MainActivity, "Text wird gesprochen", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("Keine Audiodaten generiert")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Fehler bei der Synthese", e)
                Toast.makeText(this@MainActivity, "Fehler bei Synthese: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.synthesizeButton.isEnabled = true
            }
        }
    }

    private fun loadModelFromAssets() {
        try {
            val modelBytes = assets.open("model.onnx").use { it.readBytes() }
            ttsManager.loadModelFromBytes(modelBytes)
            binding.modelStatus.text = "Modell geladen"
            binding.synthesizeButton.isEnabled = true
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Laden des Models aus Assets", e)
            binding.modelStatus.text = "Fehler beim Laden des Models"
            // Fallback auf File Chooser
            Toast.makeText(this, "Kein Model in Assets gefunden, bitte manuell laden", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        ttsManager.close()
        super.onDestroy()
    }
}
