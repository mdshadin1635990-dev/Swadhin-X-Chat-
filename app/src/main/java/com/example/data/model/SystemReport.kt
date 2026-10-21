package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SystemReport(
    val id: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val reportedUserId: String? = null,
    val reportedMessageId: String? = null,
    val reason: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
