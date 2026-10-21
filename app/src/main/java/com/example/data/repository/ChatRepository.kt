package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.DirectChat
import com.example.data.model.DirectChatMessage
import com.example.data.model.SystemReport
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth get() = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing ChatRepository.")
    }

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        val email = user.email ?: ""
        return email.equals("mdshadin1635990@gmail.com", ignoreCase = true)
    }

    // --- User Profile Operations ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val docRef = db.collection("users").document(userId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                close(error)
                return@addSnapshotListener
            }
            val profile = snapshot?.toObject(UserProfile::class.java)
            trySend(profile)
        }
        awaitClose { registration.remove() }
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = callbackFlow {
        val query = db.collection("users")
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users")
                close(error)
                return@addSnapshotListener
            }
            val users = snapshot?.documents?.mapNotNull { it.toObject(UserProfile::class.java) } ?: emptyList()
            trySend(users)
        }
        awaitClose { registration.remove() }
    }

    suspend fun createOrUpdateProfile(displayName: String, photoUrl: String?, statusMessage: String?): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = user.uid
        val docRef = db.collection("users").document(uid)

        val existingDoc = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        val isAdmin = isCurrentUserAdmin()
        val role = if (isAdmin) "admin" else (existingDoc?.getString("role") ?: "user")

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "email" to (user.email ?: ""),
            "displayName" to displayName.trim(),
            "photoUrl" to photoUrl,
            "statusMessage" to (statusMessage?.trim() ?: "Active on Swadhin X Chat"),
            "isOnline" to true,
            "role" to role,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (existingDoc == null || !existingDoc.exists()) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            val existingCreatedAt = existingDoc.getTimestamp("createdAt")
            if (existingCreatedAt != null) {
                payload["createdAt"] = existingCreatedAt
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
        }

        val nonNullPayload = payload.filterValues { it != null }

        return try {
            docRef.set(nonNullPayload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Channel Operations ---

    fun observeChannels(): Flow<List<Channel>> = callbackFlow {
        val query = db.collection("channels")
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "channels")
                close(error)
                return@addSnapshotListener
            }
            val channels = snapshot?.documents?.mapNotNull { it.toObject(Channel::class.java) } ?: emptyList()
            trySend(channels)
        }
        awaitClose { registration.remove() }
    }

    suspend fun createChannel(
        name: String,
        description: String,
        topic: String,
        icon: String = "chat",
        isAnnouncementOnly: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val channelId = "ch_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val docRef = db.collection("channels").document(channelId)

        val payload = mapOf(
            "id" to channelId,
            "name" to name.trim(),
            "description" to description.trim(),
            "topic" to topic.trim(),
            "icon" to icon,
            "isAnnouncementOnly" to isAnnouncementOnly,
            "createdBy" to uid,
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(channelId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChannel(channelId: String): Result<Unit> {
        val docRef = db.collection("channels").document(channelId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Channel Messages ---

    fun observeChannelMessages(channelId: String): Flow<List<ChannelMessage>> = callbackFlow {
        val query = db.collection("channels")
            .document(channelId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "channels/$channelId/messages")
                close(error)
                return@addSnapshotListener
            }
            val messages = snapshot?.documents?.mapNotNull { it.toObject(ChannelMessage::class.java) } ?: emptyList()
            trySend(messages)
        }
        awaitClose { registration.remove() }
    }

    suspend fun sendChannelMessage(
        channelId: String,
        text: String,
        senderName: String,
        senderPhotoUrl: String?,
        messageType: String = "text"
    ): Result<String> {
        val uid = requireUserId()
        val msgId = "msg_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val msgRef = db.collection("channels").document(channelId).collection("messages").document(msgId)
        val channelRef = db.collection("channels").document(channelId)

        val payload = mutableMapOf<String, Any?>(
            "id" to msgId,
            "channelId" to channelId,
            "senderId" to uid,
            "senderName" to senderName,
            "senderPhotoUrl" to senderPhotoUrl,
            "text" to text.trim(),
            "messageType" to messageType,
            "isPinned" to false,
            "deleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            msgRef.set(payload).await()
            // Update channel snippet
            channelRef.update(
                mapOf(
                    "messageCount" to FieldValue.increment(1),
                    "lastMessageText" to text.trim().take(100),
                    "lastMessageSenderName" to senderName.take(60),
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(msgId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChannelMessage(channelId: String, messageId: String): Result<Unit> {
        val msgRef = db.collection("channels").document(channelId).collection("messages").document(messageId)
        return try {
            msgRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- Direct Chats (1-on-1) ---

    fun observeDirectChats(currentUserId: String): Flow<List<DirectChat>> = callbackFlow {
        val query = db.collection("direct_chats")
            .whereArrayContains("participantUids", currentUserId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "direct_chats")
                close(error)
                return@addSnapshotListener
            }
            val chats = snapshot?.documents?.mapNotNull { it.toObject(DirectChat::class.java) } ?: emptyList()
            trySend(chats)
        }
        awaitClose { registration.remove() }
    }

    suspend fun getOrCreateDirectChat(otherUserId: String): Result<String> {
        val currentUserId = requireUserId()
        val sortedUids = listOf(currentUserId, otherUserId).sorted()
        val chatId = "dm_${sortedUids[0].take(12)}_${sortedUids[1].take(12)}"
        val docRef = db.collection("direct_chats").document(chatId)

        return try {
            val doc = docRef.get().await()
            if (!doc.exists()) {
                val payload = mapOf(
                    "id" to chatId,
                    "participantUids" to sortedUids,
                    "lastMessageText" to "Chat started",
                    "lastMessageSenderId" to currentUserId,
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(chatId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDirectMessages(chatId: String): Flow<List<DirectChatMessage>> = callbackFlow {
        val query = db.collection("direct_chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "direct_chats/$chatId/messages")
                close(error)
                return@addSnapshotListener
            }
            val messages = snapshot?.documents?.mapNotNull { it.toObject(DirectChatMessage::class.java) } ?: emptyList()
            trySend(messages)
        }
        awaitClose { registration.remove() }
    }

    suspend fun sendDirectMessage(
        chatId: String,
        text: String,
        senderName: String,
        senderPhotoUrl: String?
    ): Result<String> {
        val uid = requireUserId()
        val msgId = "dmsg_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val msgRef = db.collection("direct_chats").document(chatId).collection("messages").document(msgId)
        val chatRef = db.collection("direct_chats").document(chatId)

        val payload = mutableMapOf<String, Any?>(
            "id" to msgId,
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to senderName,
            "senderPhotoUrl" to senderPhotoUrl,
            "text" to text.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            msgRef.set(payload).await()
            chatRef.update(
                mapOf(
                    "lastMessageText" to text.trim().take(100),
                    "lastMessageSenderId" to uid,
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(msgId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- System Reports & Moderation ---

    suspend fun submitReport(reason: String, reportedUserId: String?, reportedMessageId: String?): Result<String> {
        val uid = requireUserId()
        val user = auth.currentUser
        val reportId = "rep_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val docRef = db.collection("system_reports").document(reportId)

        val payload = mutableMapOf<String, Any?>(
            "id" to reportId,
            "reporterId" to uid,
            "reporterName" to (user?.displayName ?: "Anonymous"),
            "reportedUserId" to reportedUserId,
            "reportedMessageId" to reportedMessageId,
            "reason" to reason.trim(),
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            docRef.set(payload).await()
            Result.success(reportId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeReports(): Flow<List<SystemReport>> = callbackFlow {
        val query = db.collection("system_reports")
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "system_reports")
                close(error)
                return@addSnapshotListener
            }
            val reports = snapshot?.documents?.mapNotNull { it.toObject(SystemReport::class.java) } ?: emptyList()
            trySend(reports)
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> {
        val docRef = db.collection("system_reports").document(reportId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
