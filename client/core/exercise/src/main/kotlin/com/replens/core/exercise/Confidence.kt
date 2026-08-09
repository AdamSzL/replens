package com.replens.core.exercise

/**
 * How much confidence a landmark needs before a rule may read its position.
 *
 * Shared vocabulary rather than a per-exercise constant: occluded and
 * out-of-frame joints are still reported, with low values, so *every* rule that
 * trusts a coordinate needs this gate — and a second copy of the number would
 * drift from the first.
 *
 * A rule is free to demand more where it can justify it; the argument stays a
 * parameter everywhere this is used as a default.
 */
const val DEFAULT_MIN_LIKELIHOOD = 0.5f
