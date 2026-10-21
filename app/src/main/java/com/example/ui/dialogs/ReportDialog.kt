package com.example.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RoseError

@Composable
fun ReportDialog(
    targetName: String,
    reportedUserId: String?,
    reportedMessageId: String?,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, reportedUserId: String?, reportedMessageId: String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Inappropriate Content") }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf("Inappropriate Content", "Spam / Advertising", "Harassment", "Impersonation")

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = RoseError
            )
        },
        title = {
            Text(
                text = "Report to Admin",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Report regarding: $targetName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Violation Type", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))

                categories.forEach { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedCategory = category },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCategory == category) RoseError.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedCategory == category) RoseError else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text("Details for Admin Review") },
                    placeholder = { Text("Explain what occurred...") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullReason = "$selectedCategory: ${reason.trim()}"
                    isSubmitting = true
                    onSubmit(fullReason, reportedUserId, reportedMessageId)
                },
                enabled = reason.trim().length >= 3 && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseError,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("submit_report_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit Report")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}
