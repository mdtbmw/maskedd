package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.update.AppUpdateInfo

/**
 * In-App Remote Self-Update Dialog Modal
 * Displays version release notes, download progress, and mandatory update enforcement.
 */
@Composable
fun AppUpdateModal(
    updateInfo: AppUpdateInfo,
    onStartUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!updateInfo.hasUpdate) return

    Dialog(
        onDismissRequest = { if (!updateInfo.isMandatory) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isMandatory,
            dismissOnClickOutside = !updateInfo.isMandatory
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF6366F1).copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "App Update",
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (updateInfo.isMandatory) "Mandatory App Update" else "New Version Available",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "v${updateInfo.latestVersionName} (Build ${updateInfo.latestVersionCode})",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), shape = RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Release Notes:",
                            style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "Performance improvements and new neural voice models." },
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (updateInfo.isDownloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Downloading Update... ${updateInfo.downloadProgressPercent}%",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { updateInfo.downloadProgressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color(0xFF6366F1),
                            trackColor = Color(0xFF334155)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!updateInfo.isMandatory) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Later", color = Color(0xFF94A3B8))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Button(
                            onClick = onStartUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Update & Install Now", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
