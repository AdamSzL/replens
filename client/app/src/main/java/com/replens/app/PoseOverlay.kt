package com.replens.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame
import kotlin.math.max

private val CONNECTIONS = listOf(
    // torso
    LandmarkType.LEFT_SHOULDER to LandmarkType.RIGHT_SHOULDER,
    LandmarkType.LEFT_SHOULDER to LandmarkType.LEFT_HIP,
    LandmarkType.RIGHT_SHOULDER to LandmarkType.RIGHT_HIP,
    LandmarkType.LEFT_HIP to LandmarkType.RIGHT_HIP,
    // arms
    LandmarkType.LEFT_SHOULDER to LandmarkType.LEFT_ELBOW,
    LandmarkType.LEFT_ELBOW to LandmarkType.LEFT_WRIST,
    LandmarkType.RIGHT_SHOULDER to LandmarkType.RIGHT_ELBOW,
    LandmarkType.RIGHT_ELBOW to LandmarkType.RIGHT_WRIST,
    // legs
    LandmarkType.LEFT_HIP to LandmarkType.LEFT_KNEE,
    LandmarkType.LEFT_KNEE to LandmarkType.LEFT_ANKLE,
    LandmarkType.RIGHT_HIP to LandmarkType.RIGHT_KNEE,
    LandmarkType.RIGHT_KNEE to LandmarkType.RIGHT_ANKLE,
    // feet
    LandmarkType.LEFT_ANKLE to LandmarkType.LEFT_HEEL,
    LandmarkType.LEFT_HEEL to LandmarkType.LEFT_FOOT_INDEX,
    LandmarkType.RIGHT_ANKLE to LandmarkType.RIGHT_HEEL,
    LandmarkType.RIGHT_HEEL to LandmarkType.RIGHT_FOOT_INDEX,
)

private const val MIN_IN_FRAME_LIKELIHOOD = 0.5f

@Composable
fun PoseOverlay(frame: PoseFrame, mirrored: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Same FILL_CENTER mapping the viewfinder applies to the preview.
        val scale = max(size.width / frame.sourceWidth, size.height / frame.sourceHeight)
        val dx = (size.width - frame.sourceWidth * scale) / 2f
        val dy = (size.height - frame.sourceHeight * scale) / 2f

        fun toCanvas(landmark: Landmark): Offset {
            val x = landmark.x * scale + dx
            return Offset(
                if (mirrored) size.width - x else x,
                landmark.y * scale + dy,
            )
        }

        val visible = frame.pose.all
            .filter { it.inFrameLikelihood >= MIN_IN_FRAME_LIKELIHOOD }
            .associateBy { it.type }

        for ((startType, endType) in CONNECTIONS) {
            val start = visible[startType] ?: continue
            val end = visible[endType] ?: continue
            drawLine(
                color = Color.White,
                start = toCanvas(start),
                end = toCanvas(end),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        for (landmark in visible.values) {
            drawCircle(color = Color.Cyan, radius = 8f, center = toCanvas(landmark))
        }
    }
}
