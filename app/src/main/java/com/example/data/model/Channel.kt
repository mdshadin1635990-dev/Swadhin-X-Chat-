package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Channel(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val topic: String? = "General",
    val icon: String? = "chat",
    @get:PropertyName("isAnnouncementOnly")
    @set:PropertyName("isAnnouncementOnly")
    var isAnnouncementOnly: Boolean = false,
    val createdBy: String = "",
    val messageCount: Long = 0L,
    val lastMessageText: String? = null,
    val lastMessageSenderName: String? = null,
    val lastMessageTime: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChannelMessage(
    val id: String = "",
    val channelId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val messageType: String = "text",
    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
