package com.example.chessscan

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

object MoveParser {
    data class Correction(val ply: Int, val from: String, val to: String) {
        val label: String
            get() = "Move ${ply / 2 + 1}${if (ply % 2 == 0) "" else "..."}: '$from' -> '$to'"
    }

    data class AutoResult(val text: String, val corrections: List<Correction>)

    data class Validation(
        val sans: List<String>,
        val moves: List<Move>,
        val board: Board,
        val badPly: Int?,
        val badToken: String?
    )

    private class Cand(val key: String, val san: String, val move: Move)

    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "½-½", "*")
    private val NUMBER_PREFIX = Regex("^\\d+\\s*[.)]+\\.*")
    private val CASTLE = Regex("^[0oO](-[0oO])+$")

    // ---- Tokenizing -------------------------------------------------------

    fun tokenize(raw: String): List<String> = raw.split(Regex("\\s+")).mapNotNull { clean(it) }

    private fun clean(t0: String): String? {
        var t = t0.trim().trim(',', ';', ':', '|', '_', '"', '\'')
        if (t in RESULTS) return null
        t = t.replace(NUMBER_PREFIX, "").trimEnd('+', '#', '!', '?')
        if (t.isEmpty() || t.all { it.isDigit() }) return null
        if (t.length < 2 || t.length > 8) return null
        return t
    }

    /** Comparison key: ignores check marks, captures, dashes, '=' and maps 0-0 to O-O. */
    private fun key(s: String): String {
        if (CASTLE.matches(s)) return "O".repeat(s.count { it == '0' || it == 'o' || it == 'O' })
        return s.filter { it !in "+#!?xX:-=" }
    }

    // ---- Legal move generation -------------------------------------------

    private fun legal(board: Board): List<Cand> {
        val fen = board.getFen()
        return board.legalMoves().map { m ->
            val ml = MoveList(fen)
            ml.add(m)
            val san = ml.toSanArray()[0]
            Cand(key(san), san, m)
        }
    }

    // ---- Fuzzy matching ---------------------------------------------------

    private val GROUPS = listOf("1lI|i", "5S", "0OQD", "8B", "6bG", "2Zz", "ce", "gq9", "4A", "7T", "hn", "ao")

    private fun sub(a: Char, b: Char): Double = when {
        a == b -> 0.0
        a.equals(b, ignoreCase = true) -> 0.4
        GROUPS.any { a in it && b in it } -> 0.4
        else -> 1.0
    }

    private fun dist(a: String, b: String): Double {
        val dp = Array(a.length + 1) { DoubleArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i.toDouble()
        for (j in 0..b.length) dp[0][j] = j.toDouble()
        for (i in 1..a.length) for (j in 1..b.length)
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + sub(a[i - 1], b[j - 1]))
        return dp[a.length][b.length]
    }

    private fun fuzzy(tk: String, cands: List<Cand>): Cand? {
        val scored = cands.map { it to dist(tk, it.key) }.sortedBy { it.second }
        val best = scored.firstOrNull() ?: return null
        if (best.second > 1.0) return null                                        // too far off
        if (scored.size > 1 && scored[1].second - best.second < 0.01) return null // ambiguous
        return best.first
    }

    // ---- Public API -------------------------------------------------------

    /**
     * Replays tokens from the start position, snapping each to the closest legal move.
     * Stops at the first move it can't resolve and leaves the remaining raw tokens for manual fixing.
     */
    fun autoCorrect(tokens: List<String>): AutoResult {
        val board = Board()
        val out = mutableListOf<String>()
        val corrections = mutableListOf<Correction>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val cands = legal(board)
            val k = key(t)
            val exact = cands.firstOrNull { it.key == k }
            val chosen = exact ?: fuzzy(k, cands)
            if (chosen == null) {
                if (out.isEmpty()) { i++; continue }   // skip header junk before the first move
                break
            }
            if (exact == null) corrections.add(Correction(out.size, t, chosen.san))
            board.doMove(chosen.move)
            out.add(chosen.san)
            i++
        }
        if (out.isEmpty()) return AutoResult(tokens.joinToString(" "), emptyList())
        val rest = tokens.drop(i)
        val text = format(out) + if (rest.isEmpty()) "" else " " + rest.joinToString(" ")
        return AutoResult(text, corrections)
    }

    fun validate(text: String): Validation {
        val board = Board()
        val sans = mutableListOf<String>()
        val moves = mutableListOf<Move>()
        for ((i, t) in tokenize(text).withIndex()) {
            val k = key(t)
            val c = legal(board).firstOrNull { it.key == k }
                ?: return Validation(sans, moves, board, i, t)
            board.doMove(c.move)
            sans.add(c.san)
            moves.add(c.move)
        }
        return Validation(sans, moves, board, null, null)
    }

    fun format(sans: List<String>): String =
        sans.chunked(2).mapIndexed { i, p -> "${i + 1}. ${p.joinToString(" ")}" }.joinToString(" ")
}
