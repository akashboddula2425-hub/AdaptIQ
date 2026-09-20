package com.adaptiq.tutor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
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

enum class PracticeMode {
    MENU, FLASHCARDS, MULTIPLE_CHOICE, MATCHING
}

@Composable
fun PracticeScreen(viewModel: TutorViewModel) {
    var currentMode by remember { mutableStateOf(PracticeMode.MENU) }

    when (currentMode) {
        PracticeMode.MENU -> PracticeMenu(onModeSelected = { currentMode = it })
        PracticeMode.FLASHCARDS -> FlashcardsScreen(viewModel, onBack = { currentMode = PracticeMode.MENU })
        PracticeMode.MULTIPLE_CHOICE -> MultipleChoiceScreen(viewModel, onBack = { currentMode = PracticeMode.MENU })
        PracticeMode.MATCHING -> MatchingScreen(viewModel, onBack = { currentMode = PracticeMode.MENU })
    }
}

@Composable
fun PracticeMenu(onModeSelected: (PracticeMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2C3E50))
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
                    text = "Select an Activity",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        PracticeCard(title = "Flashcards", subtitle = "Review concepts with AI-generated flashcards") {
            onModeSelected(PracticeMode.FLASHCARDS)
        }
        
        PracticeCard(title = "Choose the correct answers", subtitle = "Test your knowledge with multiple choice") {
            onModeSelected(PracticeMode.MULTIPLE_CHOICE)
        }
        
        PracticeCard(title = "Match the following", subtitle = "Connect terms with their definitions") {
            onModeSelected(PracticeMode.MATCHING)
        }
    }
}

@Composable
fun PracticeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun FlashcardsScreen(viewModel: TutorViewModel, onBack: () -> Unit) {
    val flashcards by viewModel.flashcards.collectAsState()
    val currentIndex by viewModel.currentFlashcardIndex.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (flashcards.isEmpty() && uiState != TutorUiState.GeneratingQuiz) {
            viewModel.generateFlashcards()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Flashcards", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (uiState == TutorUiState.GeneratingQuiz) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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

            var flipped by remember { mutableStateOf(false) }
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
                    Surface(
                        shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                            Text(card.front, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                            Text(card.back, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Color.White, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray), modifier = Modifier.weight(1f).clickable { viewModel.generateFlashcards() }
                ) {
                    Row(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Regenerate", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Button(
                    onClick = {
                        if (currentIndex < flashcards.size - 1) viewModel.nextFlashcard() else viewModel.generateFlashcards()
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (currentIndex < flashcards.size - 1) "Next Card" else "New Set")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MultipleChoiceScreen(viewModel: TutorViewModel, onBack: () -> Unit) {
    val quiz by viewModel.quiz.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (quiz == null && uiState != TutorUiState.GeneratingQuiz) {
            viewModel.generateQuiz()
        }
    }

    // Reset state on new quiz
    LaunchedEffect(quiz) {
        selectedOption = null
        submitted = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Multiple Choice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState == TutorUiState.GeneratingQuiz) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (quiz != null) {
            Text(quiz!!.question, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(24.dp))
            
            quiz!!.options.forEachIndexed { index, option ->
                val isCorrect = index == quiz!!.correctIndex
                val isSelected = index == selectedOption
                
                val bgColor = if (submitted) {
                    if (isCorrect) Color(0xFFE8F5E9) else if (isSelected) Color(0xFFFFEBEE) else Color.White
                } else {
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !submitted) { selectedOption = index },
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Text(option, modifier = Modifier.padding(16.dp), fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (submitted) {
                Button(onClick = { viewModel.generateQuiz() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Next Question")
                }
            } else {
                Button(onClick = { submitted = true }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = selectedOption != null) {
                    Text("Submit Answer")
                }
            }
        }
    }
}

@Composable
fun MatchingScreen(viewModel: TutorViewModel, onBack: () -> Unit) {
    val matchingGame by viewModel.matchingGame.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var selectedTerm by remember { mutableStateOf<Int?>(null) }
    var selectedDef by remember { mutableStateOf<Int?>(null) }
    var matchedPairs by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(Unit) {
        if (matchingGame == null && uiState != TutorUiState.GeneratingQuiz) {
            viewModel.generateMatchingGame()
        }
    }

    LaunchedEffect(matchingGame) {
        selectedTerm = null
        selectedDef = null
        matchedPairs = setOf()
    }

    LaunchedEffect(selectedTerm, selectedDef) {
        if (selectedTerm != null && selectedDef != null) {
            if (selectedTerm == selectedDef) {
                matchedPairs = matchedPairs + selectedTerm!!
            }
            // Add a tiny delay to show the selection before clearing
            kotlinx.coroutines.delay(300)
            selectedTerm = null
            selectedDef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Match the Following", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState == TutorUiState.GeneratingQuiz) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (matchingGame != null) {
            // We need a stable random order for terms and definitions, but we can't recalculate on every recomposition.
            val shuffledTerms = remember(matchingGame) { matchingGame!!.pairs.indices.shuffled() }
            val shuffledDefs = remember(matchingGame) { matchingGame!!.pairs.indices.shuffled() }

            Row(modifier = Modifier.weight(1f)) {
                // Left Column (Terms)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shuffledTerms.size) { i ->
                        val index = shuffledTerms[i]
                        val isMatched = matchedPairs.contains(index)
                        val isSelected = selectedTerm == index

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable(enabled = !isMatched) { selectedTerm = index },
                            colors = CardDefaults.cardColors(containerColor = if (isMatched) Color(0xFFE8F5E9) else if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(matchingGame!!.pairs[index].term, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Right Column (Definitions)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shuffledDefs.size) { i ->
                        val index = shuffledDefs[i]
                        val isMatched = matchedPairs.contains(index)
                        val isSelected = selectedDef == index

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable(enabled = !isMatched) { selectedDef = index },
                            colors = CardDefaults.cardColors(containerColor = if (isMatched) Color(0xFFE8F5E9) else if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(matchingGame!!.pairs[index].definition, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
            
            if (matchedPairs.size == matchingGame!!.pairs.size) {
                Button(onClick = { viewModel.generateMatchingGame() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Generate New Game")
                }
            }
        }
    }
}
