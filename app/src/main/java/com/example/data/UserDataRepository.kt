package com.example.data

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.ai.ImageQuotaManager
import com.example.ai.MessageRole
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

const val OWNER_EMAIL = "fqore2012@gmail.com"

fun isOwnerEmail(email: String?): Boolean =
    email?.trim()?.equals(OWNER_EMAIL, ignoreCase = true) == true

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val darkTheme: Boolean = true,
    val suspended: Boolean = false,
    val ownerNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudChatMessageDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = MessageRole.USER.name,
    val text: String = "",
    val generatedImage: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OwnerAppConfigDoc(
    val ownerEmail: String = OWNER_EMAIL,
    val announcement: String = "",
    val ownerDirective: String = "",
    val dailyImageLimit: Int = ImageQuotaManager.DAILY_IMAGE_LIMIT,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val updatedBy: String = OWNER_EMAIL,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class UserDataRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUid != null && uid != expectedUid) {
            throw IllegalStateException("Signed-in user mismatch.")
        }
        return uid
    }

    private fun requireOwnerAdmin(): String {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Owner must be signed in.")
        val email = currentUser.email
        if (!isOwnerEmail(email) || !currentUser.isEmailVerified) {
            throw IllegalStateException("Only the verified app owner ($OWNER_EMAIL) may perform this action.")
        }
        return currentUser.uid
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDoc?> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun observeMessages(userId: String): Flow<List<CloudChatMessageDoc>> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        return db.collection("users").document(userId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    CloudChatMessageDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    fun observeAppConfig(): Flow<OwnerAppConfigDoc?> {
        return db.collection("appConfig").document("settings")
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(OwnerAppConfigDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun observeAllUsers(): Flow<List<UserProfileDoc>> {
        requireOwnerAdmin()
        return db.collection("users")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    UserProfileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    fun observeUserMessagesForOwner(targetUserId: String): Flow<List<CloudChatMessageDoc>> {
        requireOwnerAdmin()
        require(targetUserId.isNotBlank()) { "targetUserId must not be blank" }
        return db.collection("users").document(targetUserId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    CloudChatMessageDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().ifEmpty { "مستخدم" }.take(100)
            val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(254)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()

            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    put("darkTheme", true)
                    put("suspended", false)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    if (existing.getString("displayName") != safeName) {
                        put("displayName", safeName)
                    }
                    if (safeEmail != null && existing.getString("email") != safeEmail) {
                        put("email", safeEmail)
                    }
                    if (isNotEmpty()) {
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                }
                if (updates.isNotEmpty()) {
                    docRef.update(updates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateThemePreference(userId: String, darkTheme: Boolean): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid).update(
                mapOf(
                    "darkTheme" to darkTheme,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveMessage(
        userId: String,
        messageId: String,
        role: MessageRole,
        text: String,
        generatedImage: Boolean
    ): Result<Unit> {
        val cleanId = messageId.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
        val docPath = "users/$userId/messages/$cleanId"
        return try {
            val uid = requireUserId(userId)
            require(cleanId.isNotBlank()) { "Message ID must not be blank" }
            val safeText = text.trim().ifEmpty { "رسالة" }.take(12000)
            val payload = mapOf(
                "userId" to uid,
                "role" to role.name,
                "text" to safeText,
                "generatedImage" to generatedImage,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("messages").document(cleanId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllMessages(userId: String): Result<Unit> {
        val colPath = "users/$userId/messages"
        return try {
            val uid = requireUserId(userId)
            deleteAllMessagesInCollection(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    suspend fun setUserSuspended(
        targetUserId: String,
        suspended: Boolean,
        ownerNote: String? = null
    ): Result<Unit> {
        val docPath = "users/$targetUserId"
        return try {
            requireOwnerAdmin()
            val cleanTarget = targetUserId.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
            require(cleanTarget.isNotBlank()) { "Target user ID must not be blank" }
            val updates = buildMap<String, Any> {
                put("suspended", suspended)
                if (ownerNote != null) {
                    put("ownerNote", ownerNote.trim().take(500))
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(cleanTarget).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSingleMessageAsOwner(
        targetUserId: String,
        messageId: String
    ): Result<Unit> {
        val docPath = "users/$targetUserId/messages/$messageId"
        return try {
            requireOwnerAdmin()
            db.collection("users").document(targetUserId)
                .collection("messages").document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteUserMessagesAsOwner(targetUserId: String): Result<Unit> {
        val colPath = "users/$targetUserId/messages"
        return try {
            requireOwnerAdmin()
            deleteAllMessagesInCollection(targetUserId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    suspend fun saveAppConfig(
        announcement: String,
        ownerDirective: String,
        dailyImageLimit: Int,
        maintenanceMode: Boolean,
        maintenanceMessage: String
    ): Result<Unit> {
        val docPath = "appConfig/settings"
        return try {
            requireOwnerAdmin()
            val payload = mapOf(
                "ownerEmail" to OWNER_EMAIL,
                "announcement" to announcement.trim().take(400),
                "ownerDirective" to ownerDirective.trim().take(1200),
                "dailyImageLimit" to dailyImageLimit.coerceIn(1, 50),
                "maintenanceMode" to maintenanceMode,
                "maintenanceMessage" to maintenanceMessage.trim().take(300),
                "updatedBy" to OWNER_EMAIL,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("appConfig").document("settings")
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun deleteAllMessagesInCollection(uid: String) {
        val snapshot = db.collection("users").document(uid)
            .collection("messages")
            .get()
            .await()
        if (!snapshot.isEmpty) {
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
    }
}
