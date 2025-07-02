
package com.databasepopulator.database

import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.ForeignKeyMetadata
import com.databasepopulator.core.TableMetadata
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

/**
 * Извлекатель метаданных из базы данных
 */
class MetadataExtractor {
    
    /**
     * Извлекает метаданные всех таблиц из базы данных
     */
    fun extractTableMetadata(connection: Connection, databaseName: String): List<TableMetadata> {
        val metadata = connection.metaData
        val tables = mutableListOf<TableMetadata>()
        
        // Получаем список всех таблиц
        val tablesResultSet = metadata.getTables(null, null, "%", arrayOf("TABLE"))
        
        while (tablesResultSet.next()) {
            val tableName = tablesResultSet.getString("TABLE_NAME")
            
            // Пропускаем системные таблицы
            if (isSystemTable(tableName)) continue
            
            val tableMetadata = extractSingleTableMetadata(metadata, tableName)
            tables.add(tableMetadata)
        }
        
        tablesResultSet.close()
        return tables
    }
    
    /**
     * Извлекает метаданные одной таблицы
     */
    private fun extractSingleTableMetadata(metadata: DatabaseMetaData, tableName: String): TableMetadata {
        // Извлекаем информацию о колонках
        val columns = extractColumnMetadata(metadata, tableName)
        
        // Извлекаем первичные ключи
        val primaryKeys = extractPrimaryKeys(metadata, tableName)
        
        // Извлекаем внешние ключи
        val foreignKeys = extractForeignKeys(metadata, tableName)
        
        // Извлекаем уникальные ограничения
        val uniqueConstraints = extractUniqueConstraints(metadata, tableName)
        
        return TableMetadata(
            name = tableName,
            columns = columns,
            primaryKeys = primaryKeys,
            foreignKeys = foreignKeys,
            uniqueConstraints = uniqueConstraints
        )
    }
    
    /**
     * Извлекает метаданные колонок
     */
    private fun extractColumnMetadata(metadata: DatabaseMetaData, tableName: String): List<ColumnMetadata> {
        val columns = mutableListOf<ColumnMetadata>()
        val columnsResultSet = metadata.getColumns(null, null, tableName, "%")
        
        while (columnsResultSet.next()) {
            val column = ColumnMetadata(
                name = columnsResultSet.getString("COLUMN_NAME"),
                type = columnsResultSet.getString("TYPE_NAME"),
                sqlType = columnsResultSet.getInt("DATA_TYPE"),
                size = columnsResultSet.getInt("COLUMN_SIZE"),
                nullable = columnsResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                autoIncrement = columnsResultSet.getString("IS_AUTOINCREMENT") == "YES",
                defaultValue = columnsResultSet.getString("COLUMN_DEF")
            )
            columns.add(column)
        }
        
        columnsResultSet.close()
        return columns
    }
    
    /**
     * Извлекает первичные ключи
     */
    private fun extractPrimaryKeys(metadata: DatabaseMetaData, tableName: String): List<String> {
        val primaryKeys = mutableListOf<String>()
        val pkResultSet = metadata.getPrimaryKeys(null, null, tableName)
        
        while (pkResultSet.next()) {
            primaryKeys.add(pkResultSet.getString("COLUMN_NAME"))
        }
        
        pkResultSet.close()
        return primaryKeys
    }
    
    /**
     * Извлекает внешние ключи
     */
    private fun extractForeignKeys(metadata: DatabaseMetaData, tableName: String): List<ForeignKeyMetadata> {
        val foreignKeys = mutableListOf<ForeignKeyMetadata>()
        val fkResultSet = metadata.getImportedKeys(null, null, tableName)
        
        while (fkResultSet.next()) {
            val foreignKey = ForeignKeyMetadata(
                columnName = fkResultSet.getString("FKCOLUMN_NAME"),
                referencedTable = fkResultSet.getString("PKTABLE_NAME"),
                referencedColumn = fkResultSet.getString("PKCOLUMN_NAME")
            )
            foreignKeys.add(foreignKey)
        }
        
        fkResultSet.close()
        return foreignKeys
    }
    
    /**
     * Извлекает уникальные ограничения
     */
    private fun extractUniqueConstraints(metadata: DatabaseMetaData, tableName: String): List<List<String>> {
        val uniqueConstraints = mutableListOf<List<String>>()
        
        try {
            val indexInfo = metadata.getIndexInfo(null, null, tableName, true, false)
            val indexColumns = mutableMapOf<String, MutableList<String>>()
            
            while (indexInfo.next()) {
                val indexName = indexInfo.getString("INDEX_NAME") ?: continue
                val columnName = indexInfo.getString("COLUMN_NAME") ?: continue
                
                indexColumns.getOrPut(indexName) { mutableListOf() }.add(columnName)
            }
            
            uniqueConstraints.addAll(indexColumns.values.map { it.toList() })
            indexInfo.close()
        } catch (e: Exception) {
            // Игнорируем ошибки извлечения индексов
        }
        
        return uniqueConstraints
    }
    
    /**
     * Проверяет, является ли таблица системной
     */
    private fun isSystemTable(tableName: String): Boolean {
        val systemPrefixes = listOf("sys", "information_schema", "pg_", "sql_")
        return systemPrefixes.any { tableName.lowercase().startsWith(it) }
    }
}
