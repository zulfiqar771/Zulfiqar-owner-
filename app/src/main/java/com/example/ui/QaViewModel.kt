package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AppMode
import com.example.ai.ImageQuotaManager
import com.example.ai.ImageUtils
import com.example.ai.MessageRole
import com.example.ai.QaMessage
import com.example.ai.QaRepository
import com.example.data.CloudChatMessageDoc
import com.example.data.OperationType
import com.example.data.OwnerAppConfigDoc
import com.example.data.UserDataRepository
import com.example.data.UserProfileDoc
import com.example.data.handleFirestoreError
import com.example.data.isOwnerEmail
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID

private const val TAG = "QaViewModel"
private const val CHAT_TIMEOUT_MS = 60_000L
private const val IMAGE_TIMEOUT_MS = 60_000L

sealed interface QaUiState {
    data object Idle : QaUiState
    data object Loading : QaUiState
    data object GeneratingImage : QaUiState
    data class Streaming(val partialText: String) : QaUiState
    data object Success : QaUiState
    data class Error(
        val message: String,
        val failedQuestion: String,
        val failedImage: Bitmap?,
        val failedMode: AppMode
    ) : QaUiState
}

class QaViewModel(
    private val appContext: Context,
    private val userDataRepository: UserDataRepository,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?
) : ViewModel() {

    val isOwner: Boolean = isOwnerEmail(userEmail)

    private val repository = QaRepository()
    private val quotaManager = ImageQuotaManager(appContext)
    private val sessionBitmaps = mutableMapOf<String, Bitmap>()

    private val _messages = MutableStateFlow<List<QaMessage>>(emptyList())
    val messages: StateFlow<List<QaMessage>> = _messages.asStateFlow()

    private val _inputValue = MutableStateFlow(TextFieldValue(""))
    val inputValue: StateFlow<TextFieldValue> = _inputValue.asStateFlow()

    val hasInputText: StateFlow<Boolean> = _inputValue
        .map { it.text.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _attachedImage = MutableStateFlow<Bitmap?>(null)
    val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

    private val _selectedMode = MutableStateFlow(AppMode.AUTO)
    val selectedMode: StateFlow<AppMode> = _selectedMode.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _appConfig = MutableStateFlow(OwnerAppConfigDoc())
    val appConfig: StateFlow<OwnerAppConfigDoc> = _appConfig.asStateFlow()

    private val _isSuspended = MutableStateFlow(false)
    val isSuspended: StateFlow<Boolean> = _isSuspended.asStateFlow()

    private val _ownerNote = MutableStateFlow<String?>(null)
    val ownerNote: StateFlow<String?> = _ownerNote.asStateFlow()

    private val _remainingImageQuota = MutableStateFlow(quotaManager.remainingCount())
    val remainingImageQuota: StateFlow<Int> = _remainingImageQuota.asStateFlow()

    private val _uiState = MutableStateFlow<QaUiState>(QaUiState.Idle)
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    // Owner dashboard state (populated only when isOwner == true)
    private val _allUsers = MutableStateFlow<List<UserProfileDoc>>(emptyList())
    val allUsers: StateFlow<List<UserProfileDoc>> = _allUsers.asStateFlow()

    private val _inspectedUser = MutableStateFlow<UserProfileDoc?>(null)
    val inspectedUser: StateFlow<UserProfileDoc?> = _inspectedUser.asStateFlow()

    private val _inspectedUserMessages = MutableStateFlow<List<CloudChatMessageDoc>>(emptyList())
    val inspectedUserMessages: StateFlow<List<CloudChatMessageDoc>> = _inspectedUserMessages.asStateFlow()

    private val _ownerActionFeedback = MutableStateFlow<String?>(null)
    val ownerActionFeedback: StateFlow<String?> = _ownerActionFeedback.asStateFlow()

    private var activeJob: Job? = null
    private var inspectedMessagesJob: Job? = null
    private var hasAppliedCloudTheme = false

    init {
        require(currentUserId.isNotBlank()) { "QaViewModel requires an authenticated user ID." }

        viewModelScope.launch {
            userDataRepository.ensureUserProfile(
                userId = currentUserId,
                displayName = userDisplayName,
                email = userEmail
            )
        }

        // Observe own profile (theme + owner suspension status) with two-tier Flow unwrapping
        viewModelScope.launch {
            userDataRepository.observeUserProfile(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    handleFirestoreError(rootError, OperationType.GET, "users/$currentUserId")
                    Log.w(TAG, "Error observing profile", rootError)
                }
                .collect { profile ->
                    if (profile != null) {
                        if (!hasAppliedCloudTheme) {
                            hasAppliedCloudTheme = true
                            _isDarkTheme.value = profile.darkTheme
                        }
                        _isSuspended.value = !isOwner && profile.suspended
                        _ownerNote.value = profile.ownerNote
                    }
                }
        }

        // Observe global app settings managed by the Owner
        viewModelScope.launch {
            userDataRepository.observeAppConfig()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    handleFirestoreError(rootError, OperationType.GET, "appConfig/settings")
                    Log.w(TAG, "Error observing appConfig", rootError)
                }
                .collect { config ->
                    val resolved = config ?: OwnerAppConfigDoc()
                    _appConfig.value = resolved
                    _remainingImageQuota.value = quotaManager.remainingCount(resolved.dailyImageLimit)
                }
        }

        // Observe persisted user chat history from Firestore
        viewModelScope.launch {
            userDataRepository.observeMessages(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    handleFirestoreError(rootError, OperationType.LIST, "users/$currentUserId/messages")
                    Log.w(TAG, "Error observing cloud messages", rootError)
                }
                .collect { cloudDocs ->
                    val mapped = cloudDocs.map { doc ->
                        val role = if (doc.role == MessageRole.ASSISTANT.name) {
                            MessageRole.ASSISTANT
                        } else {
                            MessageRole.USER
                        }
                        QaMessage(
                            id = doc.id,
                            role = role,
                            text = doc.text,
                            imageBitmap = sessionBitmaps[doc.id],
                            isGeneratedImage = doc.generatedImage,
                            timestampMillis = doc.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    }
                    if (mapped.isNotEmpty() || _messages.value.isEmpty()) {
                        _messages.value = mapped
                    }
                }
        }

        // Owner-only live user roster listener
        if (isOwner) {
            viewModelScope.launch {
                userDataRepository.observeAllUsers()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        handleFirestoreError(rootError, OperationType.LIST, "users")
                        Log.w(TAG, "Error observing all users as owner", rootError)
                    }
                    .collect { users ->
                        _allUsers.value = users
                        val currentlyInspected = _inspectedUser.value
                        if (currentlyInspected != null) {
                            _inspectedUser.value = users.firstOrNull { it.userId == currentlyInspected.userId }
                        }
                    }
            }
        }
    }

    fun selectUserToInspect(user: UserProfileDoc?) {
        if (!isOwner) return
        inspectedMessagesJob?.cancel()
        _inspectedUser.value = user
        _inspectedUserMessages.value = emptyList()
        if (user == null) return

        inspectedMessagesJob = viewModelScope.launch {
            userDataRepository.observeUserMessagesForOwner(user.userId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    handleFirestoreError(rootError, OperationType.LIST, "users/${user.userId}/messages")
                    _ownerActionFeedback.value = "تعذر تحميل رسائل المستخدم: ${rootError.localizedMessage}"
                }
                .collect { msgs ->
                    _inspectedUserMessages.value = msgs
                }
        }
    }

    fun toggleUserSuspension(user: UserProfileDoc, ownerNote: String? = null) {
        if (!isOwner || user.userId == currentUserId) return
        val nextSuspended = !user.suspended
        viewModelScope.launch {
            val res = userDataRepository.setUserSuspended(
                targetUserId = user.userId,
                suspended = nextSuspended,
                ownerNote = ownerNote
            )
            _ownerActionFeedback.value = if (res.isSuccess) {
                if (nextSuspended) "تم إيقاف حساب ${user.displayName} بنجاح."
                else "تم تفعيل حساب ${user.displayName} بنجاح."
            } else {
                "تعذر تحديث حالة المستخدم: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteSingleMessageAsOwner(targetUserId: String, messageId: String) {
        if (!isOwner) return
        viewModelScope.launch {
            val res = userDataRepository.deleteSingleMessageAsOwner(targetUserId, messageId)
            if (res.isFailure) {
                _ownerActionFeedback.value = "تعذر حذف الرسالة: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearUserChatAsOwner(targetUserId: String) {
        if (!isOwner) return
        viewModelScope.launch {
            val res = userDataRepository.deleteUserMessagesAsOwner(targetUserId)
            _ownerActionFeedback.value = if (res.isSuccess) {
                "تم مسح محادثات المستخدم بالكامل."
            } else {
                "تعذر مسح المحادثات: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveOwnerAppConfig(
        announcement: String,
        ownerDirective: String,
        dailyImageLimit: Int,
        maintenanceMode: Boolean,
        maintenanceMessage: String
    ) {
        if (!isOwner) return
        viewModelScope.launch {
            val res = userDataRepository.saveAppConfig(
                announcement = announcement,
                ownerDirective = ownerDirective,
                dailyImageLimit = dailyImageLimit,
                maintenanceMode = maintenanceMode,
                maintenanceMessage = maintenanceMessage
            )
            _ownerActionFeedback.value = if (res.isSuccess) {
                "تم حفظ وتطبيق أوامر المالك بنجاح 👑"
            } else {
                "تعذر حفظ الأوامر: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearOwnerFeedback() {
        _ownerActionFeedback.value = null
    }

    fun setMode(mode: AppMode) {
        _selectedMode.value = mode
    }

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        hasAppliedCloudTheme = true
        viewModelScope.launch {
            userDataRepository.updateThemePreference(currentUserId, next)
        }
    }

    fun onInputValueChange(value: TextFieldValue) {
        _inputValue.value = value
    }

    fun insertTextAtCursor(insert: String) {
        _inputValue.update { current ->
            val text = current.text
            val start = current.selection.start.coerceIn(0, text.length)
            val end = current.selection.end.coerceIn(0, text.length)
            val minIdx = minOf(start, end)
            val maxIdx = maxOf(start, end)
            val updatedText = buildString(text.length + insert.length) {
                append(text, 0, minIdx)
                append(insert)
                append(text, maxIdx, text.length)
            }
            TextFieldValue(
                text = updatedText,
                selection = TextRange(minIdx + insert.length)
            )
        }
    }

    fun backspaceAtCursor() {
        _inputValue.update { current ->
            val text = current.text
            if (text.isEmpty()) return@update current
            val start = current.selection.start.coerceIn(0, text.length)
            val end = current.selection.end.coerceIn(0, text.length)
            val minIdx = minOf(start, end)
            val maxIdx = maxOf(start, end)
            if (minIdx != maxIdx) {
                val updatedText = text.removeRange(minIdx, maxIdx)
                TextFieldValue(
                    text = updatedText,
                    selection = TextRange(minIdx)
                )
            } else if (minIdx > 0) {
                val updatedText = text.removeRange(minIdx - 1, minIdx)
                TextFieldValue(
                    text = updatedText,
                    selection = TextRange(minIdx - 1)
                )
            } else {
                current
            }
        }
    }

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageUtils.decodeUriToSoftwareBitmap(appContext, uri)
            }
            if (bitmap != null) {
                _attachedImage.value = bitmap
            }
        }
    }

    fun clearAttachedImage() {
        _attachedImage.value = null
    }

    private fun isImageGenerationIntent(text: String): Boolean {
        val normalized = text.trim().lowercase()
        val isCodeOrExplainRequest = listOf(
            "كود", "بايثون", "python", "روبلوكس", "roblox", "lua", "luau", "اشرح", "كيف"
        ).any { normalized.contains(it) }
        if (isCodeOrExplainRequest) return false

        val explicitTriggers = listOf(
            "ارسم", "ارسملي", "ارسم لي",
            "اصنع صورة", "اصنع لي صورة", "صنع صورة",
            "صمم صورة", "صمم لي صورة",
            "ولّد صورة", "ولد صورة", "توليد صورة",
            "اعمل صورة", "سوي صورة", "سويلي صورة",
            "انشئ صورة", "أنشئ صورة",
            "draw ", "generate image", "generate an image", "create image", "create an image"
        )
        return explicitTriggers.any { normalized.contains(it) }
    }

    private fun persistMessageAsync(message: QaMessage) {
        message.imageBitmap?.let { bmp ->
            sessionBitmaps[message.id] = bmp
        }
        viewModelScope.launch {
            userDataRepository.saveMessage(
                userId = currentUserId,
                messageId = message.id,
                role = message.role,
                text = message.text,
                generatedImage = message.isGeneratedImage
            )
        }
    }

    fun send(
        customText: String? = null,
        defaultVisionPrompt: String = "اشرح محتوى هذه الصورة بالتفصيل وأجب عن أي نصوص أو عناصر ظاهرة فيها."
    ) {
        if (!isOwner && (_isSuspended.value || _appConfig.value.maintenanceMode)) {
            return
        }
        if (_uiState.value is QaUiState.Loading ||
            _uiState.value is QaUiState.Streaming ||
            _uiState.value is QaUiState.GeneratingImage
        ) {
            return
        }

        val currentLimit = _appConfig.value.dailyImageLimit.coerceIn(1, 50)
        val currentMode = _selectedMode.value
        val imageToSend = _attachedImage.value
        val rawText = (customText ?: _inputValue.value.text).trim()

        val shouldGenerateImage = currentMode == AppMode.IMAGE_GEN ||
            (currentMode == AppMode.AUTO && imageToSend == null && rawText.isNotEmpty() && isImageGenerationIntent(rawText))

        if (shouldGenerateImage) {
            if (rawText.isEmpty()) return
            val remaining = quotaManager.remainingCount(currentLimit)
            _remainingImageQuota.value = remaining
            if (remaining <= 0) {
                _uiState.value = QaUiState.Error(
                    message = "وصلت للحد اليومي لصناعة الصور ($currentLimit صور). يتجدد الرصيد غداً، ويمكنك الاستمرار في الدردشة وتحليل الصور والبرمجة ✨.",
                    failedQuestion = rawText,
                    failedImage = imageToSend,
                    failedMode = AppMode.IMAGE_GEN
                )
                return
            }

            val historyBefore = _messages.value
            val userMsg = QaMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                text = rawText,
                imageBitmap = imageToSend
            )
            _messages.value = historyBefore + userMsg
            persistMessageAsync(userMsg)
            if (customText == null) _inputValue.value = TextFieldValue("")
            _attachedImage.value = null

            executeImageGeneration(prompt = rawText, referenceImage = imageToSend)
            return
        }

        if (rawText.isEmpty() && imageToSend == null) return
        val questionText = if (rawText.isNotEmpty()) rawText else defaultVisionPrompt

        val historyBefore = _messages.value
        val userMsg = QaMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = questionText,
            imageBitmap = imageToSend
        )
        _messages.value = historyBefore + userMsg
        persistMessageAsync(userMsg)
        if (customText == null) _inputValue.value = TextFieldValue("")
        _attachedImage.value = null

        executeTextOrVisionQuery(
            question = questionText,
            attachedImage = imageToSend,
            priorMessages = historyBefore,
            mode = currentMode
        )
    }

    fun retryLast() {
        val errorState = _uiState.value as? QaUiState.Error ?: return
        if (errorState.failedMode == AppMode.IMAGE_GEN) {
            executeImageGeneration(
                prompt = errorState.failedQuestion,
                referenceImage = errorState.failedImage
            )
            return
        }

        val currentMessages = _messages.value
        val historyBefore = if (
            currentMessages.lastOrNull()?.role == MessageRole.USER &&
            currentMessages.lastOrNull()?.text == errorState.failedQuestion
        ) {
            currentMessages.dropLast(1)
        } else {
            currentMessages
        }

        executeTextOrVisionQuery(
            question = errorState.failedQuestion,
            attachedImage = errorState.failedImage,
            priorMessages = historyBefore,
            mode = errorState.failedMode
        )
    }

    fun stopGenerating() {
        activeJob?.cancel()
        activeJob = null
        val currentState = _uiState.value
        if (currentState is QaUiState.Streaming && currentState.partialText.isNotBlank()) {
            val partialMsg = QaMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                text = currentState.partialText.trim()
            )
            _messages.value = _messages.value + partialMsg
            persistMessageAsync(partialMsg)
        }
        _uiState.value = QaUiState.Idle
    }

    fun startNewConversation() {
        activeJob?.cancel()
        activeJob = null
        sessionBitmaps.clear()
        _messages.value = emptyList()
        _inputValue.value = TextFieldValue("")
        _attachedImage.value = null
        _uiState.value = QaUiState.Idle
        viewModelScope.launch {
            userDataRepository.clearAllMessages(currentUserId)
        }
    }

    private fun executeTextOrVisionQuery(
        question: String,
        attachedImage: Bitmap?,
        priorMessages: List<QaMessage>,
        mode: AppMode
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = QaUiState.Loading
            val accumulated = StringBuilder()
            val liveDirective = _appConfig.value.ownerDirective
            try {
                withTimeout(CHAT_TIMEOUT_MS) {
                    repository.streamAnswer(
                        question = question,
                        attachedImage = attachedImage,
                        priorMessages = priorMessages,
                        ownerDirective = liveDirective
                    ).collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            _uiState.value = QaUiState.Streaming(accumulated.toString())
                        }
                    }
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    val replyMsg = QaMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        text = finalText
                    )
                    _messages.value = _messages.value + replyMsg
                    persistMessageAsync(replyMsg)
                    _uiState.value = QaUiState.Success
                } else {
                    _uiState.value = QaUiState.Error(
                        message = "لم يصل رد نصي من النموذج. يرجى المحاولة مرة أخرى 🙏.",
                        failedQuestion = question,
                        failedImage = attachedImage,
                        failedMode = mode
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini request timed out", e)
                _uiState.value = QaUiState.Error(
                    message = "انتهت مهلة الانتظار (60 ثانية). تحقق من استقرار الإنترنت ثم أعد المحاولة ⏳.",
                    failedQuestion = question,
                    failedImage = attachedImage,
                    failedMode = mode
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini request failed", e)
                _uiState.value = QaUiState.Error(
                    message = mapErrorToArabic(e, isImageGen = false),
                    failedQuestion = question,
                    failedImage = attachedImage,
                    failedMode = mode
                )
            }
        }
    }

    private fun executeImageGeneration(
        prompt: String,
        referenceImage: Bitmap?
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = QaUiState.GeneratingImage
            val currentLimit = _appConfig.value.dailyImageLimit.coerceIn(1, 50)
            try {
                val generatedBitmap = withTimeout(IMAGE_TIMEOUT_MS) {
                    repository.generateImage(
                        prompt = prompt,
                        referenceImage = referenceImage
                    )
                }

                if (generatedBitmap != null) {
                    _remainingImageQuota.value = quotaManager.consumeOne(currentLimit)
                    val imageMsg = QaMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        text = "تم إنشاء الصورة بنجاح ✨",
                        imageBitmap = generatedBitmap,
                        isGeneratedImage = true
                    )
                    _messages.value = _messages.value + imageMsg
                    persistMessageAsync(imageMsg)
                    _uiState.value = QaUiState.Success
                } else {
                    _uiState.value = QaUiState.Error(
                        message = "لم يرجع النموذج صورة لهذا الوصف. جرّب وصفاً أوضح وبسيطاً 🎨.",
                        failedQuestion = prompt,
                        failedImage = referenceImage,
                        failedMode = AppMode.IMAGE_GEN
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = QaUiState.Error(
                    message = "انتهت مهلة صناعة الصورة (60 ثانية). يرجى المحاولة مرة أخرى ⏳.",
                    failedQuestion = prompt,
                    failedImage = referenceImage,
                    failedMode = AppMode.IMAGE_GEN
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = QaUiState.Error(
                    message = mapErrorToArabic(e, isImageGen = true),
                    failedQuestion = prompt,
                    failedImage = referenceImage,
                    failedMode = AppMode.IMAGE_GEN
                )
            }
        }
    }

    private fun mapErrorToArabic(error: Exception, isImageGen: Boolean): String {
        val detail = error.localizedMessage ?: error.message ?: error::class.simpleName.orEmpty()
        val raw = "${error::class.simpleName.orEmpty()} $detail".lowercase()
        return when {
            error is UnknownHostException ||
                error is IOException ||
                raw.contains("network") ||
                raw.contains("unable to resolve host") ->
                "تعذر الاتصال بالإنترنت. يرجى التأكد من الشبكة والمحاولة مرة أخرى 🌐."

            isImageGen && (raw.contains("billing") || raw.contains("blaze") || raw.contains("plan") || raw.contains("403")) ->
                "توليد الصور عبر Firebase AI Logic يتطلب تفعيل خطة Blaze في مشروع Firebase ($detail)."

            raw.contains("429") ||
                raw.contains("quota") ||
                raw.contains("resource_exhausted") ||
                raw.contains("rate limit") ->
                "تم الوصول لحد الاستخدام المؤقت للخادم. انتظر قليلاً ثم أعد المحاولة ($detail)."

            raw.contains("403") ||
                raw.contains("app check") ||
                raw.contains("attestation") ->
                "تعذر التحقق من أمان التطبيق (App Check). تأكد من اتصالك وأعد تشغيل التطبيق ($detail)."

            else ->
                "حدث خطأ أثناء الاتصال بالمساعد: $detail"
        }
    }
}
