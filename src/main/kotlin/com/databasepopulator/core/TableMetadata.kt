
package com.databasepopulator.core

/**
 * Метаданные таблицы
 */
data class TableMetadata(
    val name: String,
    val columns: List<ColumnMetadata>,
    val foreignKeys: List<ForeignKeyMetadata> = emptyList(),
    val primaryKeys: List<String> = emptyList()
)

/**
 * Метаданные колонки
 */
data class ColumnMetadata(
    val name: String,
    val sqlType: Int,
    val typeName: String,
    val size: Int,
    val nullable: Boolean,
    val autoIncrement: Boolean = false,
    val defaultValue: String? = null
)

/**
 * Метаданные внешнего ключа
 */
data class ForeignKeyMetadata(
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String,
    val constraintName: String
)
