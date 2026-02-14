package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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

    var hoveredNodeId by remember { mutableStateOf<String?>(null) }
    var marqueeStart by remember { mutableStateOf<Offset?>(null) }
    var marqueeEnd by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0))
            .pointerInput(isSelectMode) {
                if (isSelectMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            marqueeStart = offset
                            marqueeEnd = offset
                        },
                        onDragEnd = {
                            if (marqueeStart != null && marqueeEnd != null) {
                                val left = minOf(marqueeStart!!.x, marqueeEnd!!.x)
                                val top = minOf(marqueeStart!!.y, marqueeEnd!!.y)
                                val right = maxOf(marqueeStart!!.x, marqueeEnd!!.x)
                                val bottom = maxOf(marqueeStart!!.y, marqueeEnd!!.y)

                                val logLeft = (left - offsetAnim.value.x) / zoom
                                val logTop = (top - offsetAnim.value.y) / zoom
                                val logRight = (right - offsetAnim.value.x) / zoom
                                val logBottom = (bottom - offsetAnim.value.y) / zoom

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
                } else {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        onZoomChange((zoom * gestureZoom).coerceIn(0.1f, 3f))
                        coroutineScope.launch {
                            offsetAnim.snapTo(offsetAnim.value + pan)
                        }
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

        LaunchedEffect(selectedNodeIds) {
            if (selectedNodeIds.size == 1) {
                val selectedNode = graph.nodes.find { it.id == selectedNodeIds.first() }
                if (selectedNode != null) {
                    val currentX = offsetAnim.value.x
                    val currentY = offsetAnim.value.y

                    val nodeLeft = selectedNode.x.toFloat() * zoom + currentX
                    val nodeRight =
                        (selectedNode.x.toFloat() + selectedNode.width.toFloat()) * zoom + currentX
                    val nodeTop = selectedNode.y.toFloat() * zoom + currentY
                    val nodeBottom =
                        (selectedNode.y.toFloat() + selectedNode.height.toFloat()) * zoom + currentY

                    val margin = 20f

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
        }

        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offsetAnim.value.x
                translationY = offsetAnim.value.y
            }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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
                    drawPath(path, color, style = Stroke(width = 4f))
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
                                        val dx = dragAmount.x / zoom
                                        val dy = dragAmount.y / zoom

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
                            when (node) {
                                is GraphNode.MetadataNode -> MetadataCard(node)
                                is GraphNode.SnapshotNode -> SnapshotCard(node)
                                is GraphNode.ManifestNode -> ManifestCard(node)
                                is GraphNode.FileNode     -> FileCard(node)
                                is GraphNode.RowNode      -> RowCard(node)
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
                    graph.nodes.forEach { n ->
                        drawRect(
                            color = getGraphNodeColor(n),
                            topLeft = Offset(n.x.toFloat() * mapScale, n.y.toFloat() * mapScale),
                            size = Size(n.width.toFloat() * mapScale, n.height.toFloat() * mapScale)
                        )
                    }

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

private fun getNodeTooltipText(node: GraphNode): String {
    return when (node) {
        is GraphNode.MetadataNode -> "Metadata File\nVersion: ${node.data.formatVersion}\nSnapshots: ${node.data.snapshots.size}"
        is GraphNode.SnapshotNode -> "Snapshot: ${node.data.snapshotId}\nOp: ${node.data.summary["operation"]}"
        is GraphNode.ManifestNode -> "Manifest\nAdded Files: ${node.data.addedFilesCount}\nDeleted Files: ${node.data.deletedFilesCount}"
        is GraphNode.FileNode     -> "Data File\nRecords: ${node.data.recordCount}\nSize: ${node.data.fileSizeInBytes} bytes"
        is GraphNode.RowNode      -> if (node.isDelete) "Delete Record" else "Data Record"
    }
}