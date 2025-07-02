
package com.databasepopulator.config

/**
 * Основная конфигурация популятора
 */
data class PopulatorConfig(
    val databases: List<DatabaseConfig>,
    val defaultRecordCount: Int = 1000,
    val tableSettings: List<TableSetting> = emptyList(),
    val fieldRelations: List<FieldRelation> = emptyList(),
    val generationRules: Map<String, GenerationRule> = emptyMap()
)

/**
 * Конфигурация подключения к базе данных
 */
data class DatabaseConfig(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val driver: String,
    val defaultRecordCount: Int = 1000
)

/**
 * Настройки для конкретной таблицы
 */
data class TableSetting(
    val databaseName: String,
    val tableName: String,
    val recordCount: Int? = null
)

/**
 * Связь между полями разных таблиц
 */
data class FieldRelation(
    val type: RelationType,
    val sourceFields: List<String>, // формат: "database.table.column"
    val targetFields: List<String>  // формат: "database.table.column"
)

/**
 * Типы связей между полями
 */
enum class RelationType {
    SAME_VALUES,    // одинаковые наборы значений
    DISJOINT_UNION  // сумма непересекающихся наборов
}

/**
 * Правило генерации данных для поля
 */
data class GenerationRule(
    val type: String, // тип генератора (name, email, number, date, etc.)
    val parameters: Map<String, String> = emptyMap() // дополнительные параметры
)
