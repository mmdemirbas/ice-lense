package ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.swing.JFileChooser

@Composable
fun Sidebar(
    warehousePath: String?,
    tables: List<String>,
    selectedTable: String?,
    showRows: Boolean,
    onShowRowsChange: (Boolean) -> Unit,
    showMetadata: Boolean,
    onShowMetadataChange: (Boolean) -> Unit,
    showSnapshots: Boolean,
    onShowSnapshotsChange: (Boolean) -> Unit,
    showManifests: Boolean,
    onShowManifestsChange: (Boolean) -> Unit,
    showDataFiles: Boolean,
    onShowDataFilesChange: (Boolean) -> Unit,
    onTableSelect: (String) -> Unit,
    onWarehouseChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxHeight().background(Color(0xFFF5F5F5)).padding(8.dp)
    ) {
        Button(
            onClick = {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Select Iceberg Warehouse Directory"
                // Default to user home or current warehouse if set
                val startDir = warehousePath ?: System.getProperty("user.home")
                chooser.currentDirectory = File(startDir)

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    onWarehouseChange(chooser.selectedFile.absolutePath)
                }
            }, modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Warehouse", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filtering
        Text("FILTER NODES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showMetadata, onCheckedChange = onShowMetadataChange, modifier = Modifier.size(32.dp))
                Text("Metadata", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showSnapshots, onCheckedChange = onShowSnapshotsChange, modifier = Modifier.size(32.dp))
                Text("Snapshots", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showManifests, onCheckedChange = onShowManifestsChange, modifier = Modifier.size(32.dp))
                Text("Manifests", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showDataFiles, onCheckedChange = onShowDataFilesChange, modifier = Modifier.size(32.dp))
                Text("Data Files", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showRows, onCheckedChange = onShowRowsChange, modifier = Modifier.size(32.dp))
                Text("Data Rows", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (warehousePath != null) {
            Text(
                text = "WAREHOUSE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                text = File(warehousePath).name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "TABLES (${tables.size})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val tableListState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = tableListState, modifier = Modifier.fillMaxSize()) {
                    items(tables) { tableName ->
                        val isSelected = tableName == selectedTable
                        val bgColor = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
                        val textColor = if (isSelected) Color(0xFF1976D2) else Color.Black

                        Box(
                            modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .clickable { onTableSelect(tableName) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)) {
                            Text(
                                text = tableName,
                                fontSize = 13.sp,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = tableListState)
                )
            }
        } else {
            Text(
                text = "No warehouse selected.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}