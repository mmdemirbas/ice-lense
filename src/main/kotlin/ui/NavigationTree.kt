package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode

@Composable
fun NavigationTree(
    graph: GraphModel,
    selectedNode: GraphNode?,
    onNodeSelect: (GraphNode) -> Unit
) {
    // Flatten DAG into a list of (Node to Depth) for LazyColumn
    val flattenedTree = remember(graph) { flattenGraph(graph) }
    val listState = rememberLazyListState()

    // 1. Auto-scroll to selected node
    LaunchedEffect(selectedNode) {
        if (selectedNode != null) {
            val index = flattenedTree.indexOfFirst { it.first.id == selectedNode.id }
            if (index >= 0) {
                // Scroll the item into view smoothly
                listState.animateScrollToItem(index)
            }
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(flattenedTree) { (node, depth) ->
            val isSelected = node.id == selectedNode?.id
            val bgColor = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
            val textColor = if (isSelected) Color(0xFF1976D2) else Color.DarkGray

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .clickable { onNodeSelect(node) }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .padding(start = (depth * 16).dp) // Indentation based on depth
            ) {
                Text(
                    text = getNodeLabel(node),
                    fontSize = 11.sp,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Depth-First Search to flatten the tree
private fun flattenGraph(graph: GraphModel): List<Pair<GraphNode, Int>> {
    val result = mutableListOf<Pair<GraphNode, Int>>()
    val edgesBySource = graph.edges.groupBy { it.fromId }

    fun traverse(nodeId: String, depth: Int) {
        val node = graph.nodes.find { it.id == nodeId } ?: return
        result.add(node to depth)
        val children = edgesBySource[nodeId]?.map { it.toId } ?: emptyList()
        children.forEach { traverse(it, depth + 1) }
    }

    // Find roots (nodes with no incoming edges)
    val childIds = graph.edges.map { it.toId }.toSet()
    val roots = graph.nodes.filter { it.id !in childIds }

    roots.forEach { traverse(it.id, 0) }
    return result
}

private fun getNodeLabel(node: GraphNode): String {
    return when (node) {
        is GraphNode.SnapshotNode -> "Snap: ${node.data.snapshotId}"
        is GraphNode.ManifestListNode -> "Manifest List"
        is GraphNode.ManifestNode -> "Manifest (${node.data.addedDataFilesCount} adds)"
        is GraphNode.FileNode -> "File: ${node.data.filePath?.substringAfterLast("/")}"
    }
}