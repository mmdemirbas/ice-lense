package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphNode

fun getGraphNodeColor(node: GraphNode): Color = when (node) {
    is GraphNode.MetadataNode -> Color(0xFFE1BEE7)
    is GraphNode.SnapshotNode -> Color(0xFFBBDEFB)
    is GraphNode.ManifestNode -> when (node.data.content) {
        1    -> Color(0xFFFFCDD2)
        else -> Color(0xFFC8E6C9)
    }

    is GraphNode.FileNode     -> when (node.data.content ?: 0) {
        1    -> Color(0xFFFFCC80)
        2    -> Color(0xFFEF9A9A)
        else -> Color(0xFFE0F2F1)
    }

    is GraphNode.RowNode      -> when {
        node.isDelete -> Color(0xFFFFCCBC)
        else          -> Color(0xFFFFF9C4)
    }
}

fun getGraphNodeBorderColor(node: GraphNode): Color = when (node) {
    is GraphNode.MetadataNode -> Color(0xFF8E24AA)
    is GraphNode.SnapshotNode -> Color(0xFF1976D2)
    is GraphNode.ManifestNode -> when (node.data.content) {
        1    -> Color(0xFFD32F2F)
        else -> Color(0xFF388E3C)
    }

    is GraphNode.FileNode     -> when (node.data.content ?: 0) {
        1    -> Color(0xFFF57C00)
        2    -> Color(0xFFD32F2F)
        else -> Color(0xFF00897B)
    }

    is GraphNode.RowNode      -> when {
        node.isDelete -> Color(0xFFE64A19)
        else          -> Color(0xFFFBC02D)
    }
}

@Composable
fun MetadataCard(node: GraphNode.MetadataNode, onClick: (GraphNode) -> Unit) {
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(getGraphNodeColor(node), RoundedCornerShape(8.dp))
        .border(BorderStroke(2.dp, getGraphNodeBorderColor(node)), RoundedCornerShape(8.dp))
        .clickable { onClick(node) }
        .padding(8.dp)) {
        Column {
            Text(
                "METADATA FILE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            Text(node.fileName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Format V${node.data.formatVersion}", fontSize = 11.sp)
            Text("Snapshots: ${node.data.snapshots.size}", fontSize = 11.sp)
            Text("Current Snap: ${node.data.currentSnapshotId ?: "None"}", fontSize = 10.sp)
        }
    }
}

@Composable
fun SnapshotCard(node: GraphNode.SnapshotNode, onClick: (GraphNode) -> Unit) {
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(Color(0xFFBBDEFB), RoundedCornerShape(8.dp))
        .border(BorderStroke(2.dp, Color(0xFF1976D2)), RoundedCornerShape(8.dp))
        .clickable { onClick(node) }
        .padding(8.dp)) {
        Column {
            Text("SNAPSHOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Text("ID: ${node.data.snapshotId}", fontWeight = FontWeight.Bold)
            Text("Op: ${node.data.summary["operation"] ?: "unknown"}", fontSize = 12.sp)
        }
    }
}

@Composable
fun ManifestCard(node: GraphNode.ManifestNode, onClick: (GraphNode) -> Unit) {
    val color = if (node.data.content == 1) Color(0xFFFFCDD2) else Color(0xFFC8E6C9)
    val borderColor = if (node.data.content == 1) Color(0xFFD32F2F) else Color(0xFF388E3C)

    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(color, RoundedCornerShape(8.dp))
        .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(8.dp))
        .clickable { onClick(node) }
        .padding(8.dp)) {
        Column {
            Text(
                if (node.data.content == 1) "DELETE MANIFEST" else "DATA MANIFEST",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Added: ${node.data.addedFilesCount} files", fontSize = 11.sp)
        }
    }
}

@Composable
fun FileCard(node: GraphNode.FileNode, onClick: (GraphNode) -> Unit) {
    // V2 Content: 0=Data, 1=Pos Delete, 2=Eq Delete
    val isDelete = (node.data.content ?: 0) > 0
    val bg = if (isDelete) Color(0xFFEF9A9A) else Color(0xFFE0F2F1)

    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(bg, RoundedCornerShape(4.dp))
        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        .clickable { onClick(node) }
        .padding(4.dp)) {
        Column {
            Text(
                if (isDelete) "DEL FILE ${node.simpleId}" else "DATA FILE ${node.simpleId}",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Text(node.data.fileFormat.orEmpty(), fontSize = 10.sp)
            Text("${node.data.recordCount} rows", fontSize = 10.sp)
        }
    }
}

@Composable
fun RowCard(node: GraphNode.RowNode, onClick: (GraphNode) -> Unit) {
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(getGraphNodeColor(node), RoundedCornerShape(4.dp))
        .border(1.dp, getGraphNodeBorderColor(node), RoundedCornerShape(4.dp))
        .clickable { onClick(node) }
        .padding(6.dp)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                if (node.isDelete) "DELETE ROW" else "DATA ROW",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(2.dp))
            node.data.entries.take(3).forEach { (k, v) ->
                Text(
                    text = "$k: $v",
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (node.data.size > 3) {
                Text("...", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}