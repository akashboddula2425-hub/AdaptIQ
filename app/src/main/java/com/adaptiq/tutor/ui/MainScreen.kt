package com.adaptiq.tutor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptiq.tutor.viewmodel.TutorUiState
import com.adaptiq.tutor.viewmodel.TutorViewModel

enum class NavigationTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ONBOARD("Onboard", Icons.Default.RocketLaunch),
    TUTOR("AI Tutor", Icons.Default.Face),
    PRACTICE("Practice", Icons.Default.EditNote),
    PODS("Pods", Icons.Default.People)
}

@Composable
fun MainScreen(viewModel: TutorViewModel) {
    var selectedTab by remember { mutableStateOf(NavigationTab.TUTOR) }
    val uiState by viewModel.uiState.collectAsState()

    // Top Bar (Matches design "AdaptIQ Dynamic AI Tutor" + Coin/Score badges)
    Scaffold(
        topBar = {
            if (uiState !is TutorUiState.ModelNotFound) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "AdaptIQ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = selectedTab.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Settings / Change Model
                        IconButton(onClick = { viewModel.resetModelSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Change AI Model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState !is TutorUiState.ModelNotFound) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationTab.values().forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 10.sp) },
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.TUTOR -> TutorScreen(viewModel = viewModel, isEmbedded = true)
                NavigationTab.PRACTICE -> PracticeScreen()
                else -> PlaceholderScreen(tab = selectedTab)
            }
        }
    }
}

@Composable
fun PlaceholderScreen(tab: NavigationTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("WIP: ${tab.title} Screen", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
