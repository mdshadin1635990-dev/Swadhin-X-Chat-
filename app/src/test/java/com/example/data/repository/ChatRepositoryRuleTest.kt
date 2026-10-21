package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createChannel_authenticatedUser_createsDocumentAndEmits() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = ChatRepository(firestore)

    val channelResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createChannel("Global Hub", "International chat hub", "General", "globe", false)
    }
    assertTrue(channelResult.isSuccess)
    val channelId = channelResult.getOrThrow()
    assertTrue(channelId.isNotEmpty())

    val channels = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeChannels().first { list -> list.any { it.id == channelId } }
    }
    assertTrue(channels.any { it.id == channelId })
  }

  @Test
  fun sendChannelMessage_authenticatedUser_success() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = ChatRepository(firestore)

    val channelId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createChannel("Language Cafe", "Practice languages", "Languages", "chat", false).getOrThrow()
    }

    val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.sendChannelMessage(channelId, "Hello international friends!", "Alice", null)
    }
    assertTrue(msgResult.isSuccess)
    val msgId = msgResult.getOrThrow()

    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeChannelMessages(channelId).first { list -> list.any { it.id == msgId } }
    }
    assertTrue(messages.any { it.id == msgId })
  }

  @Test
  fun observeChannels_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = ChatRepository(firestore)

    var caughtExpectedError = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeChannels().first()
      }
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
      caughtExpectedError = true
    }
    assertTrue(caughtExpectedError)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
