package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.auth.AuthHelper
import com.example.data.repository.ChatRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.main.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        val currentUser by AuthHelper.authStateFlow()
            .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = {
                    // Reactive auth state updates automatically
                }
            )
        } else {
            val repository = remember(user.uid) {
                ChatRepository(this@MainActivity)
            }

            MainScreen(
                currentUser = user,
                repository = repository,
                onSignOut = {
                    AuthHelper.signOut(
                        context = this@MainActivity,
                        credentialManager = CredentialManager.create(this@MainActivity),
                        onSignOutComplete = {
                            // User returns to AuthScreen
                        },
                        scope = lifecycleScope
                    )
                }
            )
        }
    }
}
