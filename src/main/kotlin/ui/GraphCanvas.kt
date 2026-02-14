package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import model.GraphModel
import model.GraphNode
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GraphCanvas(
    graph: GraphModel,
    selectedNodeIds: Set<String>,
    isSelectMode: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
) {
    val offsetAnim = remember { Animatable(Offset(100f, 100f), Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()
    
    // Smooth internal state to eliminate lag during gestures
    var localZoom by remember { mutableStateOf(zoom) }
    
    var hoveredNodeId by remember { mutableStateOf<String?>(null) }
    var marqueeStart by remember { mutableStateOf<Offset?>(null) }
    var marqueeEnd by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0))
            .pointerInput(isSelectMode) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        
                        if (event.type == PointerEventType.Scroll) {
                            val delta = changes.first().scrollDelta
                            val isZoom = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                            
                            if (isZoom) {
                                // Zoom at mouse position
                                val zoomFactor = Math.pow(1.1, -delta.y.toDouble()).toFloat()
                                val oldZoom = localZoom
                                val newZoom = (oldZoom * zoomFactor).coerceIn(0.1f, 3f)
                                
                                if (newZoom != oldZoom) {
                                    val mousePos = changes.first().position
                                    val layoutOffset = offsetAnim.value + (mousePos - offsetAnim.value) * (1 - newZoom / oldZoom)
                                    localZoom = newZoom
                                    onZoomChange(newZoom)
                                    coroutineScope.launch {
                                        offsetAnim.snapTo(layoutOffset)
                                    }
                                }
                            } else {
                                // Pan
                                coroutineScope.launch {
                                    offsetAnim.snapTo(offsetAnim.value - Offset(delta.x * 20f, delta.y * 20f))
                                }
                            }
                            changes.forEach { it.consume() }
                        } else {
                            // Detect pinch from multi-touch if reported as separate pointers
                            val zoomFactor = event.calculateZoom()
                            if (zoomFactor != 1f) {
                                val oldZoom = localZoom
                                val newZoom = (oldZoom * zoomFactor).coerceIn(0.1f, 3f)
                                if (newZoom != oldZoom) {
                                    val centroid = event.calculateCentroid()
                                    val layoutOffset = offsetAnim.value + (centroid - offsetAnim.value) * (1 - newZoom / oldZoom)
                                    localZoom = newZoom
                                    onZoomChange(newZoom)
                                    coroutineScope.launch {
                                        offsetAnim.snapTo(layoutOffset)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Keep the transform gestures for non-scroll interactions
            .pointerInput(isSelectMode) {
                if (!isSelectMode) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val oldZoom = localZoom
                        val newZoom = (oldZoom * gestureZoom).coerceIn(0.1f, 3f)

                        if (newZoom != oldZoom) {
                            val layoutOffset = offsetAnim.value + (centroid - offsetAnim.value) * (1 - newZoom / oldZoom)
                            localZoom = newZoom
                            onZoomChange(newZoom)
                            coroutineScope.launch {
                                offsetAnim.snapTo(layoutOffset + pan)
                            }
                        } else {
                            coroutineScope.launch {
                                offsetAnim.snapTo(offsetAnim.value + pan)
                            }
                        }
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            marqueeStart = offset
                            marqueeEnd = offset
                        },
                        onDragEnd = {
                            if (marqueeStart != null && marqueeEnd != null) {
                                val left = Math.min(marqueeStart!!.x, marqueeEnd!!.x)
                                val top = Math.min(marqueeStart!!.y, marqueeEnd!!.y)
                                val right = Math.max(marqueeStart!!.x, marqueeEnd!!.x)
                                val bottom = Math.max(marqueeStart!!.y, marqueeEnd!!.y)

                                val logLeft = (left - offsetAnim.value.x) / localZoom
                                val logTop = (top - offsetAnim.value.y) / localZoom
                                val logRight = (right - offsetAnim.value.x) / localZoom
                                val logBottom = (bottom - offsetAnim.value.y) / localZoom

                                val selRect = androidx.compose.ui.geometry.Rect(logLeft, logTop, logRight, logBottom)

                                val selected = graph.nodes.filter { n ->
                                    selRect.overlaps(
                                        androidx.compose.ui.geometry.Rect(
                                            n.x.toFloat(), n.y.toFloat(),
                                            (n.x + n.width).toFloat(), (n.y + n.height).toFloat()
                                        )
                                    )
                                }.map { it.id }.toSet()

                                onSelectionChange(selected)
                            }
                            marqueeStart = null
                            marqueeEnd = null
                        }
                    ) { change, _ ->
                        change.consume()
                        marqueeEnd = change.position
                    }
                }
            }) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        // Sync local state when external zoom changes (e.g. from buttons)
        LaunchedEffect(zoom) {
            if (Math.abs(zoom - localZoom) > 0.001f) {
                val viewportCenter = Offset(viewportWidth / 2f, viewportHeight / 2f)

                val oldZoom = localZoom
                val newZoom = zoom
                val layoutOffset = offsetAnim.value + (viewportCenter - offsetAnim.value) * (1 - newZoom / oldZoom)

                localZoom = newZoom
                offsetAnim.snapTo(layoutOffset)
            }
        }

        LaunchedEffect(selectedNodeIds) {
            if (selectedNodeIds.size == 1) {
                val selectedNode = graph.nodes.find { it.id == selectedNodeIds.first() }
                if (selectedNode != null) {
                    val currentZoom = localZoom
                    val currentX = offsetAnim.value.x
                    val currentY = offsetAnim.value.y

                    val nodeLeft = selectedNode.x.toFloat() * currentZoom + currentX
                    val nodeRight =
                        (selectedNode.x.toFloat() + selectedNode.width.toFloat()) * currentZoom + currentX
                    val nodeTop = selectedNode.y.toFloat() * currentZoom + currentY
                    val nodeBottom =
                        (selectedNode.y.toFloat() + selectedNode.height.toFloat()) * currentZoom + currentY

                    val margin = 20f

                    val isVisible =
                        nodeLeft >= margin && nodeRight <= (viewportWidth - margin) && nodeTop >= margin && nodeBottom <= (viewportHeight - margin)

                    if (!isVisible) {
                        val nodeCenterX = selectedNode.x.toFloat() + (selectedNode.width.toFloat() / 2f)
                        val nodeCenterY =
                            selectedNode.y.toFloat() + (selectedNode.height.toFloat() / 2f)

                        val targetX = (viewportWidth / 2f - nodeCenterX) * currentZoom
                        val targetY = (viewportHeight / 2f - nodeCenterY) * currentZoom

                        offsetAnim.animateTo(Offset(targetX, targetY))
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = localZoom
                scaleY = localZoom
                translationX = offsetAnim.value.x
                translationY = offsetAnim.value.y
                transformOrigin = TransformOrigin(0f, 0f)
            }) {
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { onSelectionChange(emptySet()) })
            }) {
                val activeNodeIds = if (hoveredNodeId != null) setOf(hoveredNodeId!!) else selectedNodeIds

                val normalEdges = mutableListOf<Pair<Path, Color>>()
                val highlightedEdges = mutableListOf<Pair<Path, Color>>()

                graph.edges.forEach { edge ->
                    val source = graph.nodes.find { it.id == edge.fromId }
                    val target = graph.nodes.find { it.id == edge.toId }

                    if (source != null && target != null) {
                        val path = Path()
                        val edgeColor = getGraphNodeBorderColor(source)

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

                        if (activeNodeIds.contains(edge.fromId) || activeNodeIds.contains(edge.toId)) {
                            highlightedEdges.add(path to edgeColor)
                        } else {
                            normalEdges.add(path to edgeColor)
                        }
                    }
                }

                normalEdges.forEach { (path, color) ->
                    drawPath(path, color.copy(alpha = 0.7f), style = Stroke(width = 2f))
                }

                highlightedEdges.forEach { (path, color) ->
                    drawPath(path, color, style = Stroke(width = 6f))
                }
            }

            graph.nodes.forEach { node ->
                Box(modifier = Modifier.offset { IntOffset(node.x.toInt(), node.y.toInt()) }) {
                    TooltipArea(
                        tooltip = {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xEE333333), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = getNodeTooltipText(node),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        delayMillis = 400,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(16.dp, 16.dp)
                        )
                    ) {
                        Box(
                            modifier = Modifier
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
                                }
                                .pointerInput(node.id + "_interaction") {
                                    detectTapGestures(onTap = { onSelectionChange(setOf(node.id)) })
                                }
                                .pointerInput(node.id + "_drag") {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / localZoom
                                        val dy = dragAmount.y / localZoom

                                        if (selectedNodeIds.contains(node.id)) {
                                            graph.nodes.filter { it.id in selectedNodeIds }.forEach { n ->
                                                n.x += dx
                                                n.y += dy
                                            }
                                        } else {
                                            node.x += dx
                                            node.y += dy
                                            onSelectionChange(setOf(node.id))
                                        }
                                    }
                                }
                                // Removed redundant .clickable to avoid double selection triggers
                        ) {
                            val isActive = hoveredNodeId == node.id || selectedNodeIds.contains(node.id)
                            when (node) {
                                is GraphNode.MetadataNode -> MetadataCard(node, isSelected = isActive)
                                is GraphNode.SnapshotNode -> SnapshotCard(node, isSelected = isActive)
                                is GraphNode.ManifestNode -> ManifestCard(node, isSelected = isActive)
                                is GraphNode.FileNode     -> FileCard(node, isSelected = isActive)
                                is GraphNode.RowNode      -> RowCard(node, isSelected = isActive)
                            }
                        }
                    }
                }
            }
        }

        if (marqueeStart != null && marqueeEnd != null) {
            Canvas(Modifier.fillMaxSize()) {
                val left = minOf(marqueeStart!!.x, marqueeEnd!!.x)
                val top = minOf(marqueeStart!!.y, marqueeEnd!!.y)
                val width = kotlin.math.abs(marqueeStart!!.x - marqueeEnd!!.x)
                val height = kotlin.math.abs(marqueeStart!!.y - marqueeEnd!!.y)

                drawRect(
                    color = Color(0xFF1976D2).copy(alpha = 0.2f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                drawRect(
                    color = Color(0xFF1976D2),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(1.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(240.dp, 160.dp)
                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val mapScale =
                            min(240f / graph.width.toFloat(), 160f / graph.height.toFloat())
                        coroutineScope.launch {
                            offsetAnim.snapTo(
                                offsetAnim.value - Offset(
                                    dragAmount.x / mapScale * localZoom, dragAmount.y / mapScale * localZoom
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
                    graph.nodes.forEach { n ->
                        drawRect(
                            color = getGraphNodeColor(n),
                            topLeft = Offset(n.x.toFloat() * mapScale, n.y.toFloat() * mapScale),
                            size = Size(n.width.toFloat() * mapScale, n.height.toFloat() * mapScale)
                        )
                    }

                    val vpW = viewportWidth / localZoom
                    val vpH = viewportHeight / localZoom
                    val vpX = -offsetAnim.value.x / localZoom
                    val vpY = -offsetAnim.value.y / localZoom

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

private fun getNodeTooltipText(node: GraphNode): String {
    return when (node) {
        is GraphNode.MetadataNode -> "Metadata File\nVersion: ${node.data.formatVersion}\nSnapshots: ${node.data.snapshots.size}"
        is GraphNode.SnapshotNode -> "Snapshot: ${node.data.snapshotId}\nOp: ${node.data.summary["operation"]}"
        is GraphNode.ManifestNode -> "Manifest\nAdded Files: ${node.data.addedFilesCount}\nDeleted Files: ${node.data.deletedFilesCount}"
        is GraphNode.FileNode     -> {
            val type = when (node.data.content ?: 0) {
                1    -> "Position Delete File"
                2    -> "Equality Delete File"
                else -> "Data File"
            }
            "$type\nRecords: ${node.data.recordCount}\nSize: ${node.data.fileSizeInBytes} bytes"
        }

        is GraphNode.RowNode      -> when (node.content) {
            1    -> "Position Delete Record"
            2    -> "Equality Delete Record"
            else -> "Data Record"
        }
    }
}