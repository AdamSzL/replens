package com.replens.core.pose

/**
 * What the user can choose right now.
 *
 * @param facings a property of the device, known as soon as the provider resolves
 * @param zoomRange a property of the bound lens, so it is null until one is bound
 *   and changes on every flip
 */
data class CameraOptions(
    val facings: Set<CameraFacing>,
    val zoomRange: ZoomRange? = null,
)
