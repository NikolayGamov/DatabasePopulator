
package com.databasepopulator.core

/**
 * Метаданные таблицы
 */
data class TableMetadata(
    val name: String,
    val columns: List<ColumnMetadata>,
    val primaryKeys: List<String>,
    val foreignKeys: List<ForeignKeyMetadata>,
    val uniqueConstraints: List<List<String>>
)

/**
 * Метаданные колонки
 */
data class ColumnMetadata(
    val name: String,
    val type: String,
    val sqlType: Int,
    val size: Int,
    val nullable: Boolean,
    val autoIncrement: Boolean,
    val defaultValue: String?
)

/**
 * Метаданные внешнего ключа
 */
data class ForeignKeyMetadata(
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String
)
