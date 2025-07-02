package com.databasepopulator.core

/**
 * Метаданные таблицы
 */
data class TableMetadata(
    val name: String,
    val columns: List<ColumnMetadata>,
    val foreignKeys: List<ForeignKeyMetadata>,
    val primaryKeys: List<String>,
    val databaseProvider: com.databasepopulator.database.DatabaseProvider? = null
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
    val defaultValue: String? = null,
    val isUserDefinedType: Boolean = false,
    val enumValues: List<String> = emptyList(),
    val compositeTypeFields: List<CompositeTypeField> = emptyList(),
    val isArrayType: Boolean = false,
    val isJsonType: Boolean = false,
    val isUuidType: Boolean = false,
    val arrayElementType: String? = null
)

/**
 * Поле составного типа
 */
data class CompositeTypeField(
    val name: String,
    val typeName: String,
    val sqlType: Int
)

/**
 * Метаданные пользовательского типа
 */
data class UserDefinedType(
    val name: String,
    val category: String, // 'e' для enum, 'c' для composite
    val enumValues: List<String> = emptyList(),
    val compositeFields: List<CompositeTypeField> = emptyList()
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