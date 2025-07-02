
package com.databasepopulator.generator

import com.databasepopulator.config.PopulatorConfig
import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.TableMetadata
import com.github.javafaker.Faker
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Генератор синтетических данных для таблиц
 */
class DataGenerator(private val config: PopulatorConfig) {
    
    private val faker = Faker(Locale("ru"))
    private val random = Random()
    
    /**
     * Наполняет таблицу синтетическими данными
     */
    fun populateTable(connection: Connection, table: TableMetadata, recordCount: Int) {
        if (recordCount <= 0) return
        
        // Подготавливаем SQL для вставки
        val insertSql = buildInsertStatement(table)
        val preparedStatement = connection.prepareStatement(insertSql)
        
        try {
            val batchSize = 1000
            var currentBatch = 0
            
            repeat(recordCount) { recordIndex ->
                // Генерируем данные для записи
                generateRecordData(preparedStatement, table, recordIndex)
                preparedStatement.addBatch()
                currentBatch++
                
                // Выполняем batch когда достигаем размера пакета
                if (currentBatch >= batchSize) {
                    preparedStatement.executeBatch()
                    connection.commit()
                    currentBatch = 0
                }
            }
            
            // Выполняем оставшиеся записи
            if (currentBatch > 0) {
                preparedStatement.executeBatch()
                connection.commit()
            }
            
        } finally {
            preparedStatement.close()
        }
    }
    
    /**
     * Строит SQL INSERT statement для таблицы
     */
    private fun buildInsertStatement(table: TableMetadata): String {
        val columns = table.columns.filter { !it.autoIncrement }
        val columnNames = columns.joinToString(", ") { it.name }
        val placeholders = columns.joinToString(", ") { "?" }
        
        return "INSERT INTO ${table.name} ($columnNames) VALUES ($placeholders)"
    }
    
    /**
     * Генерирует данные для одной записи
     */
    private fun generateRecordData(stmt: PreparedStatement, table: TableMetadata, recordIndex: Int) {
        val columns = table.columns.filter { !it.autoIncrement }
        
        columns.forEachIndexed { index, column ->
            val value = generateColumnValue(table.name, column, recordIndex)
            setParameterValue(stmt, index + 1, value, column)
        }
    }
    
    /**
     * Генерирует значение для конкретной колонки
     */
    private fun generateColumnValue(tableName: String, column: ColumnMetadata, recordIndex: Int): Any? {
        // Проверяем, есть ли специальное правило генерации
        val ruleKey = "$tableName.${column.name}"
        val generationRule = config.generationRules[ruleKey]
        
        if (generationRule != null) {
            return generateValueByRule(generationRule, column)
        }
        
        // Генерируем значение по типу данных
        return generateValueByType(column, recordIndex)
    }
    
    /**
     * Генерирует значение по правилу из конфигурации
     */
    private fun generateValueByRule(rule: com.databasepopulator.config.GenerationRule, column: ColumnMetadata): Any? {
        return when (rule.type.lowercase()) {
            "name" -> faker.name().fullName()
            "firstname" -> faker.name().firstName()
            "lastname" -> faker.name().lastName()
            "email" -> faker.internet().emailAddress()
            "phone" -> faker.phoneNumber().phoneNumber()
            "address" -> faker.address().fullAddress()
            "company" -> faker.company().name()
            "uuid" -> UUID.randomUUID().toString()
            "constant" -> rule.parameters["value"]
            "sequence" -> rule.parameters["start"]?.toIntOrNull()?.let { start ->
                start + random.nextInt(1000000)
            }
            else -> generateValueByType(column, 0)
        }
    }
    
    /**
     * Генерирует значение по типу данных колонки
     */
    private fun generateValueByType(column: ColumnMetadata, recordIndex: Int): Any? {
        if (!column.nullable && random.nextDouble() < 0.1) {
            return null // 10% шанс на null для nullable колонок
        }
        
        return when (column.sqlType) {
            Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR -> {
                generateStringValue(column)
            }
            Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                random.nextInt(100000)
            }
            Types.BIGINT -> {
                random.nextLong().coerceIn(0, Long.MAX_VALUE / 2)
            }
            Types.DECIMAL, Types.NUMERIC -> {
                (random.nextDouble() * 10000).toBigDecimal()
            }
            Types.REAL, Types.FLOAT -> {
                random.nextFloat() * 1000
            }
            Types.DOUBLE -> {
                random.nextDouble() * 1000
            }
            Types.BOOLEAN, Types.BIT -> {
                random.nextBoolean()
            }
            Types.DATE -> {
                java.sql.Date.valueOf(LocalDate.now().minusDays(random.nextLong(3650)))
            }
            Types.TIMESTAMP -> {
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(random.nextLong(365)))
            }
            Types.TIME -> {
                java.sql.Time.valueOf(String.format("%02d:%02d:%02d", 
                    random.nextInt(24), random.nextInt(60), random.nextInt(60)))
            }
            else -> {
                "Generated_${recordIndex}"
            }
        }
    }
    
    /**
     * Генерирует строковое значение
     */
    private fun generateStringValue(column: ColumnMetadata): String {
        val maxLength = minOf(column.size, 255)
        
        return when {
            column.name.contains("email", ignoreCase = true) -> faker.internet().emailAddress()
            column.name.contains("name", ignoreCase = true) -> faker.name().fullName()
            column.name.contains("phone", ignoreCase = true) -> faker.phoneNumber().phoneNumber()
            column.name.contains("address", ignoreCase = true) -> faker.address().streetAddress()
            column.name.contains("city", ignoreCase = true) -> faker.address().city()
            column.name.contains("company", ignoreCase = true) -> faker.company().name()
            else -> faker.lorem().characters(random.nextInt(maxLength) + 1)
        }.take(maxLength)
    }
    
    /**
     * Устанавливает значение параметра в PreparedStatement
     */
    private fun setParameterValue(stmt: PreparedStatement, paramIndex: Int, value: Any?, column: ColumnMetadata) {
        when {
            value == null -> stmt.setNull(paramIndex, column.sqlType)
            value is String -> stmt.setString(paramIndex, value)
            value is Int -> stmt.setInt(paramIndex, value)
            value is Long -> stmt.setLong(paramIndex, value)
            value is Double -> stmt.setDouble(paramIndex, value)
            value is Float -> stmt.setFloat(paramIndex, value)
            value is Boolean -> stmt.setBoolean(paramIndex, value)
            value is java.sql.Date -> stmt.setDate(paramIndex, value)
            value is java.sql.Timestamp -> stmt.setTimestamp(paramIndex, value)
            value is java.sql.Time -> stmt.setTime(paramIndex, value)
            value is java.math.BigDecimal -> stmt.setBigDecimal(paramIndex, value)
            else -> stmt.setString(paramIndex, value.toString())
        }
    }
}
