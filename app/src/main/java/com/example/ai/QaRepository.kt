package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "QaRepository"
private const val FIRST_CHUNK_TIMEOUT_MS = 5_000L
private const val UNARY_ATTEMPT_TIMEOUT_MS = 20_000L

enum class AppMode {
    AUTO,
    IMAGE_GEN
}

enum class MessageRole {
    USER,
    ASSISTANT
}

data class QaMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val imageBitmap: Bitmap? = null,
    val isGeneratedImage: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)

class QaRepository {

    companion object {
        const val MAX_CONTEXT_MESSAGES = 10
        // Primary ultra-fast model for quick, snappy answers; falls back to gemini-3.8-flash if needed.
        private const val PRIMARY_CHAT_MODEL = "gemini-3.5-flash-lite"
        private const val FALLBACK_CHAT_MODEL = "gemini-3.8-flash"
        private const val IMAGE_GEN_MODEL_NAME = "gemini-nano-banana-2.1"

        private val STRICT_ARABIC_SYSTEM_INSTRUCTION = """
            أنت مساعد ذكي ودود ولطيف جداً تتحدث باللغة العربية الواضحة والجميلة، وتلتزم بما يلي:
            1. الأسلوب اللطيف والإيموجيات: اجعل كلامك دافئاً وودوداً ومشجعاً وبعيداً عن الجفاف أو الرسمية الباردة. استخدم الإيموجيات المناسبة بشكل لطيف ومتوازن في ردودك (مثل ✨😊💡🚀📸🛡️🎮) لتكون الإجابة مبهجة وسهلة القراءة، دون وضع إيموجيات داخل كتل الأكواد.
            2. السرعة والدقة: قدّم إجابات سريعة ومنظمة وواضحة. وإذا لم تكن متأكداً من معلومة، لا تخمّن أبداً وقل بلطف: "بصراحة لا أملك معلومات مؤكدة حول هذا الأمر حالياً 🤔".
            3. تحليل الصور: عند إرسال صورة، حلّل محتواها بدقة واقرأ النصوص الظاهرة واشرحها بأسلوب جميل بناءً على ما يظهر فيها فقط.
            4. البرمجة الأخلاقية فقط: عند طلب أكواد بايثون أو تطوير ألعاب أو مابات روبلوكس (Luau)، التزم بالأكواد الأخلاقية والدفاعية والتعليمية فقط (حماية الحسابات، فحص كلمات المرور، التشفير الآمن، ألعاب Pygame، ومابات Roblox Studio). ارفض بلطف أي طلب ضار أو غير قانوني واقترح بديلاً آمناً ومفيداً.
            5. تنسيق الأكواد: ضع أي كود برمجي داخل كتل مثل ```python أو ```lua مع شرح مبسط وودود.
        """.trimIndent()
    }

    private fun buildTextModel(modelName: String, ownerDirective: String = ""): GenerativeModel {
        val fullInstruction = buildString {
            append(STRICT_ARABIC_SYSTEM_INSTRUCTION)
            val cleanDirective = ownerDirective.trim()
            if (cleanDirective.isNotEmpty()) {
                append("\n\nأمر وتوجيه خاص من مالك التطبيق (يلتزم به المساعد دائماً):\n")
                append(cleanDirective)
            }
        }
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.3f
                topP = 0.9f
                maxOutputTokens = 1400
            },
            systemInstruction = content {
                text(fullInstruction)
            }
        )
    }

    private val imageGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = IMAGE_GEN_MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private fun buildConversationContents(
        priorMessages: List<QaMessage>,
        question: String,
        attachedImage: Bitmap?
    ): List<Content> {
        val textTurns = priorMessages.filter { it.text.isNotBlank() && !it.isGeneratedImage }
        val paired = mutableListOf<QaMessage>()
        var expectUser = true

        for (msg in textTurns) {
            if (expectUser && msg.role == MessageRole.USER) {
                paired.add(msg)
                expectUser = false
            } else if (!expectUser && msg.role == MessageRole.ASSISTANT) {
                paired.add(msg)
                expectUser = true
            }
        }

        if (paired.size % 2 != 0) {
            paired.removeAt(paired.lastIndex)
        }

        val historyContents = paired.takeLast(MAX_CONTEXT_MESSAGES).map { msg ->
            val roleName = if (msg.role == MessageRole.USER) "user" else "model"
            content(role = roleName) {
                text(msg.text)
            }
        }

        val currentPrompt = content(role = "user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(question)
        }

        return historyContents + currentPrompt
    }

    fun streamAnswer(
        question: String,
        attachedImage: Bitmap?,
        priorMessages: List<QaMessage>,
        ownerDirective: String = ""
    ): Flow<String> = channelFlow {
        val allContents = buildConversationContents(
            priorMessages = priorMessages,
            question = question,
            attachedImage = attachedImage
        )

        val candidates = listOf(
            PRIMARY_CHAT_MODEL to buildTextModel(PRIMARY_CHAT_MODEL, ownerDirective),
            FALLBACK_CHAT_MODEL to buildTextModel(FALLBACK_CHAT_MODEL, ownerDirective)
        )

        var lastError: Exception? = null
        for ((modelName, model) in candidates) {
            var emitted = false
            try {
                val streamSucceeded = coroutineScope {
                    var gotFirstChunk = false
                    val streamJob = launch {
                        model.generateContentStream(allContents).collect { chunk ->
                            val chunkText = chunk.text
                            if (!chunkText.isNullOrEmpty()) {
                                gotFirstChunk = true
                                emitted = true
                                send(chunkText)
                            }
                        }
                    }
                    val watchdog = launch {
                        delay(FIRST_CHUNK_TIMEOUT_MS)
                        if (!gotFirstChunk) {
                            Log.w(TAG, "Stream first-token >5s on $modelName; switching to unary.")
                            streamJob.cancel()
                        }
                    }
                    streamJob.join()
                    watchdog.cancel()
                    emitted
                }
                if (streamSucceeded) return@channelFlow
            } catch (e: CancellationException) {
                if (!isActive) throw e
            } catch (e: Exception) {
                Log.e(TAG, "Stream failed on $modelName", e)
                if (emitted) throw e
                lastError = e
            }

            // Fast bounded unary fallback (avoids SSE stream stalls while keeping fallback budget)
            try {
                val unaryResponse = withTimeout(UNARY_ATTEMPT_TIMEOUT_MS) {
                    model.generateContent(allContents)
                }
                val unaryText = unaryResponse.text
                if (!unaryText.isNullOrBlank()) {
                    send(unaryText)
                    return@channelFlow
                }
            } catch (e: TimeoutCancellationException) {
                if (!isActive) throw e
                Log.w(TAG, "Unary timed out on $modelName after ${UNARY_ATTEMPT_TIMEOUT_MS}ms", e)
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unary failed on $modelName", e)
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("لم يرجع النموذج أي نص.")
    }.flowOn(Dispatchers.IO)

    suspend fun generateImage(
        prompt: String,
        referenceImage: Bitmap? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val response = if (referenceImage != null) {
            imageGenModel.generateContent(
                content(role = "user") {
                    image(referenceImage)
                    text(prompt)
                }
            )
        } else {
            imageGenModel.generateContent(prompt)
        }

        response.candidates.firstOrNull()
            ?.content?.parts
            ?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
            ?.image
    }
}
