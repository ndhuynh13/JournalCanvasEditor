package com.starnest.journalcanvaseditor.domain

import kotlin.math.abs

data class SnapResult(
    val dx: Float,
    val dy: Float,
    val guides: List<SnapGuide>
)

class SnapEngine(
    private val snapThreshold: Float = 2f,
    private val releaseThreshold: Float = 4f
) {
    private var snappedVerticalTarget: Float? = null
    private var snappedHorizontalTarget: Float? = null

    fun snap(document: EditorDocument, moving: EditorObject): SnapResult {
        val verticalTargets = mutableListOf(
            0f,
            document.canvasWidth / 2f,
            document.canvasWidth
        )
        val horizontalTargets = mutableListOf(
            0f,
            document.canvasHeight / 2f,
            document.canvasHeight
        )

        document.objects.asSequence()
            .filter { it.id != moving.id && it.visible }
            .forEach {
                verticalTargets += listOf(it.left, it.centerX, it.right)
                horizontalTargets += listOf(it.top, it.centerY, it.bottom)
            }

        val movingVerticals = listOf(moving.left, moving.centerX, moving.right)
        val movingHorizontals = listOf(moving.top, moving.centerY, moving.bottom)

        var bestDx = 0f
        var bestVDistance = Float.MAX_VALUE
        var verticalGuide: SnapGuide? = null

        val lockedV = snappedVerticalTarget
        if (lockedV != null) {
            val closest = movingVerticals.minOf { abs(it - lockedV) }
            if (closest <= releaseThreshold) {
                val source = movingVerticals.minByOrNull { abs(it - lockedV) }!!
                bestDx = lockedV - source
                verticalGuide = SnapGuide(GuideOrientation.Vertical, lockedV, 0f, document.canvasHeight)
            } else {
                snappedVerticalTarget = null
            }
        }

        if (snappedVerticalTarget == null) {
            for (source in movingVerticals) {
                for (target in verticalTargets) {
                    val distance = target - source
                    if (abs(distance) < abs(bestVDistance) && abs(distance) <= snapThreshold) {
                        bestVDistance = distance
                        bestDx = distance
                        verticalGuide = SnapGuide(GuideOrientation.Vertical, target, 0f, document.canvasHeight)
                        snappedVerticalTarget = target
                    }
                }
            }
        }

        var bestDy = 0f
        var bestHDistance = Float.MAX_VALUE
        var horizontalGuide: SnapGuide? = null

        val lockedH = snappedHorizontalTarget
        if (lockedH != null) {
            val closest = movingHorizontals.minOf { abs(it - lockedH) }
            if (closest <= releaseThreshold) {
                val source = movingHorizontals.minByOrNull { abs(it - lockedH) }!!
                bestDy = lockedH - source
                horizontalGuide = SnapGuide(GuideOrientation.Horizontal, lockedH, 0f, document.canvasWidth)
            } else {
                snappedHorizontalTarget = null
            }
        }

        if (snappedHorizontalTarget == null) {
            for (source in movingHorizontals) {
                for (target in horizontalTargets) {
                    val distance = target - source
                    if (abs(distance) < abs(bestHDistance) && abs(distance) <= snapThreshold) {
                        bestHDistance = distance
                        bestDy = distance
                        horizontalGuide = SnapGuide(GuideOrientation.Horizontal, lockedH ?: target, 0f, document.canvasWidth)
                        snappedHorizontalTarget = target
                    }
                }
            }
        }

        return SnapResult(
            dx = bestDx,
            dy = bestDy,
            guides = listOfNotNull(verticalGuide, horizontalGuide)
        )
    }

    fun reset() {
        snappedVerticalTarget = null
        snappedHorizontalTarget = null
    }
}
