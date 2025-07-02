
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
    
    // Кэш пользовательских типов
    private val userDefinedTypes = mutableMapOf<String, UserDefinedType>()
    
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
            
            // Извлекаем пользовательские типы данных
            extractUserDefinedTypes(connection, schema)
            
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
            
            // Проверяем, является ли тип пользовательским
            val userDefinedType = userDefinedTypes[typeName]
            val isUserDefinedType = userDefinedType != null
            val enumValues = userDefinedType?.enumValues ?: emptyList()
            val compositeTypeFields = userDefinedType?.compositeFields ?: emptyList()
            
            columns.add(ColumnMetadata(
                name = columnName,
                sqlType = sqlType,
                typeName = typeName,
                size = columnSize,
                nullable = nullable,
                autoIncrement = autoIncrement,
                defaultValue = defaultValue,
                isUserDefinedType = isUserDefinedType,
                enumValues = enumValues,
                compositeTypeFields = compositeTypeFields
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
     * Извлекает пользовательские типы данных
     */
    private fun extractUserDefinedTypes(connection: Connection, schema: String?) {
        try {
            if (isPostgreSQLDatabase(connection)) {
                extractPostgreSQLUserDefinedTypes(connection, schema)
            } else if (isIgniteDatabase(connection)) {
                // Ignite может иметь свои пользовательские типы
                extractIgniteUserDefinedTypes(connection, schema)
            }
            
            println("Извлечено ${userDefinedTypes.size} пользовательских типов")
        } catch (e: Exception) {
            println("Предупреждение: не удалось извлечь пользовательские типы: ${e.message}")
        }
    }
    
    /**
     * Извлекает пользовательские типы PostgreSQL
     */
    private fun extractPostgreSQLUserDefinedTypes(connection: Connection, schema: String?) {
        // Извлекаем ENUM типы
        val enumQuery = """
            SELECT t.typname, e.enumlabel
            FROM pg_type t 
            JOIN pg_enum e ON t.oid = e.enumtypid 
            WHERE t.typnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
            ORDER BY t.typname, e.enumsortorder
        """.trimIndent()
        
        connection.prepareStatement(enumQuery).use { stmt ->
            stmt.setString(1, schema ?: "public")
            val rs = stmt.executeQuery()
            
            val enumMap = mutableMapOf<String, MutableList<String>>()
            while (rs.next()) {
                val typeName = rs.getString("typname")
                val enumLabel = rs.getString("enumlabel")
                enumMap.getOrPut(typeName) { mutableListOf() }.add(enumLabel)
            }
            
            enumMap.forEach { (typeName, values) ->
                userDefinedTypes[typeName] = UserDefinedType(
                    name = typeName,
                    category = "e",
                    enumValues = values
                )
            }
        }
        
        // Извлекаем составные типы
        val compositeQuery = """
            SELECT t.typname, a.attname, a.atttypid, pt.typname as field_type
            FROM pg_type t
            JOIN pg_class c ON c.reltype = t.oid
            JOIN pg_attribute a ON a.attrelid = c.oid
            JOIN pg_type pt ON pt.oid = a.atttypid
            WHERE t.typnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
            AND c.relkind = 'c' AND a.attnum > 0
            ORDER BY t.typname, a.attnum
        """.trimIndent()
        
        connection.prepareStatement(compositeQuery).use { stmt ->
            stmt.setString(1, schema ?: "public")
            val rs = stmt.executeQuery()
            
            val compositeMap = mutableMapOf<String, MutableList<CompositeTypeField>>()
            while (rs.next()) {
                val typeName = rs.getString("typname")
                val fieldName = rs.getString("attname")
                val fieldType = rs.getString("field_type")
                val fieldTypeId = rs.getInt("atttypid")
                
                compositeMap.getOrPut(typeName) { mutableListOf() }.add(
                    CompositeTypeField(fieldName, fieldType, fieldTypeId)
                )
            }
            
            compositeMap.forEach { (typeName, fields) ->
                userDefinedTypes[typeName] = UserDefinedType(
                    name = typeName,
                    category = "c",
                    compositeFields = fields
                )
            }
        }
    }
    
    /**
     * Извлекает пользовательские типы Ignite (заглушка)
     */
    private fun extractIgniteUserDefinedTypes(connection: Connection, schema: String?) {
        // Ignite пока не имеет полной поддержки пользовательских типов
        // Но может быть расширено в будущем
        println("Ignite: пользовательские типы пока не поддерживаются")
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
