package com.example.chessscan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

private data class Word(val text: String, val cx: Float, val cy: Float, val h: Float)

object Ocr {
    /** Returns recognized words regrouped into visual rows (top to bottom, left to right). */
    suspend fun recognize(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(image).await()
            val words = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }
                .mapNotNull { e ->
                    e.boundingBox?.let { b ->
                        Word(e.text, b.exactCenterX(), b.exactCenterY(), b.height().toFloat())
                    }
                }
            if (words.isEmpty()) return ""
            val medianH = words.map { it.h }.sorted()[words.size / 2]
            val rows = mutableListOf<MutableList<Word>>()
            for (w in words.sortedBy { it.cy }) {
                val row = rows.lastOrNull()
                if (row != null && w.cy - row.map { it.cy }.average() <= medianH * 0.6) row.add(w)
                else rows.add(mutableListOf(w))
            }
            return rows.joinToString("\n") { r -> r.sortedBy { it.cx }.joinToString(" ") { it.text } }
        } finally {
            recognizer.close()
        }
    }
}
