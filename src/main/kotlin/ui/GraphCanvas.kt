package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import model.GraphModel
import model.GraphNode

@Composable
fun GraphCanvas(
    graph: GraphModel,
    onNodeClick: (GraphNode) -> Unit,
) {
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0))
        // Handle Click & Drag Panning + Zooming
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                zoom = (zoom * gestureZoom).coerceIn(0.1f, 3f)
                offset += pan
            }
        }
        // Handle Trackpad Scrolling
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.first().scrollDelta
                        // Invert delta and scale for natural feel mapping
                        offset -= Offset(delta.x * 20f, delta.y * 20f)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }) {
        // Apply transformations
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                }) {
            // 1. Draw Edges (Canvas)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                graph.edges.forEach { edge ->
                    val source = graph.nodes.find { it.id == edge.fromId }
                    val target = graph.nodes.find { it.id == edge.toId }

                    if (source != null && target != null) {
                        val startX = (source.x + source.width / 2).toFloat()
                        val startY = (source.y + source.height).toFloat()
                        val endX = (target.x + target.width / 2).toFloat()
                        val endY = target.y.toFloat()

                        path.moveTo(startX, startY)
                        // Cubic Bezier for nice curves
                        path.cubicTo(
                            startX, startY + 50f, endX, endY - 50f, endX, endY
                        )
                    }
                }
                drawPath(path, Color.Black, style = Stroke(width = 2f))
            }

            // 2. Draw Nodes (Composables)
            graph.nodes.forEach { node ->
                Box(modifier = Modifier.offset { IntOffset(node.x.toInt(), node.y.toInt()) }
                    // Add node dragging capability
                    .pointerInput(node.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            node.x += dragAmount.x / zoom
                            node.y += dragAmount.y / zoom
                        }
                    }) {
                    when (node) {
                        is GraphNode.SnapshotNode -> SnapshotCard(node, onNodeClick)
                        is GraphNode.ManifestListNode -> ManifestListCard(node, onNodeClick)
                        is GraphNode.ManifestNode -> ManifestCard(node, onNodeClick)
                        is GraphNode.FileNode -> FileCard(node, onNodeClick)
                    }
                }
            }
        }
    }
}