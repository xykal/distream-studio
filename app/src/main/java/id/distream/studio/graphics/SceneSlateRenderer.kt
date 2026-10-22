package id.distream.studio.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import id.distream.studio.model.StudioScene

/**
 * Generator grafis resolusi tinggi untuk Scene:
 * - Segera Mulai (Starting Soon)
 * - Istirahat (Be Right Back)
 * - Layar Privasi (Privacy Shield untuk proteksi OTP / Password / Chat Pribadi)
 */
object SceneSlateRenderer {

    fun createSlateBitmap(
        width: Int,
        height: Int,
        scene: StudioScene,
        headline: String,
        handle: String
    ): Bitmap {
        val safeW = width.coerceAtLeast(360)
        val safeH = height.coerceAtLeast(360)
        val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, safeW.toFloat(), safeH.toFloat(),
                intArrayOf(
                    Color.parseColor("#131417"),
                    Color.parseColor("#1B1D24"),
                    Color.parseColor("#121316")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, safeW.toFloat(), safeH.toFloat(), bgPaint)

        val margin = safeW * 0.085f
        val centerY = safeH * 0.48f
        val cardHeight = safeW * 0.44f
        val rect = RectF(
            margin,
            centerY - cardHeight / 2f,
            safeW - margin,
            centerY + cardHeight / 2f
        )

        val accentHex = if (scene == StudioScene.PRIVACY_SHIELD) "#F59E0B" else "#2563EB"

        canvas.drawRoundRect(
            rect,
            28f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F0191B20")
                style = Paint.Style.FILL
            }
        )
        canvas.drawRoundRect(
            rect,
            28f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentHex)
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
        )

        val badgeText = when (scene) {
            StudioScene.STARTING_SOON -> "SIARAN SEGERA DIMULAI"
            StudioScene.INTERMISSION_BRB -> "ISTIRAHAT SEJENAK"
            StudioScene.PRIVACY_SHIELD -> "MODE PRIVASI AKTIF"
            else -> "DISTREAM STUDIO"
        }

        val mainTitle = when (scene) {
            StudioScene.STARTING_SOON -> headline.ifBlank { "Siaran Segera Dimulai" }
            StudioScene.INTERMISSION_BRB -> "Kembali dalam beberapa saat"
            StudioScene.PRIVACY_SHIELD -> "Layar sedang disembunyikan sementara"
            else -> headline
        }

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentHex)
            textSize = safeW * 0.030f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(badgeText, safeW / 2f, rect.top + cardHeight * 0.30f, tagPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FAFAFA")
            textSize = safeW * 0.046f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(mainTitle.take(30), safeW / 2f, rect.top + cardHeight * 0.56f, titlePaint)

        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = safeW * 0.030f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val cleanHandle = if (handle.isBlank()) "DiStream Studio" else "@${handle.trim().removePrefix("@")}"
        canvas.drawText(cleanHandle, safeW / 2f, rect.top + cardHeight * 0.80f, handlePaint)

        return bmp
    }
}
