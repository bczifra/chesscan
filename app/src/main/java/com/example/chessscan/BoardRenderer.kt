package com.example.chessscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.Piece

object BoardRenderer {
    private const val W = 1080
    private const val MARGIN = 40
    private const val SQ = 125f
    private const val BOARD = 1000

    private const val LIGHT = 0xFFF0D9B5.toInt()
    private const val DARK = 0xFFB58863.toInt()
    private const val FRAME = 0xFF5C3A21.toInt()
    private const val BG = 0xFFF6ECDC.toInt()
    private const val INK = 0xFF3B2412.toInt()

    /** Set to a bundled chess font if your device renders the piece glyphs poorly. */
    var pieceTypeface: Typeface? = null

    private val GLYPHS = mapOf(
        'K' to "\u265A", 'Q' to "\u265B", 'R' to "\u265C",
        'B' to "\u265D", 'N' to "\u265E", 'P' to "\u265F"
    )

    fun render(v: MoveParser.Validation, white: String, black: String, result: String): Bitmap {
        val fullMoves = (v.sans.size + 1) / 2
        val cols = 3
        val rows = maxOf(1, (fullMoves + cols - 1) / cols)
        val headerH = 150
        val rowH = 52
        val listTop = headerH + BOARD + 50
        val height = listTop + rows * rowH + 60

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)

        // Header
        val title = if (white.isBlank() && black.isBlank()) "Chess game"
        else "${white.ifBlank { "?" }} vs ${black.ifBlank { "?" }}"
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 54f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        c.drawText(
            TextUtils.ellipsize(title, titlePaint, (W - 2 * MARGIN).toFloat(), TextUtils.TruncateAt.END).toString(),
            MARGIN.toFloat(), 80f, titlePaint
        )
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A5A3A.toInt(); textSize = 32f }
        val subtitle = listOfNotNull(result.ifBlank { null }, "$fullMoves moves").joinToString("  ·  ")
        c.drawText(subtitle, MARGIN.toFloat(), 125f, sub)

        // Board frame + squares
        val bx = MARGIN.toFloat()
        val by = headerH.toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = FRAME
        c.drawRect(bx - 10, by - 10, bx + BOARD + 10, by + BOARD + 10, fill)

        val last = v.moves.lastOrNull()
        val coord = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        for (r in 0 until 8) for (f in 0 until 8) {
            val rank = 7 - r
            val light = (f + rank) % 2 == 1
            val x = bx + f * SQ
            val y = by + r * SQ
            fill.color = if (light) LIGHT else DARK
            c.drawRect(x, y, x + SQ, y + SQ, fill)
            val name = "${'a' + f}${rank + 1}"
            if (last != null && (last.from.name.equals(name, true) || last.to.name.equals(name, true))) {
                fill.color = 0x80F6E05A.toInt()
                c.drawRect(x, y, x + SQ, y + SQ, fill)
            }
            coord.color = if (light) DARK else LIGHT
            if (f == 0) c.drawText("${rank + 1}", x + 6, y + 24, coord)
            if (rank == 0) c.drawText("${'a' + f}", x + SQ - 20, y + SQ - 8, coord)
        }

        // Pieces
        val fillP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 108f; textAlign = Paint.Align.CENTER
            pieceTypeface?.let { typeface = it }
        }
        val strokeP = TextPaint(fillP).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = 0xFF1A1A1A.toInt()
        }
        val fm = fillP.fontMetrics
        for (r in 0 until 8) for (f in 0 until 8) {
            val rank = 7 - r
            val piece = v.board.getPiece(Square.valueOf("${'A' + f}${rank + 1}"))
            if (piece == Piece.NONE) continue
            val sym = piece.fenSymbol[0]
            val glyph = (GLYPHS[sym.uppercaseChar()] ?: continue) + "\uFE0E"
            val cx = bx + f * SQ + SQ / 2
            val cy = by + r * SQ + SQ / 2 - (fm.ascent + fm.descent) / 2
            if (sym.isUpperCase()) {
                c.drawText(glyph, cx, cy, strokeP)
                fillP.color = 0xFFFFFFFF.toInt()
            } else {
                fillP.color = 0xFF1A1A1A.toInt()
            }
            c.drawText(glyph, cx, cy, fillP)
        }

        // Move list (3 columns, filled top-to-bottom)
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 32f; typeface = Typeface.MONOSPACE
        }
        for (n in 0 until fullMoves) {
            val col = n / rows
            val row = n % rows
            val w = v.sans[2 * n]
            val b = v.sans.getOrNull(2 * n + 1) ?: ""
            c.drawText("${n + 1}. $w $b", MARGIN + col * 340f, listTop + (row + 1) * rowH - 14f, mono)
        }
        return bmp
    }
}
