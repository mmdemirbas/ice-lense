package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.GraphModel
import model.GraphNode
import kotlin.math.min

@Composable
fun GraphCanvas(
    graph: GraphModel,
    selectedNode: GraphNode?,
    onNodeClick: (GraphNode) -> Unit,
) {
    var zoom by remember { mutableStateOf(1f) }
    val offsetAnim = remember { Animatable(Offset(100f, 100f), Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    var hoveredNodeId by remember { mutableStateOf<String?>(null) }

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
                                offsetAnim.value - Offset(delta.x * 20f, delta.y * 20f)
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
                val currentX = offsetAnim.value.x
                val currentY = offsetAnim.value.y

                val nodeLeft = selectedNode.x.toFloat() * zoom + currentX
                val nodeRight =
                    (selectedNode.x.toFloat() + selectedNode.width.toFloat()) * zoom + currentX
                val nodeTop = selectedNode.y.toFloat() * zoom + currentY
                val nodeBottom =
                    (selectedNode.y.toFloat() + selectedNode.height.toFloat()) * zoom + currentY

                val margin = 50f // Keep a 50px padding from the edge

                val isVisible =
                    nodeLeft >= margin && nodeRight <= (viewportWidth - margin) && nodeTop >= margin && nodeBottom <= (viewportHeight - margin)

                if (!isVisible) {
                    val nodeCenterX = selectedNode.x.toFloat() + (selectedNode.width.toFloat() / 2f)
                    val nodeCenterY =
                        selectedNode.y.toFloat() + (selectedNode.height.toFloat() / 2f)

                    val targetX = (viewportWidth / 2f - nodeCenterX) * zoom
                    val targetY = (viewportHeight / 2f - nodeCenterY) * zoom

                    offsetAnim.animateTo(Offset(targetX, targetY))
                }
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
                val activeNodeId = hoveredNodeId ?: selectedNode?.id

                val normalEdges = mutableListOf<Path>()
                val highlightedEdges = mutableListOf<Path>()

                graph.edges.forEach { edge ->
                    val source = graph.nodes.find { it.id == edge.fromId }
                    val target = graph.nodes.find { it.id == edge.toId }

                    if (source != null && target != null) {
                        val path = Path()
                        if (edge.isSibling) {
                            val startX = (source.x + source.width / 2).toFloat()
                            val startY = (source.y + source.height).toFloat()
                            val endX = (target.x + target.width / 2).toFloat()
                            val endY = target.y.toFloat()
                            val midY = startY + (endY - startY) / 2f

                            path.moveTo(startX, startY)
                            path.lineTo(startX, midY)
                            path.lineTo(endX, midY)
                            path.lineTo(endX, endY)
                        } else {
                            val startX = (source.x + source.width).toFloat()
                            val startY = (source.y + source.height / 2).toFloat()
                            val endX = target.x.toFloat()
                            val endY = (target.y + target.height / 2).toFloat()
                            val midX = startX + (endX - startX) / 2f

                            path.moveTo(startX, startY)
                            path.lineTo(midX, startY)
                            path.lineTo(midX, endY)
                            path.lineTo(endX, endY)
                        }

                        // Check if edge connects to the active node
                        if (edge.fromId == activeNodeId || edge.toId == activeNodeId) {
                            highlightedEdges.add(path)
                        } else {
                            normalEdges.add(path)
                        }
                    }
                }

                // Draw background edges first
                normalEdges.forEach { path ->
                    drawPath(path, Color.LightGray, style = Stroke(width = 2f))
                }
                // Draw active edges on top, thicker and colored
                highlightedEdges.forEach { path ->
                    drawPath(path, Color(0xFF1976D2), style = Stroke(width = 4f))
                }
            }

            graph.nodes.forEach { node ->
                Box(modifier = Modifier.offset { IntOffset(node.x.toInt(), node.y.toInt()) }
                    // Hover detection
                    .pointerInput(node.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Enter) {
                                    hoveredNodeId = node.id
                                } else if (event.type == PointerEventType.Exit) {
                                    hoveredNodeId = null
                                }
                            }
                        }
                    }.pointerInput(node.id + "_drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            node.x += dragAmount.x / zoom
                            node.y += dragAmount.y / zoom
                        }
                    }) {
                    when (node) {
                        is GraphNode.MetadataNode -> MetadataCard(node, onNodeClick)
                        is GraphNode.SnapshotNode -> SnapshotCard(node, onNodeClick)
                        is GraphNode.ManifestNode -> ManifestCard(node, onNodeClick)
                        is GraphNode.FileNode     -> FileCard(node, onNodeClick)
                        is GraphNode.RowNode      -> RowCard(node, onNodeClick) // NEW
                    }
                }
            }
        }

        // Minimap Radar UI
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(240.dp, 160.dp)
                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                // Allow dragging the minimap viewport rectangle to pan the main canvas
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val mapScale =
                            min(240f / graph.width.toFloat(), 160f / graph.height.toFloat())
                        // Inverse translation: dragging minimap view moves main canvas opposite
                        coroutineScope.launch {
                            offsetAnim.snapTo(
                                offsetAnim.value - Offset(
                                    dragAmount.x / mapScale * zoom, dragAmount.y / mapScale * zoom
                                )
                            )
                        }
                    }
                }) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val mapScale =
                    min(size.width / graph.width.toFloat(), size.height / graph.height.toFloat())
                val mapOffsetX = (size.width - (graph.width.toFloat() * mapScale)) / 2f
                val mapOffsetY = (size.height - (graph.height.toFloat() * mapScale)) / 2f

                translate(mapOffsetX, mapOffsetY) {
                    // 1. Draw mini nodes
                    graph.nodes.forEach { n ->
                        drawRect(
                            color = getGraphNodeColor(n), // Uses shared theme
                            topLeft = Offset(n.x.toFloat() * mapScale, n.y.toFloat() * mapScale),
                            size = Size(n.width.toFloat() * mapScale, n.height.toFloat() * mapScale)
                        )
                    }

                    // 2. Draw active viewport window
                    val vpW = viewportWidth / zoom
                    val vpH = viewportHeight / zoom
                    val vpX = -offsetAnim.value.x / zoom
                    val vpY = -offsetAnim.value.y / zoom

                    drawRect(
                        color = Color.Red.copy(alpha = 0.4f),
                        topLeft = Offset(vpX * mapScale, vpY * mapScale),
                        size = Size(vpW * mapScale, vpH * mapScale),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}