package com.example.aqi

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*

data class ExerciseSuggestion(
    val name: String,
    val type: String,
    val emoji: String,
    val lottieFile: String? = null // Optional Lottie animation file
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
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Based on the current AQI of $aqi",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (outdoorExercises.isNotEmpty()) {
            ExerciseCategory("Go Outside!", outdoorExercises)
            Spacer(modifier = Modifier.height(24.dp))
        }

        ExerciseCategory("Stay Indoors", indoorExercises)
    }
}

@Composable
fun ExerciseCategory(title: String, exercises: List<ExerciseSuggestion>) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        exercises.forEach {
            ExerciseSuggestionCard(exercise = it)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ExerciseSuggestionCard(exercise: ExerciseSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
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
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Text(exercise.emoji, style = MaterialTheme.typography.headlineMedium)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge, color = Color.White)
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
