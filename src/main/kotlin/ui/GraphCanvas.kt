package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import kotlinx.coroutines.launch
import model.GraphModel
import model.GraphNode

@Composable
fun GraphCanvas(
    graph: GraphModel,
    selectedNode: GraphNode?, // 1. Pass selected node to canvas
    onNodeClick: (GraphNode) -> Unit,
) {
    var zoom by remember { mutableStateOf(1f) }
    // 2. Use Animatable for smooth panning
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    // 3. Use BoxWithConstraints to know viewport dimensions
    BoxWithConstraints(
        modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFE0E0E0))
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                zoom = (zoom * gestureZoom).coerceIn(0.1f, 3f)
                coroutineScope.launch {
                    offsetAnim.snapTo(offsetAnim.value + pan)
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.first().scrollDelta
                        coroutineScope.launch {
                            offsetAnim.snapTo(
                                offsetAnim.value - Offset(
                                    delta.x * 20f, delta.y * 20f
                                )
                            )
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        // 4. Calculate offset to center the node when selection changes
        LaunchedEffect(selectedNode) {
            if (selectedNode != null) {
                val nodeCenterX = selectedNode.x.toFloat() + (selectedNode.width.toFloat() / 2f)
                val nodeCenterY = selectedNode.y.toFloat() + (selectedNode.height.toFloat() / 2f)

                // GraphicsLayer default transformOrigin is Center.
                // Formula resolves scaled distance from center.
                val targetX = (viewportWidth / 2f - nodeCenterX) * zoom
                val targetY = (viewportHeight / 2f - nodeCenterY) * zoom

                offsetAnim.animateTo(Offset(targetX, targetY))
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offsetAnim.value.x
                translationY = offsetAnim.value.y
            }) {
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
                        val midY = startY + (endY - startY) / 2f

                        path.moveTo(startX, startY)
                        path.lineTo(startX, midY)
                        path.lineTo(endX, midY)
                        path.lineTo(endX, endY)
                    }
                }
                drawPath(path, Color.Black, style = Stroke(width = 2f))
            }

            graph.nodes.forEach { node ->
                Box(
                    modifier = Modifier
                    .offset { IntOffset(node.x.toInt(), node.y.toInt()) }
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
                        is GraphNode.RowNode -> RowCard(node, onNodeClick) // NEW
                    }
                }
            }
        }
    }
}