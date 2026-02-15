package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// --- Metadata JSON ---
@Serializable
data class TableMetadata(
    @SerialName("format-version") val formatVersion: Int? = null,
    @SerialName("table-uuid") val tableUuid: String? = null,
    val location: String? = null,
    @SerialName("last-sequence-number") val lastSequenceNumber: Int? = null,
    @SerialName("last-updated-ms") val lastUpdatedMs: Long? = null,
    @SerialName("last-column-id") val lastColumnId: Int? = null,
    @SerialName("current-snapshot-id") val currentSnapshotId: Long? = null,
    val schemas: List<TableSchema> = emptyList(),
    val snapshots: List<Snapshot> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
)

@Serializable
data class TableSchema(
    val type: String? = null,
    @SerialName("schema-id") val schemaId: Int? = null,
    @SerialName("identifier-field-ids") val identifierFieldIds: List<Int> = emptyList(),
    val fields: List<TableSchemaField> = emptyList(),
)

@Serializable
data class TableSchemaField(
    val id: Int? = null,
    val name: String? = null,
    val required: Boolean? = null,
    val type: JsonElement? = null,
)

@Serializable
data class Snapshot(
    @SerialName("snapshot-id") val snapshotId: Long? = null,
    @SerialName("parent-snapshot-id") val parentSnapshotId: Long? = null,
    @SerialName("sequence-number") val sequenceNumber: Long? = null,
    @SerialName("schema-id") val schemaId: Int? = null,
    @SerialName("timestamp-ms") val timestampMs: Long? = null,
    @SerialName("manifest-list") val manifestList: String? = null, // Path to Avro file
    val summary: Map<String, String> = emptyMap(),
)

// --- Manifest List (Avro) ---
@Serializable
data class ManifestListEntry(
    @SerialName("manifest_path") val manifestPath: String? = null,
    @SerialName("manifest_length") val manifestLength: Long? = null,
    @SerialName("partition_spec_id") val partitionSpecId: Int? = null,
    @SerialName("content") val content: Int? = null, // 0=Data, 1=Deletes
    @SerialName("sequence_number") val sequenceNumber: Int? = null,
    @SerialName("min_sequence_number") val cominSequenceNumber: Int? = null,
    @SerialName("added_snapshot_id") val addedSnapshotId: Long? = null,
    @SerialName("added_files_count") val addedFilesCount: Int? = null,
    @SerialName("existing_files_count") val existingFilesCount: Int? = null,
    @SerialName("deleted_files_count") val deletedFilesCount: Int? = null,
    @SerialName("added_rows_count") val addedRowsCount: Long? = null,
    @SerialName("existing_rows_count") val existingRowsCount: Long? = null,
    @SerialName("deleted_rows_count") val deletedRowsCount: Long? = null,
)

// --- Manifest File (Avro) ---
// Wraps the 'data_file' struct found inside manifest entries
@Serializable
data class ManifestEntry(
    val status: Int, // 0=EXISTING, 1=ADDED, 2=DELETED
    @SerialName("snapshot_id") val snapshotId: Long? = null,
    @SerialName("sequence_number") val sequenceNumber: Long? = null,
    @SerialName("file_sequence_number") val fileSequenceNumber: Long? = null,
    @SerialName("data_file") val dataFile: DataFile? = null,
)

@Serializable
data class DataFile(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_format") val fileFormat: String? = null,
    @SerialName("record_count") val recordCount: Long? = null,
    @SerialName("file_size_in_bytes") val fileSizeInBytes: Long? = null,
    // V2 Specific: 0=DATA, 1=POSITION_DELETES, 2=EQUALITY_DELETES
    val content: Int? = null,
    @SerialName("data_sequence_number") val dataSequenceNumber: Long? = null,
    @SerialName("column_sizes") val columnSizes: List<KeyValuePairLong> = emptyList(),
    @SerialName("value_counts") val valueCounts: List<KeyValuePairLong> = emptyList(),
    @SerialName("null_value_counts") val nullValueCounts: List<KeyValuePairLong> = emptyList(),
    @SerialName("nan_value_counts") val nanValueCounts: List<KeyValuePairLong> = emptyList(),
    @SerialName("lower_bounds") val lowerBounds: List<KeyValuePairBytes> = emptyList(),
    @SerialName("upper_bounds") val upperBounds: List<KeyValuePairBytes> = emptyList(),
    @SerialName("key_metadata") val keyMetadata: ByteArray? = null,
    @SerialName("split_offsets") val splitOffsets: List<Long> = emptyList(),
    @SerialName("equality_ids") val equalityIds: List<Int>? = null,
    @SerialName("sort_order_id") val sorderOrderId: Long? = null,
)


@Serializable
data class KeyValuePairInt(val key: Int, val value: Int)

@Serializable
data class KeyValuePairString(val key: Int, val value: String)

@Serializable
data class KeyValuePairLong(val key: Int, val value: Long)

@Serializable
data class KeyValuePairBytes(val key: Int, val value: ByteArray)
