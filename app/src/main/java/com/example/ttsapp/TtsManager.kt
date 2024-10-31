package com.example.ttsapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import ai.onnxruntime.*
import java.nio.LongBuffer
import kotlin.math.absoluteValue

class TtsManager(private val context: Context) {
    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val vocabSize = 38  // Angepasst wie im Python-Script
    private val sampleRate = 22050
    private var currentAudioTrack: AudioTrack? = null

    fun loadModelFromBytes(modelBytes: ByteArray) {
        try {
            Log.d("TtsManager", "Modellgröße: ${modelBytes.size / 1024}KB")
            session?.close()
            session = ortEnvironment.createSession(modelBytes)
            Log.d("TtsManager", "Modell geladen. Input Namen: ${session?.inputNames?.joinToString()}")
            Log.d("TtsManager", "Output Namen: ${session?.outputNames?.joinToString()}")
        } catch (e: Exception) {
            Log.e("TtsManager", "Fehler beim Laden des Modells: ${e.message}")
            throw e
        }
    }

    fun synthesize(text: String): FloatArray? {
        try {
            Log.d("TtsManager", "Starte Synthese für: '$text'")
            
            if (session == null) {
                Log.e("TtsManager", "Keine Session verfügbar - Modell nicht geladen?")
                return null
            }

            // Token-Generierung wie im Python-Script
            val tokens = text.map { char ->
                val code = (char.code % vocabSize).toLong()
                Log.d("TtsManager", "Token für '$char': $code")
                code
            }.toLongArray()
            
            Log.d("TtsManager", "Generierte Tokens: ${tokens.contentToString()}")

            val tokenBuffer = LongBuffer.wrap(tokens)
            val attentionMask = LongArray(tokens.size) { 1L }
            val maskBuffer = LongBuffer.wrap(attentionMask)
            val shape = longArrayOf(1, tokens.size.toLong())

            val inputs = mapOf(
                "input_ids" to OnnxTensor.createTensor(ortEnvironment, tokenBuffer, shape),
                "attention_mask" to OnnxTensor.createTensor(ortEnvironment, maskBuffer, shape)
            )

            Log.d("TtsManager", "Input Shapes - input_ids: ${shape.contentToString()}")
            
            // ONNX Inference
            val outputs = session?.run(inputs)
            Log.d("TtsManager", "Inference durchgeführt, Outputs: ${outputs?.size()}")
            
            if (outputs == null) {
                Log.e("TtsManager", "Inference fehlgeschlagen - null Output")
                return null
            }

            // Der erste Tensor enthält die Audiodaten (basierend auf Python-Script)
            val audioData = outputs[0].value as Array<*>
            Log.d("TtsManager", "Audio Array Typ: ${audioData.javaClass}, Größe: ${audioData.size}")
            
            // Konvertiere zu FloatArray
            val result = (audioData[0] as? FloatArray) ?: run {
                Log.e("TtsManager", "Konvertierung zu FloatArray fehlgeschlagen")
                return null
            }

            Log.d("TtsManager", "Audio generiert: ${result.size} Samples")
            return result

        } catch (e: Exception) {
            Log.e("TtsManager", "Fehler bei Synthese: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Rest der Klasse bleibt gleich
    fun playAudio(audioData: FloatArray) {
        try {
            Log.d("TtsManager", "Start Audio Playback: ${audioData.size} samples")
            
            val maxValue = audioData.maxOrNull()?.absoluteValue ?: 1f
            val normalizedData = if (maxValue > 1f) {
                audioData.map { it / maxValue }.toFloatArray()
            } else {
                audioData
            }

            currentAudioTrack?.stop()
            currentAudioTrack?.release()

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            val bufferSize = maxOf(minBufferSize, normalizedData.size * 4)
            
            currentAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            currentAudioTrack?.write(normalizedData, 0, normalizedData.size, AudioTrack.WRITE_BLOCKING)
            currentAudioTrack?.play()

            Log.d("TtsManager", "Audio Playback gestartet")

        } catch (e: Exception) {
            Log.e("TtsManager", "Fehler bei Audio Playback: ${e.message}", e)
        }
    }

    fun close() {
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
            session?.close()
            ortEnvironment.close()
        } catch (e: Exception) {
            Log.e("TtsManager", "Fehler beim Schließen: ${e.message}")
        }
    }
}