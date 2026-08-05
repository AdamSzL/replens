package com.replens.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

private val CONNECTIONS = listOf(
    // torso
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    // arms
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    // legs
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
    // feet
    PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
    PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
    PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
    PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX,
)

private const val MIN_IN_FRAME_LIKELIHOOD = 0.5f

@Composable
fun PoseOverlay(frame: PoseFrame, mirrored: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Same FILL_CENTER mapping the viewfinder applies to the preview.
        val scale = max(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val dx = (size.width - frame.imageWidth * scale) / 2f
        val dy = (size.height - frame.imageHeight * scale) / 2f

        fun toCanvas(landmark: PoseLandmark): Offset {
            val x = landmark.position.x * scale + dx
            return Offset(
                if (mirrored) size.width - x else x,
                landmark.position.y * scale + dy,
            )
        }

        val landmarks = frame.pose.allPoseLandmarks
            .filter { it.inFrameLikelihood >= MIN_IN_FRAME_LIKELIHOOD }
            .associateBy { it.landmarkType }

        for ((startType, endType) in CONNECTIONS) {
            val start = landmarks[startType] ?: continue
            val end = landmarks[endType] ?: continue
            drawLine(
                color = Color.White,
                start = toCanvas(start),
                end = toCanvas(end),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        for (landmark in landmarks.values) {
            drawCircle(color = Color.Cyan, radius = 8f, center = toCanvas(landmark))
        }
    }
}
