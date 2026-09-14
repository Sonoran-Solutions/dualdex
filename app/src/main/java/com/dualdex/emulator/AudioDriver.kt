package com.dualdex.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class AudioDriver(private val defaultSampleRate: Int = 48000) {

    private var audioTrack: AudioTrack? = null
    @Volatile private var isRunning = false
    private var audioThread: Thread? = null

    fun start() {
        if (isRunning) return

        try {
            // Android native mixer runs at 48000 Hz on modern Snapdragon chipsets
            val effectiveRate = if (AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) > 0) {
                48000
            } else if (AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) > 0) {
                44100
            } else {
                defaultSampleRate
            }

            // Instruct native host to resample mGBA's 65536 Hz stream to this exact rate in C
            LibretroCoreCoordinator.defaultInstance.setTargetAudioSampleRate(effectiveRate)

            val minBufferSize = AudioTrack.getMinBufferSize(
                effectiveRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize.coerceAtLeast(4096)

            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(effectiveRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            audioTrack = builder.build()
            audioTrack?.play()
            isRunning = true

            audioThread = Thread({
                val sampleBuf = ShortArray(1024)

                while (isRunning) {
                    val count = LibretroCoreCoordinator.defaultInstance.getAudioSamples(sampleBuf)
                    if (count > 0) {
                        try {
                            audioTrack?.write(sampleBuf, 0, count)
                        } catch (e: Exception) {
                            // ignore write error during track shutdown
                        }
                    } else {
                        try {
                            Thread.sleep(1)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
            }, "DualDexAudioThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.i("DualDexAudio", "AudioDriver started (native resampled to ${effectiveRate}Hz, low-latency mode)")
        } catch (e: Exception) {
            Log.e("DualDexAudio", "Failed to start AudioDriver: ${e.message}")
        }
    }

    fun updateSampleRate() {
        stop()
        start()
    }

    fun stop() {
        isRunning = false
        audioThread?.interrupt()
        try {
            audioThread?.join(300)
        } catch (e: InterruptedException) {
            // ignore
        }
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
        Log.i("DualDexAudio", "AudioDriver stopped")
    }
}
