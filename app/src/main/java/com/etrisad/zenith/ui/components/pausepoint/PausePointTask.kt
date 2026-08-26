package com.etrisad.zenith.ui.components.pausepoint

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Nature
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random

enum class PausePointTaskType(val displayName: String, val description: String, val icon: ImageVector) {
    WAITING("Waiting", "Wait for a set duration before continuing", Icons.Outlined.AccessTime),
    BREATHING("Breathing", "Follow a breathing exercise", Icons.Outlined.Nature),
    WALK("Walk", "Walk a certain number of steps", Icons.AutoMirrored.Outlined.DirectionsWalk),
    QR_SCAN("QR Scan", "Scan a QR code", Icons.Outlined.QrCodeScanner),
    NUMBER_SLIDE("Number Slide", "Slide the tiles to solve the puzzle", Icons.Outlined.GridView),
    SWITCH("Switch Puzzle", "Match the switch sequence", Icons.Outlined.ToggleOn),
    COUNTING("Counting", "Complete a counting exercise", Icons.Outlined.FitnessCenter),
    TYPING("Typing", "Type a specific text correctly", Icons.Outlined.Keyboard),
    MATH("Math", "Solve a quick math problem", Icons.Outlined.Calculate),
    CHOOSE_APP("Choose App", "Open a suggested goal app", Icons.Outlined.TouchApp)
}

sealed class PausePointTask {
    abstract val type: PausePointTaskType
    abstract val instruction: String

    data class Waiting(
        val durationSeconds: Int = 15
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.WAITING
        override val instruction get() = "Wait for $durationSeconds seconds before proceeding"
    }

    data class Breathing(
        val rounds: Int = 3
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.BREATHING
        override val instruction get() = "Take $rounds deep breaths"
    }

    data class Walk(
        val steps: Int = 10
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.WALK
        override val instruction get() = "Take $steps steps away and back"
    }

    data class QrScan(
        val code: String = "PAUSE-${Random.nextInt(100000, 999999)}",
        val validCodes: List<String> = emptyList()
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.QR_SCAN
        override val instruction get() =
            if (validCodes.isNotEmpty()) "Scan a QR code that matches one of your saved codes"
            else "Scan the QR code to proceed"
    }

    data class NumberSlide(
        val size: Int = 3
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.NUMBER_SLIDE
        override val instruction get() = "Slide the tiles to solve the puzzle"
    }

    data class Switch(
        val leverCount: Int = 4
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.SWITCH
        override val instruction get() = "Match the switch sequence to continue"
    }

    data class Math(
        val maxOperand: Int = 20
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.MATH
        override val instruction get() = "Solve the math problem to continue"
    }

    data class Counting(
        val targetNumber: Int = Random.nextInt(5, 21),
        val label: String = ""
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.COUNTING
        override val instruction get() = if (label.isNotEmpty()) "Do $targetNumber $label" else "Count to $targetNumber"
    }

    data class Typing(
        val sentence: String = SENTENCES.random()
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.TYPING
        override val instruction get() = "Type the following sentence correctly"
    }

    data class ChooseApp(
        val suggestedPackage: String = "",
        val suggestedAppName: String = "a productive app"
    ) : PausePointTask() {
        override val type get() = PausePointTaskType.CHOOSE_APP
        override val instruction get() = "Open $suggestedAppName instead"
    }

    companion object {
        private val SENTENCES = listOf(
            "Stay focused and mindful.",
            "Small steps lead to big changes.",
            "Every moment is a fresh beginning.",
            "Progress not perfection.",
            "Be present in this moment.",
            "You are capable of amazing things.",
            "Focus on what matters most.",
            "One task at a time."
        )
    }
}

object PausePointEngine {

    fun generateTask(
        enabledTypes: Set<PausePointTaskType> = PausePointTaskType.entries.toSet(),
        goalPackageNames: Set<String> = emptySet(),
        goalAppNames: Map<String, String> = emptyMap(),
        qrCodes: List<String> = emptyList()
    ): PausePointTask {
        val filteredTypes = enabledTypes
            .filter { it != PausePointTaskType.QR_SCAN || qrCodes.isNotEmpty() }
            .toList()
        if (filteredTypes.isEmpty()) return PausePointTask.Waiting()

        val selectedType = filteredTypes.random()

        return when (selectedType) {
            PausePointTaskType.WAITING -> PausePointTask.Waiting(
                durationSeconds = listOf(10, 15, 20, 30).random()
            )
            PausePointTaskType.BREATHING -> PausePointTask.Breathing(
                rounds = listOf(3, 5).random()
            )
            PausePointTaskType.WALK -> PausePointTask.Walk(
                steps = listOf(5, 10, 15, 20).random()
            )
            PausePointTaskType.QR_SCAN -> PausePointTask.QrScan(
                code = if (qrCodes.isNotEmpty()) qrCodes.random() else "PAUSE-${Random.nextInt(100000, 999999)}",
                validCodes = qrCodes
            )
            PausePointTaskType.NUMBER_SLIDE -> PausePointTask.NumberSlide(
                size = 3
            )
            PausePointTaskType.SWITCH -> PausePointTask.Switch(
                leverCount = listOf(3, 4).random()
            )
            PausePointTaskType.MATH -> PausePointTask.Math(
                maxOperand = listOf(10, 20).random()
            )
            PausePointTaskType.COUNTING -> {
                val labels = listOf("push-ups", "jumping jacks", "squats", "sit-ups", "arm stretches")
                val useLabel = Random.nextBoolean()
                if (useLabel) {
                    PausePointTask.Counting(
                        targetNumber = listOf(5, 10, 15).random(),
                        label = labels.random()
                    )
                } else {
                    PausePointTask.Counting(
                        targetNumber = Random.nextInt(10, 31)
                    )
                }
            }
            PausePointTaskType.TYPING -> PausePointTask.Typing()
            PausePointTaskType.CHOOSE_APP -> {
                if (goalPackageNames.isNotEmpty()) {
                    val randomGoal = goalPackageNames.random()
                    PausePointTask.ChooseApp(
                        suggestedPackage = randomGoal,
                        suggestedAppName = goalAppNames[randomGoal] ?: "a productive app"
                    )
                } else {
                    PausePointTask.Counting(targetNumber = 10, label = "deep breaths")
                }
            }
        }
    }
}
