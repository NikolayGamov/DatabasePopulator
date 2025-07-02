
package com.databasepopulator.database

import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.ForeignKeyMetadata
import com.databasepopulator.core.TableMetadata
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

/**
 * Извлекает метаданные из базы данных
 */
class MetadataExtractor {
    
    /**
     * Извлекает метаданные таблиц из базы данных
     */
    fun extractTableMetadata(connection: Connection, databaseName: String): List<TableMetadata> {
        val tables = mutableListOf<TableMetadata>()
        val metaData = connection.metaData
        
        println("Извлечение метаданных для базы данных: $databaseName")
        
        try {
            // Определяем схему в зависимости от типа БД
            val schema = when {
                isIgniteDatabase(connection) -> "PUBLIC"
                isPostgreSQLDatabase(connection) -> "public"
                else -> null
            }
            
            println("Используемая схема: $schema")
            
            // Получаем список таблиц
            val tableResultSet = metaData.getTables(null, schema, "%", arrayOf("TABLE"))
            
            while (tableResultSet.next()) {
                val tableName = tableResultSet.getString("TABLE_NAME")
                
                // Пропускаем системные таблицы
                if (isSystemTable(tableName, connection)) {
                    continue
                }
                
                println("Обработка таблицы: $tableName")
                
                // Извлекаем колонки
                val columns = extractColumnMetadata(metaData, schema, tableName)
                
                // Извлекаем внешние ключи
                val foreignKeys = extractForeignKeyMetadata(metaData, schema, tableName)
                
                // Извлекаем первичные ключи
                val primaryKeys = extractPrimaryKeyMetadata(metaData, schema, tableName)
                
                tables.add(TableMetadata(tableName, columns, foreignKeys, primaryKeys))
                println("Таблица $tableName: ${columns.size} колонок, ${foreignKeys.size} внешних ключей")
            }
            
            tableResultSet.close()
            
        } catch (e: Exception) {
            println("Ошибка при извлечении метаданных: ${e.message}")
            e.printStackTrace()
        }
        
        return tables
    }
    
    /**
     * Извлекает метаданные колонок
     */
    private fun extractColumnMetadata(metaData: DatabaseMetaData, schema: String?, tableName: String): List<ColumnMetadata> {
        val columns = mutableListOf<ColumnMetadata>()
        
        val columnResultSet = metaData.getColumns(null, schema, tableName, "%")
        
        while (columnResultSet.next()) {
            val columnName = columnResultSet.getString("COLUMN_NAME")
            val sqlType = columnResultSet.getInt("DATA_TYPE")
            val typeName = columnResultSet.getString("TYPE_NAME")
            val columnSize = columnResultSet.getInt("COLUMN_SIZE")
            val nullable = columnResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable
            val defaultValue = columnResultSet.getString("COLUMN_DEF")
            val autoIncrement = columnResultSet.getString("IS_AUTOINCREMENT")?.equals("YES", true) ?: false
            
            columns.add(ColumnMetadata(
                name = columnName,
                sqlType = sqlType,
                typeName = typeName,
                size = columnSize,
                nullable = nullable,
                autoIncrement = autoIncrement,
                defaultValue = defaultValue
            ))
        }
        
        columnResultSet.close()
        return columns
    }
    
    /**
     * Извлекает метаданные внешних ключей
     */
    private fun extractForeignKeyMetadata(metaData: DatabaseMetaData, schema: String?, tableName: String): List<ForeignKeyMetadata> {
        val foreignKeys = mutableListOf<ForeignKeyMetadata>()
        
        try {
            val fkResultSet = metaData.getImportedKeys(null, schema, tableName)
            
            while (fkResultSet.next()) {
                val columnName = fkResultSet.getString("FKCOLUMN_NAME")
                val referencedTable = fkResultSet.getString("PKTABLE_NAME")
                val referencedColumn = fkResultSet.getString("PKCOLUMN_NAME")
                val constraintName = fkResultSet.getString("FK_NAME") ?: "FK_${tableName}_${columnName}"
                
                foreignKeys.add(ForeignKeyMetadata(
                    columnName = columnName,
                    referencedTable = referencedTable,
                    referencedColumn = referencedColumn,
                    constraintName = constraintName
                ))
            }
            
            fkResultSet.close()
            
        } catch (e: Exception) {
            println("Предупреждение: не удалось извлечь внешние ключи для таблицы $tableName: ${e.message}")
        }
        
        return foreignKeys
    }
    
    /**
     * Извлекает метаданные первичных ключей
     */
    private fun extractPrimaryKeyMetadata(metaData: DatabaseMetaData, schema: String?, tableName: String): List<String> {
        val primaryKeys = mutableListOf<String>()
        
        try {
            val pkResultSet = metaData.getPrimaryKeys(null, schema, tableName)
            
            while (pkResultSet.next()) {
                val columnName = pkResultSet.getString("COLUMN_NAME")
                primaryKeys.add(columnName)
            }
            
            pkResultSet.close()
            
        } catch (e: Exception) {
            println("Предупреждение: не удалось извлечь первичные ключи для таблицы $tableName: ${e.message}")
        }
        
        return primaryKeys
    }
    
    /**
     * Проверяет, является ли база данных Apache Ignite
     */
    private fun isIgniteDatabase(connection: Connection): Boolean {
        return try {
            connection.metaData.databaseProductName.contains("Ignite", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Проверяет, является ли база данных PostgreSQL
     */
    private fun isPostgreSQLDatabase(connection: Connection): Boolean {
        return try {
            connection.metaData.databaseProductName.contains("PostgreSQL", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Проверяет, является ли таблица системной
     */
    private fun isSystemTable(tableName: String, connection: Connection): Boolean {
        val lowerTableName = tableName.lowercase()
        
        // Общие системные таблицы
        if (lowerTableName.startsWith("sys") || 
            lowerTableName.startsWith("information_schema") ||
            lowerTableName.startsWith("pg_")) {
            return true
        }
        
        // Системные таблицы Ignite
        if (isIgniteDatabase(connection)) {
            return lowerTableName.startsWith("ignite") ||
                   lowerTableName.contains("_key") ||
                   lowerTableName.contains("_val")
        }
        
        return false
    }
}
