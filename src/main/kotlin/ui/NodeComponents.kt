package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphNode

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
fun ManifestListCard(node: GraphNode.ManifestListNode, onClick: (GraphNode) -> Unit) {
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(Color(0xFFFFCC80), RoundedCornerShape(8.dp))
        .border(BorderStroke(2.dp, Color(0xFFF57C00)), RoundedCornerShape(8.dp))
        .clickable { onClick(node) }
        .padding(8.dp), contentAlignment = Alignment.Center) {
        Text("Manifest List", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ManifestCard(node: GraphNode.ManifestNode, onClick: (GraphNode) -> Unit) {
    // Distinguish Data Manifest (0) vs Delete Manifest (1)
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
            Text("Added: ${node.data.addedDataFilesCount} files", fontSize = 11.sp)
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
                if (isDelete) "DEL FILE" else "DATA FILE",
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
        .background(Color(0xFFFFF9C4), RoundedCornerShape(4.dp))
        .border(1.dp, Color(0xFFFBC02D), RoundedCornerShape(4.dp))
        .clickable { onClick(node) }
        .padding(4.dp)) {
        // Preview the first few values as a string
        val previewText = node.data.values.joinToString(", ")
        Column {
            Text("DATA ROW", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Text(
                text = previewText,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}