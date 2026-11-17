package com.example.druglikenesschecker.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object TextRecognitionHelper {

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<List<String>> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = mutableListOf<String>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val text = line.text.trim()
                            if (text.isNotBlank()) {
                                lines.add(text)
                            }
                        }
                    }
                    if (lines.isEmpty() && visionText.text.isNotBlank()) {
                        lines.add(visionText.text.trim())
                    }
                    continuation.resume(Result.success(lines))
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): Result<List<String>> =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val lines = mutableListOf<String>()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val text = line.text.trim()
                                if (text.isNotBlank()) {
                                    lines.add(text)
                                }
                            }
                        }
                        if (lines.isEmpty() && visionText.text.isNotBlank()) {
                            lines.add(visionText.text.trim())
                        }
                        continuation.resume(Result.success(lines))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
}
