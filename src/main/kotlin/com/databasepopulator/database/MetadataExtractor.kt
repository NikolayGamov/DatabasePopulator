package com.databasepopulator.database

import com.databasepopulator.core.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

/**
 * Класс для извлечения метаданных таблиц из базы данных
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
            // Получаем провайдер для данного типа БД
            val provider = DatabaseProviderFactory.getProvider(connection)
            val schema = provider.getDefaultSchema()

            println("Используемая схема: $schema")
            println("Тип БД: ${provider::class.simpleName}")

            // Извлекаем пользовательские типы данных
            val userDefinedTypes = provider.extractUserDefinedTypes(connection, schema)
            println("Извлечено ${userDefinedTypes.size} пользовательских типов")

            // Получаем список таблиц
            val tableResultSet = metaData.getTables(null, schema, "%", arrayOf("TABLE"))

            while (tableResultSet.next()) {
                val tableName = tableResultSet.getString("TABLE_NAME")

                // Пропускаем системные таблицы
                if (isSystemTable(tableName, provider)) {
                    continue
                }

                println("Обработка таблицы: $tableName")

                // Извлекаем колонки
                val columns = extractColumnMetadata(metaData, schema, tableName, provider, userDefinedTypes)

                // Извлекаем внешние ключи
                val foreignKeys = extractForeignKeyMetadata(metaData, schema, tableName)

                // Извлекаем первичные ключи
                val primaryKeys = extractPrimaryKeyMetadata(metaData, schema, tableName)

                tables.add(TableMetadata(
                    name = tableName,
                    columns = columns,
                    foreignKeys = foreignKeys,
                    primaryKeys = primaryKeys,
                    databaseProvider = provider
                ))
            }

            tableResultSet.close()

        } catch (e: Exception) {
            println("Ошибка при извлечении метаданных: ${e.message}")
            e.printStackTrace()
        }

        println("Извлечено метаданных для ${tables.size} таблиц")
        return tables
    }

    /**
     * Проверяет, является ли таблица системной
     */
    private fun isSystemTable(tableName: String, provider: DatabaseProvider): Boolean {
        val systemPrefixes = when (provider) {
            is PostgreSQLProvider -> listOf("pg_", "information_schema")
            is IgniteProvider -> listOf("sys", "information_schema", "SYS")
            else -> listOf("sys", "information_schema")
        }

        return systemPrefixes.any { tableName.startsWith(it, ignoreCase = true) }
    }

    /**
     * Извлекает метаданные колонок
     */
    private fun extractColumnMetadata(
        metaData: DatabaseMetaData, 
        schema: String?, 
        tableName: String,
        provider: DatabaseProvider,
        userDefinedTypes: Map<String, UserDefinedType>
    ): List<ColumnMetadata> {
        val columns = mutableListOf<ColumnMetadata>()
        val columnResultSet = metaData.getColumns(null, schema, tableName, "%")

        while (columnResultSet.next()) {
            val columnName = columnResultSet.getString("COLUMN_NAME")
            val sqlType = columnResultSet.getInt("DATA_TYPE")
            val typeName = columnResultSet.getString("TYPE_NAME")
            val columnSize = columnResultSet.getInt("COLUMN_SIZE")
            val nullable = columnResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable
            val autoIncrement = columnResultSet.getString("IS_AUTOINCREMENT") == "YES"
            val defaultValue = columnResultSet.getString("COLUMN_DEF")

            // Определяем специальные типы с помощью провайдера
            val isJsonType = provider.isJsonType(typeName)
            val isArrayType = provider.isArrayType(typeName)
            val isUuidType = provider.isUuidType(typeName)
            val isUserDefinedType = provider.isUserDefinedType(typeName, userDefinedTypes)

            // Получаем дополнительную информацию для специальных типов
            val enumValues = if (isUserDefinedType) {
                userDefinedTypes[typeName]?.enumValues ?: provider.getEnumValues(connection, typeName)
            } else null

            val compositeTypeFields = if (isUserDefinedType) {
                provider.getCompositeTypeFields(typeName, userDefinedTypes)
            } else null

            val arrayElementType = if (isArrayType) {
                provider.getArrayElementType(typeName)
            } else null

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
                compositeTypeFields = compositeTypeFields,
                isArrayType = isArrayType,
                isJsonType = isJsonType,
                isUuidType = isUuidType,
                arrayElementType = arrayElementType
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
}