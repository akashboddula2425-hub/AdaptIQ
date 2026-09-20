package com.adaptiq.tutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Your Learning Path", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Complete these steps to calibrate your AI Tutor.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                StepCard(
                    title = "Diagnostic Quiz",
                    description = "Take a 5-minute quiz to assess your current knowledge level.",
                    isCompleted = true
                )
            }
            item {
                StepCard(
                    title = "Set Learning Goals",
                    description = "What do you want to achieve this week?",
                    isCompleted = true
                )
            }
            item {
                StepCard(
                    title = "First AI Tutoring Session",
                    description = "Jump in and ask your AI tutor a question about Physics.",
                    isCompleted = false,
                    isNext = true
                )
            }
            item {
                StepCard(
                    title = "Join a Study Pod",
                    description = "Connect with peers studying the same topics.",
                    isCompleted = false
                )
            }
        }
    }
}

@Composable
fun StepCard(title: String, description: String, isCompleted: Boolean, isNext: Boolean = false) {
    val backgroundColor = if (isCompleted) Color(0xFFE8F5E9) else Color.White
    val borderColor = if (isNext) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        border = if (isNext) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isCompleted) Color(0xFF4CAF50) else if (isNext) MaterialTheme.colorScheme.primary else Color.LightGray,
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp))
                } else if (isNext) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isCompleted) Color(0xFF2E7D32) else Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 13.sp, color = Color.DarkGray)
            }
        }
    }
}
