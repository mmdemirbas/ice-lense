package service

import java.sql.DriverManager
import java.sql.ResultSet

object DuckDbService {

    // Load JDBC driver
    init {
        Class.forName("org.duckdb.DuckDBDriver")
    }

    fun queryParquet(filePath: String): List<Map<String, Any>> {
        val rows = mutableListOf<Map<String, Any>>()

        // DuckDB In-Memory connection
        DriverManager.getConnection("jdbc:duckdb:").use { conn ->
            val stmt = conn.createStatement()
            // Query local parquet file
            val sql = "SELECT * FROM '$filePath' LIMIT 50"
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