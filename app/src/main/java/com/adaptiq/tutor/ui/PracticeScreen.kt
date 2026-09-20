package com.adaptiq.tutor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.adaptiq.tutor.viewmodel.TutorUiState
import com.adaptiq.tutor.viewmodel.TutorViewModel
import com.adaptiq.tutor.viewmodel.Quiz

@Composable
fun PracticeScreen(viewModel: TutorViewModel) {
    val currentQuiz by viewModel.currentQuiz.collectAsState()
    val selectedOption by viewModel.selectedQuizOption.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Trigger quiz generation when screen opens if we don't have one
    LaunchedEffect(Unit) {
        if (currentQuiz == null && uiState != TutorUiState.GeneratingQuiz) {
            viewModel.generateQuiz()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA)) // Light grey background
            .verticalScroll(rememberScrollState())
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
                    text = "Knowledge Check",
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
                    Text("Generating practice quiz based on your chats...")
                }
            }
        } else if (currentQuiz != null) {
            val quiz = currentQuiz!!
            Text(
                text = quiz.question,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )

            // Options
            val letters = listOf("A", "B", "C", "D")
            quiz.options.forEachIndexed { index, optionText ->
                val isSelected = selectedOption == index
                val isCorrect = index == quiz.correctIndex
                OptionCard(
                    letter = letters.getOrElse(index) { "?" },
                    text = optionText,
                    isSelected = selectedOption != null && isSelected,
                    isCorrect = isCorrect,
                    showFeedback = selectedOption != null,
                    feedback = if (isCorrect) quiz.feedback else "Not quite! Try again or review the concept.",
                    onClick = {
                        if (selectedOption == null) {
                            viewModel.selectQuizOption(index)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Clarify Button
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = border(1.dp, Color.LightGray, RoundedCornerShape(24.dp)),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.speakMessage(quiz.feedback) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Explain",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }

                // Next Button
                Button(
                    onClick = { viewModel.generateQuiz() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Next Quiz")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun OptionCard(
    letter: String,
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showFeedback: Boolean = false,
    feedback: String? = null,
    onClick: () -> Unit = {}
) {
    val showGreen = showFeedback && isCorrect
    val showRed = showFeedback && isSelected && !isCorrect
    
    val backgroundColor = when {
        showGreen -> Color(0xFFE8F5E9)
        showRed -> Color(0xFFFFEBEE)
        else -> Color.White
    }
    
    val borderColor = when {
        showGreen -> Color(0xFF4CAF50)
        showRed -> Color(0xFFF44336)
        else -> Color(0xFFE0E0E0)
    }
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Letter Circle
                Surface(
                    color = when {
                        showGreen -> Color(0xFF4CAF50)
                        showRed -> Color(0xFFF44336)
                        else -> Color(0xFFF5F5F5)
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (showGreen) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else if (showRed) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                text = letter,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        showGreen -> Color(0xFF1B5E20)
                        showRed -> Color(0xFFB71C1C)
                        else -> Color.DarkGray
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            if ((showGreen || showRed) && feedback != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = Color(0xFFFFCC80),
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.padding(4.dp), tint = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Evaluator Agent", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = feedback,
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun border(width: androidx.compose.ui.unit.Dp, color: Color, shape: androidx.compose.ui.graphics.Shape): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}
