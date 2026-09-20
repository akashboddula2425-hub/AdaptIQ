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

@Composable
fun PracticeScreen() {
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
            // In a real app, this would be an Image. Using a gradient/color box for now
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "ORBITAL MECHANICS LAB",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Energy Transfer Mode",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Options
        OptionCard(
            letter = "A",
            text = "Kinetic energy increases, total energy increases",
            isSelected = false,
            isCorrect = false
        )

        OptionCard(
            letter = "B",
            text = "Kinetic energy decreases, total energy increases",
            isSelected = true,
            isCorrect = true,
            feedback = "Spot on! Higher orbit implies lower orbital velocity (reducing kinetic energy), while potential energy becomes significantly less negative, lifting the total energy balance."
        )

        OptionCard(
            letter = "C",
            text = "Kinetic energy remains constant, total energy drops",
            isSelected = false,
            isCorrect = false
        )

        OptionCard(
            letter = "D",
            text = "Both kinetic and total energy decrease",
            isSelected = false,
            isCorrect = false
        )

        // Up Next Card
        Surface(
            color = Color(0xFFFFF3E0), // Light orange
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFE65100),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "UP NEXT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            text = "Draggable Force Vectors",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E2723)
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFE65100)
                )
            }
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
                onClick = { /* TODO */ }
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
                        text = "Clarify Vis-Viva",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                }
            }

            // Next Button
            Button(
                onClick = { /* TODO */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
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
    feedback: String? = null
) {
    val backgroundColor = if (isSelected && isCorrect) Color(0xFFE8F5E9) else Color.White
    val borderColor = if (isSelected && isCorrect) Color(0xFF4CAF50) else Color(0xFFE0E0E0)
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Letter Circle
                Surface(
                    color = if (isSelected && isCorrect) Color(0xFF4CAF50) else Color(0xFFF5F5F5),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected && isCorrect) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                    color = if (isSelected && isCorrect) Color(0xFF1B5E20) else Color.DarkGray,
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (isSelected && feedback != null) {
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
