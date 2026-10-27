package id.distream.studio.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Pemotong ekor MP4 — dipakai oleh **Replay Buffer** (setara "Replay Buffer" di OBS).
 *
 * Cara kerja singkat:
 *  1. Siaran merekam terus-menerus ke satu file buffer di cache (di-rotate tiap beberapa menit
 *     supaya tidak menghabiskan penyimpanan).
 *  2. Saat streamer menekan "Simpan Replay", file buffer difinalisasi (stopRecord) lalu hanya
 *     **N detik terakhir** yang disalin ke file MP4 baru.
 *  3. Penyalinan memakai `MediaExtractor` -> `MediaMuxer` (API platform, tanpa dependency baru),
 *     dimulai dari keyframe terdekat sebelumnya agar potongan pertama tetap bisa didekode.
 *
 * Semua operasi murni baca/tulis file, tidak menyentuh encoder.
 */
object Mp4TailTrimmer {

    private const val IO_BUFFER_BYTES = 4 * 1024 * 1024

    /** Durasi file dalam milidetik, atau null bila file tidak bisa dibaca. */
    fun durationMs(file: File): Long? {
        if (!file.exists() || file.length() <= 0L) return null
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            raw?.toLongOrNull()?.takeIf { it > 0L }
        }.getOrNull().also { runCatching { retriever.release() } }
    }

    /**
     * Menyalin [tailMs] terakhir dari [input] ke [output].
     *
     * @return true bila file hasil berhasil dibuat dan berukuran > 0.
     */
    fun trimTail(input: File, output: File, tailMs: Long): Boolean {
        val durationMs = durationMs(input) ?: return false
        val windowMs = tailMs.coerceIn(1_000L, durationMs)
        val startUs = (durationMs - windowMs).coerceAtLeast(0L) * 1_000L

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val ok = runCatching {
            extractor.setDataSource(input.absolutePath)

            var videoTrackIn = -1
            var audioTrackIn = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") && videoTrackIn < 0 -> {
                        videoTrackIn = i
                        videoFormat = format
                    }
                    mime.startsWith("audio/") && audioTrackIn < 0 -> {
                        audioTrackIn = i
                        audioFormat = format
                    }
                }
            }
            if (videoTrackIn < 0 || videoFormat == null) return@runCatching false

            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val outVideoTrack = mux.addTrack(videoFormat)
            val outAudioTrack = if (audioFormat != null) mux.addTrack(audioFormat) else -1
            mux.start()

            extractor.selectTrack(videoTrackIn)
            if (audioTrackIn >= 0) extractor.selectTrack(audioTrackIn)
            // Mulai dari keyframe terdekat sebelum titik potong, kalau tidak, klip jadi rusak.
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocateDirect(IO_BUFFER_BYTES)
            val info = MediaCodec.BufferInfo()
            var firstVideoPtsUs = -1L
            var written = 0

            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val trackIndex = extractor.sampleTrackIndex
                val ptsUs = extractor.sampleTime
                if (trackIndex == videoTrackIn && firstVideoPtsUs < 0L) firstVideoPtsUs = ptsUs
                val outTrack = when (trackIndex) {
                    videoTrackIn -> outVideoTrack
                    audioTrackIn -> outAudioTrack
                    else -> -1
                }
                // Buang audio yang datang sebelum video pertama supaya A/V tetap sinkron.
                if (outTrack >= 0 && firstVideoPtsUs >= 0L && ptsUs >= firstVideoPtsUs) {
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(0, size, ptsUs - firstVideoPtsUs, flags)
                    mux.writeSampleData(outTrack, buffer, info)
                    written++
                }
                if (!extractor.advance()) break
            }

            runCatching { mux.stop() }
            written > 0
        }.getOrElse { false }

        runCatching { extractor.release() }
        runCatching { muxer?.release() }

        if (!ok) {
            runCatching { output.delete() }
            return false
        }
        return output.length() > 0L
    }
}
