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
        val validCodes: List<String> = emptyList(),
        val acceptAny: Boolean = false
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
        val leverCount: Int = 4,
        val timeoutSeconds: Int? = null
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
        val sentence: String = sentencePool.random()
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
        val sentencePool = listOf(
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

data class PausePointVariant(
    val label: String = "",
    val text: String = "",
    val seconds: Int = 0,
    val rounds: Int = 0,
    val steps: Int = 0,
    val size: Int = 0,
    val levers: Int = 0,
    val maxOperand: Int = 0,
    val target: Int = 0
)

data class PausePointConfig(
    val waitingVariants: List<PausePointVariant> = PausePointDefaults.waitingVariants,
    val breathingVariants: List<PausePointVariant> = PausePointDefaults.breathingVariants,
    val walkVariants: List<PausePointVariant> = PausePointDefaults.walkVariants,
    val numberSlideVariants: List<PausePointVariant> = PausePointDefaults.numberSlideVariants,
    val switchVariants: List<PausePointVariant> = PausePointDefaults.switchVariants,
    val mathVariants: List<PausePointVariant> = PausePointDefaults.mathVariants,
    val countingVariants: List<PausePointVariant> = PausePointDefaults.countingVariants,
    val typingVariants: List<PausePointVariant> = PausePointDefaults.typingVariants
) {
    fun variantsFor(type: PausePointTaskType): List<PausePointVariant> = when (type) {
        PausePointTaskType.WAITING -> waitingVariants
        PausePointTaskType.BREATHING -> breathingVariants
        PausePointTaskType.WALK -> walkVariants
        PausePointTaskType.NUMBER_SLIDE -> numberSlideVariants
        PausePointTaskType.SWITCH -> switchVariants
        PausePointTaskType.MATH -> mathVariants
        PausePointTaskType.COUNTING -> countingVariants
        PausePointTaskType.TYPING -> typingVariants
        PausePointTaskType.QR_SCAN -> emptyList()
        PausePointTaskType.CHOOSE_APP -> emptyList()
    }
}

object PausePointDefaults {
    val waitingVariants = listOf(PausePointVariant(seconds = 15))
    val breathingVariants = listOf(PausePointVariant(rounds = 3))
    val walkVariants = listOf(PausePointVariant(steps = 10))
    val numberSlideVariants = listOf(PausePointVariant(size = 3))
    val switchVariants = listOf(PausePointVariant(levers = 4))
    val mathVariants = listOf(PausePointVariant(maxOperand = 20))
    val countingVariants = listOf(PausePointVariant(target = 15))
    val typingVariants = PausePointTask.sentencePool.map { PausePointVariant(text = it) }

    fun variantsFor(type: PausePointTaskType): List<PausePointVariant> = when (type) {
        PausePointTaskType.WAITING -> waitingVariants
        PausePointTaskType.BREATHING -> breathingVariants
        PausePointTaskType.WALK -> walkVariants
        PausePointTaskType.NUMBER_SLIDE -> numberSlideVariants
        PausePointTaskType.SWITCH -> switchVariants
        PausePointTaskType.MATH -> mathVariants
        PausePointTaskType.COUNTING -> countingVariants
        PausePointTaskType.TYPING -> typingVariants
        PausePointTaskType.QR_SCAN -> emptyList()
        PausePointTaskType.CHOOSE_APP -> emptyList()
    }
}

object PausePointEngine {

    private fun pickVariant(variants: List<PausePointVariant>, fallback: List<PausePointVariant>): PausePointVariant {
        val pool = variants.ifEmpty { fallback }
        return if (pool.isNotEmpty()) pool.random() else PausePointVariant()
    }

    fun generateTask(
        enabledTypes: Set<PausePointTaskType> = PausePointTaskType.entries.toSet(),
        goalPackageNames: Set<String> = emptySet(),
        goalAppNames: Map<String, String> = emptyMap(),
        qrCodes: List<String> = emptyList(),
        config: PausePointConfig = PausePointConfig(),
        cameraGranted: Boolean = true
    ): PausePointTask {
        // QR tasks are only completable with camera access: without it the user
        // would be stuck with Close as the only way out.
        val filteredTypes = enabledTypes
            .filter { it != PausePointTaskType.QR_SCAN || (qrCodes.isNotEmpty() && cameraGranted) }
            .toList()
        if (filteredTypes.isEmpty()) return PausePointTask.Waiting()

        val selectedType = filteredTypes.random()

        return when (selectedType) {
            PausePointTaskType.WAITING -> PausePointTask.Waiting(
                durationSeconds = pickVariant(config.waitingVariants, PausePointDefaults.waitingVariants).seconds.coerceAtLeast(1)
            )
            PausePointTaskType.BREATHING -> PausePointTask.Breathing(
                rounds = pickVariant(config.breathingVariants, PausePointDefaults.breathingVariants).rounds.coerceAtLeast(1)
            )
            PausePointTaskType.WALK -> PausePointTask.Walk(
                steps = pickVariant(config.walkVariants, PausePointDefaults.walkVariants).steps.coerceAtLeast(1)
            )
            PausePointTaskType.QR_SCAN -> PausePointTask.QrScan(
                code = if (qrCodes.isNotEmpty()) qrCodes.random() else "PAUSE-${Random.nextInt(100000, 999999)}",
                validCodes = qrCodes
            )
            PausePointTaskType.NUMBER_SLIDE -> PausePointTask.NumberSlide(
                size = pickVariant(config.numberSlideVariants, PausePointDefaults.numberSlideVariants).size.coerceAtLeast(3)
            )
            PausePointTaskType.SWITCH -> {
                val v = pickVariant(config.switchVariants, PausePointDefaults.switchVariants)
                PausePointTask.Switch(
                    leverCount = v.levers.coerceAtLeast(2),
                    timeoutSeconds = v.seconds.takeIf { it > 0 }
                )
            }
            PausePointTaskType.MATH -> PausePointTask.Math(
                maxOperand = pickVariant(config.mathVariants, PausePointDefaults.mathVariants).maxOperand.coerceAtLeast(1)
            )
            PausePointTaskType.COUNTING -> {
                val v = pickVariant(config.countingVariants, PausePointDefaults.countingVariants)
                PausePointTask.Counting(
                    targetNumber = v.target.coerceAtLeast(1),
                    label = v.label
                )
            }
            PausePointTaskType.TYPING -> {
                val text = pickVariant(config.typingVariants, PausePointDefaults.typingVariants).text
                PausePointTask.Typing(
                    sentence = text.ifBlank { PausePointTask.sentencePool.random() }
                )
            }
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
