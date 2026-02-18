package com.example.aqi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*

data class ExerciseSuggestion(
    val name: String,
    val type: String,
    val emoji: String,
    val lottieFile: String? = null,
    val youtubeQuery: String = ""
)

@Composable
fun ExerciseScreen(aqi: Int) {
    val suggestions = getExerciseSuggestions(aqi)
    val indoorExercises = suggestions.filter { it.type == "Indoor" }
    val outdoorExercises = suggestions.filter { it.type == "Outdoor" }
    val commuteAdvice = getCommuteAdvice(aqi)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text("Health & Work Advice", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
        Text("Personalized for AQI: $aqi", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
        
        Spacer(modifier = Modifier.height(24.dp))

        AdviceCategory("IF YOU HAVE WORK TO DO...", listOf(commuteAdvice))
        
        Spacer(modifier = Modifier.height(24.dp))

        if (outdoorExercises.isNotEmpty()) {
            ExerciseCategory("GO OUTSIDE!", outdoorExercises) { exercise ->
                openYouTube(context, exercise.youtubeQuery)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        ExerciseCategory("STAY INDOORS", indoorExercises) { exercise ->
            openYouTube(context, exercise.youtubeQuery)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

private fun openYouTube(context: Context, query: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
    context.startActivity(intent)
}

@Composable
fun AdviceCategory(title: String, advice: List<String>) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color.Cyan.copy(alpha = 0.8f), letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))
        advice.forEach {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.1f)).padding(16.dp)) {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ExerciseCategory(title: String, exercises: List<ExerciseSuggestion>, onSelect: (ExerciseSuggestion) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(16.dp))
        exercises.forEach {
            ExerciseSuggestionCard(exercise = it, onClick = { onSelect(it) })
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ExerciseSuggestionCard(exercise: ExerciseSuggestion, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(modifier = Modifier.matchParentSize().blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.25f)))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f))))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/${exercise.lottieFile}"))
                if (composition != null) {
                    LottieAnimation(composition = composition, iterations = LottieConstants.IterateForever, modifier = Modifier.size(56.dp))
                } else {
                    Text(exercise.emoji, fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(text = exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Watch on YouTube", style = MaterialTheme.typography.labelSmall, color = Color.Cyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun getCommuteAdvice(aqi: Int): String {
    return when {
        aqi <= 50 -> "Excellent air. Enjoy your commute! Ideal for walking or cycling to work."
        aqi <= 100 -> "Moderate air. Commute normally, but consider keeping car windows closed in heavy traffic."
        aqi <= 150 -> "Unhealthy for sensitive groups. If you commute by bike or foot, wear a simple mask. Keep AC on recirculate."
        aqi <= 200 -> "Polluted. Use N95 mask if walking outside. Limit time on the road. Close all windows."
        else -> "Dangerous! Avoid travel if possible. If you must go to work, use high-grade respiratory protection and stay in purified environments."
    }
}

fun getExerciseSuggestions(aqi: Int): List<ExerciseSuggestion> {
    val indoor = listOf(
        ExerciseSuggestion("Yoga", "Indoor", "🧘", "yoga.json", "yoga+for+breathing+and+detox"),
        ExerciseSuggestion("Home Workout", "Indoor", "💪", "workout.json", "full+body+indoor+workout+no+equipment"),
        ExerciseSuggestion("Stretching", "Indoor", "🤸", "stretching.json", "full+body+stretching+routine"),
        ExerciseSuggestion("Treadmill", "Indoor", "🏃", "treadmill.json", "treadmill+workout+for+beginners")
    )

    val outdoor = when {
        aqi <= 50 -> listOf(
            ExerciseSuggestion("Running", "Outdoor", "🏃‍♀️", "running.json", "running+tips+for+beginners"),
            ExerciseSuggestion("Cycling", "Outdoor", "🚴", "cycling.json", "cycling+training+guide"),
            ExerciseSuggestion("Hiking", "Outdoor", "🥾", "hiking.json", "scenic+hiking+vlog")
        )
        aqi <= 100 -> listOf(
            ExerciseSuggestion("Briskwalking", "Outdoor", "🚶‍♀️", "walking.json", "brisk+walking+benefits"),
            ExerciseSuggestion("Light Jogging", "Outdoor", "🏃", "jogging.json", "light+jogging+warmup")
        )
        else -> emptyList()
    }

    return indoor + outdoor
}
