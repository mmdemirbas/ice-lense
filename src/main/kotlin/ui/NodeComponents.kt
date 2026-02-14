package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphNode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    is GraphNode.RowNode      -> when (node.content) {
        1    -> Color(0xFFFFCCBC) // Position Delete
        2    -> Color(0xFFFFAB91) // Equality Delete
        else -> Color(0xFFFFF9C4) // Data Row
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

    is GraphNode.RowNode      -> when (node.content) {
        1    -> Color(0xFFE64A19) // Position Delete
        2    -> Color(0xFFBF360C) // Equality Delete
        else -> Color(0xFFFBC02D) // Data Row
    }
}

private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

fun formatTimestamp(ms: Long?): String {
    if (ms == null) return "N/A"
    return try {
        val formatted = timestampFormatter.format(Instant.ofEpochMilli(ms))
        "$formatted ($ms)"
    } catch (e: Exception) {
        "$ms (Error)"
    }
}

@Composable
fun DetailTable(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
    ) {
        content()
    }
}

@Composable
fun DetailRow(key: String, value: String, isHeader: Boolean = false, isDark: Boolean = false) {
    val bgColor = if (isHeader) (if (isDark) Color(0xFF444444) else Color(0xFFF5F5F5)) else Color.Transparent
    val keyColor = if (isHeader) (if (isDark) Color.White else Color.Black) else (if (isDark) Color.LightGray else Color.Gray)
    val valColor = if (isDark) Color.White else Color.Black
    val dividerColor = if (isDark) Color(0xFF555555) else Color(0xFFE0E0E0)

    Row(
        Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            modifier = Modifier.weight(0.4f),
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
            color = keyColor
        )
        Box(Modifier.width(1.dp).height(12.dp).background(dividerColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            modifier = Modifier.weight(0.6f),
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (isHeader) null else FontFamily.Monospace,
            color = valColor,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
}

@Composable
fun NodeTooltip(node: GraphNode) {
    Column(
        modifier = Modifier
            .background(Color(0xEE333333), RoundedCornerShape(4.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            .padding(8.dp)
            .width(IntrinsicSize.Max)
            .defaultMinSize(minWidth = 250.dp)
    ) {
        val title = when (node) {
            is GraphNode.MetadataNode -> "Metadata File"
            is GraphNode.SnapshotNode -> "Snapshot"
            is GraphNode.ManifestNode -> if (node.data.content == 1) "Delete Manifest" else "Data Manifest"
            is GraphNode.FileNode -> when (node.data.content ?: 0) {
                1 -> "Position Delete File"
                2 -> "Equality Delete File"
                else -> "Data File"
            }
            is GraphNode.RowNode -> when (node.content) {
                1 -> "Position Delete Row"
                2 -> "Equality Delete Row"
                else -> "Data Row"
            }
        }
        
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        DetailTable {
            when (node) {
                is GraphNode.MetadataNode -> {
                    DetailRow("File", node.fileName, isDark = true)
                    DetailRow("Version", "${node.data.formatVersion}", isDark = true)
                    DetailRow("Last Updated", formatTimestamp(node.data.lastUpdatedMs), isDark = true)
                    DetailRow("Snapshots", "${node.data.snapshots.size}", isDark = true)
                }
                is GraphNode.SnapshotNode -> {
                    DetailRow("ID", "${node.data.snapshotId}", isDark = true)
                    DetailRow("Operation", node.data.summary["operation"] ?: "N/A", isDark = true)
                    DetailRow("Timestamp", formatTimestamp(node.data.timestampMs), isDark = true)
                }
                is GraphNode.ManifestNode -> {
                    DetailRow("Added", "${node.data.addedFilesCount} files", isDark = true)
                    DetailRow("Deleted", "${node.data.deletedFilesCount} files", isDark = true)
                    DetailRow("Sequence", "${node.data.sequenceNumber ?: "N/A"}", isDark = true)
                }
                is GraphNode.FileNode -> {
                    DetailRow("ID", "File ${node.simpleId}", isDark = true)
                    DetailRow("Format", node.data.fileFormat ?: "N/A", isDark = true)
                    DetailRow("Records", "${node.data.recordCount}", isDark = true)
                    DetailRow("Size", "${node.data.fileSizeInBytes} bytes", isDark = true)
                }
                is GraphNode.RowNode -> {
                    node.data.entries.take(5).forEach { (k, v) ->
                        DetailRow(k, v.toString(), isDark = true)
                    }
                    if (node.data.size > 5) {
                        DetailRow("...", "and ${node.data.size - 5} more", isDark = true)
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataCard(node: GraphNode.MetadataNode, isSelected: Boolean = false) {
    val borderWidth = if (isSelected) 4.dp else 2.dp
    val borderColor = if (isSelected) Color.Black else getGraphNodeBorderColor(node)
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(getGraphNodeColor(node), RoundedCornerShape(8.dp))
        .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(8.dp))
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
fun SnapshotCard(node: GraphNode.SnapshotNode, isSelected: Boolean = false) {
    val borderWidth = if (isSelected) 4.dp else 2.dp
    val borderColor = if (isSelected) Color.Black else Color(0xFF1976D2)
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(Color(0xFFBBDEFB), RoundedCornerShape(8.dp))
        .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(8.dp))
        .padding(8.dp)) {
        Column {
            Text("SNAPSHOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Text("ID: ${node.data.snapshotId}", fontWeight = FontWeight.Bold)
            Text("Op: ${node.data.summary["operation"] ?: "unknown"}", fontSize = 12.sp)
        }
    }
}

@Composable
fun ManifestCard(node: GraphNode.ManifestNode, isSelected: Boolean = false) {
    val color = if (node.data.content == 1) Color(0xFFFFCDD2) else Color(0xFFC8E6C9)
    val borderColor = if (isSelected) Color.Black else (if (node.data.content == 1) Color(0xFFD32F2F) else Color(0xFF388E3C))
    val borderWidth = if (isSelected) 4.dp else 2.dp

    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(color, RoundedCornerShape(8.dp))
        .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(8.dp))
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
fun FileCard(node: GraphNode.FileNode, isSelected: Boolean = false) {
    val content = node.data.content ?: 0
    val label = when (content) {
        1    -> "POS DELETE ${node.simpleId}"
        2    -> "EQ DELETE ${node.simpleId}"
        else -> "DATA FILE ${node.simpleId}"
    }
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val borderColor = if (isSelected) Color.Black else getGraphNodeBorderColor(node)

    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(getGraphNodeColor(node), RoundedCornerShape(4.dp))
        .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(4.dp))
        .padding(4.dp)) {
        Column {
            Text(
                label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            Text(node.data.fileFormat.orEmpty(), fontSize = 10.sp)
            Text("${node.data.recordCount} rows", fontSize = 10.sp)
        }
    }
}

@Composable
fun RowCard(node: GraphNode.RowNode, isSelected: Boolean = false) {
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val borderColor = if (isSelected) Color.Black else getGraphNodeBorderColor(node)
    Box(
        modifier = Modifier
        .size(node.width.dp, node.height.dp)
        .background(getGraphNodeColor(node), RoundedCornerShape(4.dp))
        .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(4.dp))
        .padding(6.dp)) {
        Column(Modifier.fillMaxSize()) {
            val label = when (node.content) {
                1    -> "POS DELETE ROW"
                2    -> "EQ DELETE ROW"
                else -> "DATA ROW"
            }
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(2.dp))
            node.data.entries
                .filter { (k, _) -> !(node.content == 1 && k == "file_path" && node.data.containsKey("target_file")) }
                .take(3)
                .forEach { (k, v) ->
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