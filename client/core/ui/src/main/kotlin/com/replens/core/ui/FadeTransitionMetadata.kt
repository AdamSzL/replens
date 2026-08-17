package com.replens.core.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

private const val FADE_TRANSITION_DURATION_MS = 250

private val FadeEnterTransition = fadeIn(tween(FADE_TRANSITION_DURATION_MS))
private val FadeExitTransition = fadeOut(tween(FADE_TRANSITION_DURATION_MS))

val FadeContentTransform = FadeEnterTransition togetherWith FadeExitTransition
