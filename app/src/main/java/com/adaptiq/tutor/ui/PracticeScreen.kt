package com.adaptiq.tutor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptiq.tutor.viewmodel.TutorUiState
import com.adaptiq.tutor.viewmodel.TutorViewModel
import com.adaptiq.tutor.viewmodel.Flashcard

@Composable
fun PracticeScreen(viewModel: TutorViewModel) {
    val flashcards by viewModel.flashcards.collectAsState()
    val currentIndex by viewModel.currentFlashcardIndex.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Trigger quiz generation when screen opens if we don't have one
    LaunchedEffect(Unit) {
        if (flashcards.isEmpty() && uiState != TutorUiState.GeneratingQuiz) {
            viewModel.generateFlashcards()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA)) // Light grey background
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2C3E50)) // Fallback color
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "PRACTICE LAB",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "AI Generated Flashcards",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (uiState == TutorUiState.GeneratingQuiz) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generating flashcards from your conversations...", textAlign = TextAlign.Center)
                }
            }
        } else if (flashcards.isNotEmpty()) {
            val card = flashcards[currentIndex]
            
            Text(
                text = "Card ${currentIndex + 1} of ${flashcards.size}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Flippable Card
            var flipped by remember { mutableStateOf(false) }
            
            // Reset flipped state when card changes
            LaunchedEffect(currentIndex) { flipped = false }

            val rotation by animateFloatAsState(
                targetValue = if (flipped) 180f else 0f,
                animationSpec = tween(durationMillis = 400),
                label = "flip"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clickable { flipped = !flipped },
                contentAlignment = Alignment.Center
            ) {
                if (rotation <= 90f) {
                    // Front
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = card.front,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Back
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = card.back,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Generate New Button
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.generateFlashcards() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Regenerate", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Next Button
                Button(
                    onClick = {
                        if (currentIndex < flashcards.size - 1) {
                            viewModel.nextFlashcard()
                        } else {
                            viewModel.generateFlashcards()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (currentIndex < flashcards.size - 1) "Next Card" else "New Set")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
