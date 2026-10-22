package id.distream.studio.audio

import com.pedro.encoder.input.audio.CustomAudioEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3-Bus Broadcast Audio DSP Engine DiStream Studio:
 * - Bus 1: Input Mikrofon + Noise Gate (-42 dBFS) + Tombol Sensor 1kHz Real-time
 * - Bus 2: Sidechain Voice Ducking (otomatis mengecilkan volume game saat bicara)
 * - Bus 3: Injeksi sampel PCM SoundFX Deck langsung ke jalur encoder RTMP
 */
class ProBroadcastDspEffect(
    private val onTelemetryUpdate: (
        micNormalized: Float,
        sfxNormalized: Float,
        isVoiceSpeaking: Boolean
    ) -> Unit
) : CustomAudioEffect() {

    @Volatile
    var noiseGateEnabled: Boolean = true

    @Volatile
    var noiseGateThresholdDb: Float = -42f

    @Volatile
    var voiceDuckingThresholdDb: Float = -25f

    @Volatile
    var liveCensorToneActive: Boolean = false

    @Volatile
    private var activeSfxPcm: ShortArray? = null

    @Volatile
    private var activeSfxCursor: Int = 0

    private var censorPhase: Double = 0.0
    private var lastEmitMs: Long = 0L
    private var holdSpeakingUntilMs: Long = 0L

    fun enqueueSfxSamples(samples: ShortArray) {
        activeSfxCursor = 0
        activeSfxPcm = samples
    }

    fun clearSfxSamples() {
        activeSfxPcm = null
        activeSfxCursor = 0
    }

    override fun process(pcmBuffer: ByteArray): ByteArray {
        if (pcmBuffer.size < 2) return pcmBuffer

        // 1. Jika tombol Sensor Suara 1kHz ditahan, timpa input mic dengan nada 1000Hz murni
        if (liveCensorToneActive) {
            var idx = 0
            val phaseStep = 2.0 * PI * 1000.0 / 48_000.0
            while (idx + 1 < pcmBuffer.size) {
                val beepVal = (sin(censorPhase) * 20_000.0).toInt().toShort()
                censorPhase += phaseStep
                if (censorPhase > 2.0 * PI) censorPhase -= 2.0 * PI
                pcmBuffer[idx] = (beepVal.toInt() and 0xFF).toByte()
                pcmBuffer[idx + 1] = ((beepVal.toInt() shr 8) and 0xFF).toByte()
                idx += 2
            }
            emitMetricsIfDue(0.82f, 0.82f, true)
            return pcmBuffer
        }

        // 2. Hitung RMS dBFS dari input mikrofon
        var sumSquares = 0.0
        val sampleCount = pcmBuffer.size / 2
        var i = 0
        while (i + 1 < pcmBuffer.size) {
            val low = pcmBuffer[i].toInt() and 0xFF
            val high = pcmBuffer[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toDouble()
            sumSquares += sample * sample
            i += 2
        }

        val rms = sqrt(sumSquares / sampleCount.coerceAtLeast(1))
        val dbFs = if (rms > 1.0) {
            (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }

        if (noiseGateEnabled && dbFs < noiseGateThresholdDb) {
            pcmBuffer.fill(0)
        }

        // 3. Mix dengan buffer SoundFX Deck bila ada efek suara yang sedang berbunyi
        var peakSfx = 0
        val sfxArray = activeSfxPcm
        if (sfxArray != null) {
            var cursor = activeSfxCursor
            var byteIdx = 0
            while (byteIdx + 1 < pcmBuffer.size && cursor < sfxArray.size) {
                val low = pcmBuffer[byteIdx].toInt() and 0xFF
                val high = pcmBuffer[byteIdx + 1].toInt()
                val micSample = ((high shl 8) or low).toShort().toInt()
                val sfxSample = sfxArray[cursor].toInt()
                peakSfx = maxOf(peakSfx, abs(sfxSample))

                val mixed = (micSample + sfxSample)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcmBuffer[byteIdx] = (mixed and 0xFF).toByte()
                pcmBuffer[byteIdx + 1] = ((mixed shr 8) and 0xFF).toByte()

                cursor++
                byteIdx += 2
            }
            activeSfxCursor = cursor
            if (cursor >= sfxArray.size) {
                activeSfxPcm = null
            }
        }

        val now = System.currentTimeMillis()
        if (dbFs >= voiceDuckingThresholdDb) {
            holdSpeakingUntilMs = now + 320L
        }

        val micNorm = ((dbFs + 60f) / 60f).coerceIn(0.02f, 1.0f)
        val sfxNorm = (peakSfx / 26_000f).coerceIn(0f, 1.0f)
        emitMetricsIfDue(micNorm, sfxNorm, now < holdSpeakingUntilMs)

        return pcmBuffer
    }

    private fun emitMetricsIfDue(micNorm: Float, sfxNorm: Float, speaking: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastEmitMs >= 55L) {
            lastEmitMs = now
            onTelemetryUpdate(micNorm, sfxNorm, speaking)
        }
    }
}
