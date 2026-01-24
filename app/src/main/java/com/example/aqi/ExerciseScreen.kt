package com.example.aqi

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*

data class ExerciseSuggestion(
    val name: String,
    val type: String,
    val emoji: String,
    val lottieFile: String? = null
)

@Composable
fun ExerciseScreen(aqi: Int) {
    val suggestions = getExerciseSuggestions(aqi)
    val indoorExercises = suggestions.filter { it.type == "Indoor" }
    val outdoorExercises = suggestions.filter { it.type == "Outdoor" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            "Exercise Recommendations",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            "Current AQI: $aqi",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (outdoorExercises.isNotEmpty()) {
            ExerciseCategory("GO OUTSIDE!", outdoorExercises)
            Spacer(modifier = Modifier.height(24.dp))
        }

        ExerciseCategory("STAY INDOORS", indoorExercises)
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ExerciseCategory(title: String, exercises: List<ExerciseSuggestion>) {
    Column {
        Text(
            text = title, 
            style = MaterialTheme.typography.labelMedium, 
            fontWeight = FontWeight.ExtraBold, 
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        exercises.forEach {
            ExerciseSuggestionCard(exercise = it)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ExerciseSuggestionCard(exercise: ExerciseSuggestion) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 1. Frosted Glass Base
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.25f))
        )

        // 2. Content Layer
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f))))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/${exercise.lottieFile}"))
                
                if (composition != null) {
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Text(exercise.emoji, fontSize = 32.sp)
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Text(
                    text = exercise.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

fun getExerciseSuggestions(aqi: Int): List<ExerciseSuggestion> {
    val indoor = listOf(
        ExerciseSuggestion("Yoga", "Indoor", "🧘", "yoga.json"),
        ExerciseSuggestion("Home Workout", "Indoor", "💪", "workout.json"),
        ExerciseSuggestion("Stretching", "Indoor", "🤸", "stretching.json"),
        ExerciseSuggestion("Treadmill", "Indoor", "🏃", "treadmill.json")
    )

    val outdoor = when {
        aqi <= 50 -> listOf(
            ExerciseSuggestion("Running", "Outdoor", "🏃‍♀️", "running.json"),
            ExerciseSuggestion("Cycling", "Outdoor", "🚴", "cycling.json"),
            ExerciseSuggestion("Hiking", "Outdoor", "🥾", "hiking.json")
        )
        aqi <= 100 -> listOf(
            ExerciseSuggestion("Briskwalking", "Outdoor", "🚶‍♀️", "walking.json"),
            ExerciseSuggestion("Light Jogging", "Outdoor", "🏃", "jogging.json")
        )
        else -> emptyList()
    }

    return indoor + outdoor
}
