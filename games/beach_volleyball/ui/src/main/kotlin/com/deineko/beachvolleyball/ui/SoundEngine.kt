package com.deineko.beachvolleyball.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Tiny synthesized sound effects -- no bundled audio assets, everything here is a generated PCM
 *  waveform. A short "boing" on paddle hits, a bright three-note chime on scoring, and a longer
 *  fanfare on winning, so the match feels alive without needing licensed sound files. */
class SoundEngine {
    private val sampleRate = 22_050

    private val hitBuffer = tone(startHz = 520f, endHz = 880f, durationMs = 90, decay = 3.0)
    private val scoreBuffer = arpeggio(listOf(660f, 880f, 1046f), noteDurationMs = 110)
    private val winBuffer = arpeggio(listOf(523f, 659f, 784f, 1046f, 1318f), noteDurationMs = 130)

    fun playHit() = play(hitBuffer)
    fun playScore() = play(scoreBuffer)
    fun playWin() = play(winBuffer)

    private fun play(buffer: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * 2)
            .build()
        track.write(buffer, 0, buffer.size)
        track.setNotificationMarkerPosition(buffer.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) = t.release()
            override fun onPeriodicNotification(t: AudioTrack) = Unit
        })
        track.play()
    }

    private fun tone(startHz: Float, endHz: Float, durationMs: Int, decay: Double): ShortArray {
        val samples = sampleRate * durationMs / 1000
        return ShortArray(samples) { i ->
            val t = i / sampleRate.toFloat()
            val progress = i / samples.toFloat()
            val freq = startHz + (endHz - startHz) * progress
            val envelope = exp(-decay * progress).toFloat()
            (sin(2.0 * PI * freq * t) * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
    }

    private fun arpeggio(frequenciesHz: List<Float>, noteDurationMs: Int): ShortArray {
        val perNote = sampleRate * noteDurationMs / 1000
        val total = ShortArray(perNote * frequenciesHz.size)
        frequenciesHz.forEachIndexed { noteIndex, freq ->
            for (i in 0 until perNote) {
                val t = i / sampleRate.toFloat()
                val progress = i / perNote.toFloat()
                val envelope = exp(-4.0 * progress).toFloat()
                total[noteIndex * perNote + i] =
                    (sin(2.0 * PI * freq * t) * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
            }
        }
        return total
    }
}
