package com.starnest.journalcanvaseditor.render

import android.graphics.RectF
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.EditorObject

internal class CanvasObjectSpatialIndex(
    private val nodeCapacity: Int = DEFAULT_NODE_CAPACITY,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH
) {
    private var root = QuadNode(
        bounds = RectF(),
        depth = 0,
        nodeCapacity = nodeCapacity,
        maxDepth = maxDepth
    )

    fun rebuild(document: EditorDocument, objects: List<EditorObject>) {
        val canvasBounds = RectF(0f, 0f, document.canvasWidth, document.canvasHeight)
        root = QuadNode(
            bounds = canvasBounds,
            depth = 0,
            nodeCapacity = nodeCapacity,
            maxDepth = maxDepth
        )

        for (obj in objects) {
            val bounds = CanvasGeometry.objectBounds(obj)
            if (canvasBounds.intersectsInclusive(bounds)) {
                root.insert(Entry(obj, bounds))
            }
        }
    }

    fun query(x: Float, y: Float): List<EditorObject> {
        if (!root.bounds.containsInclusive(x, y)) return emptyList()

        val result = mutableListOf<EditorObject>()
        root.query(x, y, result)
        return result
    }

    private data class Entry(
        val obj: EditorObject,
        val bounds: RectF
    )

    private class QuadNode(
        val bounds: RectF,
        private val depth: Int,
        private val nodeCapacity: Int,
        private val maxDepth: Int
    ) {
        private val entries = mutableListOf<Entry>()
        private var children: List<QuadNode>? = null

        fun insert(entry: Entry): Boolean {
            if (!bounds.intersectsInclusive(entry.bounds)) return false

            childContaining(entry.bounds)?.let { child ->
                return child.insert(entry)
            }

            entries.add(entry)
            if (entries.size > nodeCapacity && depth < maxDepth) {
                splitAndMoveContainedEntries()
            }
            return true
        }

        fun query(x: Float, y: Float, result: MutableList<EditorObject>) {
            if (!bounds.containsInclusive(x, y)) return

            for (entry in entries) {
                if (entry.bounds.containsInclusive(x, y)) {
                    result.add(entry.obj)
                }
            }

            val childNodes = children
            if (childNodes != null) {
                for (child in childNodes) {
                    if (child.bounds.containsInclusive(x, y)) {
                        child.query(x, y, result)
                    }
                }
            }
        }

        private fun splitAndMoveContainedEntries() {
            if (children == null) {
                children = createChildren()
            }

            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val child = childContaining(entry.bounds)
                if (child != null) {
                    child.insert(entry)
                    iterator.remove()
                }
            }
        }

        private fun childContaining(rect: RectF): QuadNode? {
            return children?.firstOrNull { child -> child.bounds.containsInclusive(rect) }
        }

        private fun createChildren(): List<QuadNode> {
            val midX = (bounds.left + bounds.right) / 2f
            val midY = (bounds.top + bounds.bottom) / 2f
            return listOf(
                RectF(bounds.left, bounds.top, midX, midY),
                RectF(midX, bounds.top, bounds.right, midY),
                RectF(bounds.left, midY, midX, bounds.bottom),
                RectF(midX, midY, bounds.right, bounds.bottom)
            ).map { childBounds ->
                QuadNode(
                    bounds = childBounds,
                    depth = depth + 1,
                    nodeCapacity = nodeCapacity,
                    maxDepth = maxDepth
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_NODE_CAPACITY = 8
        const val DEFAULT_MAX_DEPTH = 6
    }
}

private fun RectF.containsInclusive(x: Float, y: Float): Boolean {
    return x >= left && x <= right && y >= top && y <= bottom
}

private fun RectF.containsInclusive(other: RectF): Boolean {
    return other.left >= left &&
        other.right <= right &&
        other.top >= top &&
        other.bottom <= bottom
}

private fun RectF.intersectsInclusive(other: RectF): Boolean {
    return left <= other.right &&
        right >= other.left &&
        top <= other.bottom &&
        bottom >= other.top
}
