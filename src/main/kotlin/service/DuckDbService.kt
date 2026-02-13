package service

import java.io.File
import java.sql.DriverManager
import java.sql.ResultSet

object DuckDbService {

    // Load JDBC driver
    init {
        Class.forName("org.duckdb.DuckDBDriver")
    }

    fun queryParquet(filePath: String): List<Map<String, Any>> {
        val rows = mutableListOf<Map<String, Any>>()

        // Resolve symlinks and pure native path for the OS
        val canonicalPath = File(filePath).canonicalPath
        // Escape single quotes just in case the path contains them
        val safePath = canonicalPath.replace("'", "''").replace("\\", "/")

        // DuckDB In-Memory connection
        DriverManager.getConnection("jdbc:duckdb:").use { conn ->
            val stmt = conn.createStatement()

            // Explicitly use read_parquet() to bypass auto-detect globbing issues
            val sql = "SELECT * FROM read_parquet('$safePath') LIMIT 50"
            val rs: ResultSet = stmt.executeQuery(sql)
            val meta = rs.metaData
            val colCount = meta.columnCount

            while (rs.next()) {
                val row = mutableMapOf<String, Any>()
                for (i in 1..colCount) {
                    val colName = meta.getColumnName(i)
                    val value = rs.getObject(i) ?: "null"
                    row[colName] = value
                }
                rows.add(row)
            }
        }
        return rows
    }
}
