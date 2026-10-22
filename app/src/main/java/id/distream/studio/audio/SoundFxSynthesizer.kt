package id.distream.studio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import id.distream.studio.model.SoundFxPad
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generator & Sampler Audio 48kHz Stereo 16-bit PCM untuk SoundFX Deck DiStream Studio.
 *
 * Keunggulan arsitektur:
 * 1. Menghasilkan sampel PCM 48kHz Stereo presisi yang langsung di-mix ke dalam jalur RTMP
 *    encoder (`ProBroadcastDspEffect`) sehingga 100% terdengar jernih oleh penonton live!
 * 2. Sekaligus memutar audio monitor lokal (`AudioTrack`) ke telinga streamer tanpa latensi.
 */
object SoundFxSynthesizer {

    private const val SAMPLE_RATE = 48_000
    private var activeMonitorTrack: AudioTrack? = null

    fun buildStereoPcm16(pad: SoundFxPad, volumeGain: Float): ShortArray {
        val frames = (SAMPLE_RATE * (pad.durationMs / 1000.0)).toInt()
        val stereo = ShortArray(frames * 2)
        val rng = Random(42)

        for (i in 0 until frames) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / frames

            val rawSample: Double = when (pad) {
                SoundFxPad.AIRHORN -> {
                    val env = if (progress < 0.06) progress / 0.06 else (1.0 - progress * 0.25)
                    val s1 = sin(2.0 * PI * 587.33 * t)
                    val s2 = sin(2.0 * PI * 591.50 * t)
                    val s3 = sin(2.0 * PI * 880.00 * t) * 0.65
                    ((s1 + s2 + s3) / 2.65) * env
                }
                SoundFxPad.VICTORY -> {
                    val noteFreq = when {
                        progress < 0.22 -> 523.25
                        progress < 0.44 -> 659.25
                        progress < 0.66 -> 783.99
                        else -> 1046.50
                    }
                    val localDecay = exp(-3.2 * (progress % 0.22))
                    (sin(2.0 * PI * noteFreq * t) * 0.7 + sin(4.0 * PI * noteFreq * t) * 0.3) * localDecay
                }
                SoundFxPad.COIN_ALERT -> {
                    val freq = if (progress < 0.30) 987.77 else 1318.51
                    val decay = exp(-5.0 * (if (progress < 0.30) progress else progress - 0.30))
                    sin(2.0 * PI * freq * t) * decay
                }
                SoundFxPad.BOOM_DROP -> {
                    val sweepFreq = 115.0 * exp(-3.4 * progress) + 36.0
                    val decay = exp(-2.8 * progress)
                    sin(2.0 * PI * sweepFreq * t) * decay
                }
                SoundFxPad.RIMSHOT -> {
                    when {
                        progress < 0.20 -> sin(2.0 * PI * 165.0 * t) * exp(-10.0 * progress)
                        progress < 0.42 -> sin(2.0 * PI * 125.0 * t) * exp(-10.0 * (progress - 0.20))
                        else -> (rng.nextDouble(-1.0, 1.0)) * exp(-5.5 * (progress - 0.42)) * 0.75
                    }
                }
                SoundFxPad.APPLAUSE -> {
                    val burst = (sin(2.0 * PI * 17.0 * t) * 0.5 + 0.5)
                    val env = if (progress < 0.1) progress * 10.0 else (1.0 - progress * 0.7)
                    rng.nextDouble(-0.85, 0.85) * burst * env * 0.55
                }
                SoundFxPad.SUSPENSE -> {
                    val riseFreq = 190.0 + (620.0 * progress * progress)
                    sin(2.0 * PI * riseFreq * t) * (0.35 + progress * 0.65)
                }
                SoundFxPad.CENSOR_BEEP -> {
                    sin(2.0 * PI * 1000.0 * t) * 0.85
                }
            }

            val scaled = (rawSample * volumeGain.coerceIn(0f, 1.5f) * 24_000.0)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            stereo[i * 2] = scaled
            stereo[i * 2 + 1] = scaled
        }
        return stereo
    }

    fun playLocalMonitor(stereoSamples: ShortArray) {
        stopLocalMonitor()
        runCatching {
            val byteSize = stereoSamples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(stereoSamples, 0, stereoSamples.size)
            track.play()
            activeMonitorTrack = track
        }
    }

    fun stopLocalMonitor() {
        runCatching {
            activeMonitorTrack?.stop()
            activeMonitorTrack?.release()
        }
        activeMonitorTrack = null
    }
}
