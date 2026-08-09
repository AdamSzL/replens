package com.replens.feature.workout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.theme.RepLensTheme
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

/** Only these are drawn: a landmark with no bone attached reads as noise. */
private val CONNECTED_TYPES = CONNECTIONS.flatMapTo(mutableSetOf()) { (a, b) -> listOf(a, b) }

private const val MIN_IN_FRAME_LIKELIHOOD = 0.5f

// dp, because a DrawScope works in raw pixels.
private val BoneWidth = 3.dp
private val JointRadius = 5.dp
private val OutlineWidth = 2.dp

/**
 * @param frame invoked inside the draw scope, so a new frame invalidates only the
 *   draw phase. **Do not hoist the read out of the lambda** — that puts it back
 *   into composition and recomposes the screen 30 times a second.
 */
@Composable
internal fun PoseOverlay(
    frame: () -> PoseFrame?,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    val boneColor = RepLensTheme.colors.onOverlay
    val jointColor = RepLensTheme.colors.overlayAccent
    val outlineColor = RepLensTheme.colors.overlayOutline
    Canvas(modifier = modifier) {
        val currentFrame = frame() ?: return@Canvas
        // Same FILL_CENTER mapping the viewfinder applies to the preview.
        val scale = max(size.width / currentFrame.sourceWidth, size.height / currentFrame.sourceHeight)
        val dx = (size.width - currentFrame.sourceWidth * scale) / 2f
        val dy = (size.height - currentFrame.sourceHeight * scale) / 2f

        fun toCanvas(landmark: Landmark): Offset {
            val x = landmark.x * scale + dx
            return Offset(
                if (mirrored) size.width - x else x,
                landmark.y * scale + dy,
            )
        }

        val visible = currentFrame.pose.all
            .filter { it.inFrameLikelihood >= MIN_IN_FRAME_LIKELIHOOD }
            .associateBy { it.type }

        val bones = CONNECTIONS.mapNotNull { (startType, endType) ->
            val start = visible[startType] ?: return@mapNotNull null
            val end = visible[endType] ?: return@mapNotNull null
            toCanvas(start) to toCanvas(end)
        }
        val joints = visible.filterKeys { it in CONNECTED_TYPES }.values.map(::toCanvas)

        val boneWidth = BoneWidth.toPx()
        val jointRadius = JointRadius.toPx()
        val outline = OutlineWidth.toPx()

        // All outlines, then all fills: interleaved, one bone's outline would cut
        // into the bone before it.
        for ((start, end) in bones) {
            drawLine(outlineColor, start, end, boneWidth + outline * 2f, StrokeCap.Round)
        }
        for ((start, end) in bones) {
            drawLine(boneColor, start, end, boneWidth, StrokeCap.Round)
        }
        for (joint in joints) {
            drawCircle(outlineColor, jointRadius + outline, joint)
        }
        for (joint in joints) {
            drawCircle(jointColor, jointRadius, joint)
        }
    }
}
