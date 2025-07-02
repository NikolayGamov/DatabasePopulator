
package com.databasepopulator.generator

import com.databasepopulator.config.PopulatorConfig
import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.TableMetadata
import com.databasepopulator.core.UserDefinedType
import com.databasepopulator.core.CompositeTypeField
import com.github.javafaker.Faker
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import java.io.StringReader
import java.sql.Connection
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
    
    // Кэш для значений связанных полей
    private val fieldRelationCache = mutableMapOf<String, MutableList<Any>>()
    
    init {
        // Инициализируем кэш для связанных полей
        config.fieldRelations.forEach { relation ->
            relation.sourceFields.forEach { field ->
                fieldRelationCache[field] = mutableListOf()
            }
            relation.targetFields.forEach { field ->
                fieldRelationCache[field] = mutableListOf()
            }
        }
        println("Инициализирован кэш для ${fieldRelationCache.size} связанных полей")
    }
    
    /**
     * Наполняет таблицу синтетическими данными
     */
    fun populateTable(connection: Connection, table: TableMetadata, recordCount: Int) {
        if (recordCount <= 0) return
        
        println("Генерация $recordCount записей для таблицы ${table.name}...")
        
        // Проверяем, поддерживает ли драйвер COPY
        if (connection.isWrapperFor(BaseConnection::class.java)) {
            populateTableWithCopy(connection, table, recordCount)
        } else {
            // Fallback на batch insert для других БД
            populateTableWithBatch(connection, table, recordCount)
        }
    }
    
    /**
     * Наполняет таблицу используя COPY (PostgreSQL)
     */
    private fun populateTableWithCopy(connection: Connection, table: TableMetadata, recordCount: Int) {
        val columns = table.columns.filter { !it.autoIncrement }
        
        // Создаем CSV данные в памяти
        val csvData = StringBuilder()
        
        repeat(recordCount) { recordIndex ->
            val values = mutableListOf<String>()
            
            columns.forEach { column ->
                val value = generateColumnValue(table.name, column, recordIndex)
                values.add(formatValueForCopy(value, column))
            }
            
            csvData.append(values.joinToString("\t")).append("\n")
        }
        
        // Используем COPY для вставки данных
        val copyManager = CopyManager(connection.unwrap(BaseConnection::class.java))
        val columnNames = columns.joinToString(", ") { it.name }
        val copyStatement = "COPY ${table.name} ($columnNames) FROM STDIN WITH (FORMAT CSV, DELIMITER E'\\t', NULL 'NULL')"
        
        val reader = StringReader(csvData.toString())
        
        try {
            val rowsInserted = copyManager.copyIn(copyStatement, reader)
            println("Вставлено $rowsInserted записей через COPY")
            connection.commit()
        } catch (e: Exception) {
            println("Ошибка при COPY операции: ${e.message}")
            connection.rollback()
            throw e
        }
    }
    
    /**
     * Наполняет таблицу используя batch insert (fallback)
     */
    private fun populateTableWithBatch(connection: Connection, table: TableMetadata, recordCount: Int) {
        println("Используется batch insert (COPY недоступен)")
        
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
     * Форматирует значение для COPY команды
     */
    private fun formatValueForCopy(value: Any?, column: ColumnMetadata): String {
        return when {
            value == null -> "NULL"
            value is String -> value.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
            value is Boolean -> if (value) "t" else "f"
            else -> value.toString()
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
     * Генерирует данные для одной записи (для batch insert)
     */
    private fun generateRecordData(stmt: java.sql.PreparedStatement, table: TableMetadata, recordIndex: Int) {
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
        val fieldKey = "$tableName.${column.name}"
        
        // Проверяем, есть ли связь с другими полями
        val fieldRelation = config.fieldRelations.find { relation ->
            relation.sourceFields.contains(fieldKey) || relation.targetFields.contains(fieldKey)
        }
        
        if (fieldRelation != null) {
            return generateRelatedValue(fieldKey, fieldRelation, column, recordIndex)
        }
        
        // Проверяем, есть ли специальное правило генерации
        val generationRule = config.generationRules[fieldKey]
        
        if (generationRule != null) {
            return generateValueByRule(generationRule, column)
        }
        
        // Генерируем значение по типу данных
        return generateValueByType(column, recordIndex)
    }
    
    /**
     * Генерирует значение для связанного поля
     */
    private fun generateRelatedValue(fieldKey: String, relation: FieldRelation, column: ColumnMetadata, recordIndex: Int): Any? {
        when (relation.type) {
            RelationType.SAME_VALUES -> {
                // Для SAME_VALUES используем общий пул значений
                val sourceField = relation.sourceFields.first()
                val cache = fieldRelationCache[sourceField] ?: mutableListOf()
                
                return if (cache.isNotEmpty() && random.nextDouble() < 0.8) {
                    // 80% шанс использовать существующее значение
                    cache.random()
                } else {
                    // 20% шанс сгенерировать новое значение
                    val newValue = generateNewValueForRelation(column, recordIndex)
                    if (newValue != null) {
                        // Добавляем значение во все связанные кэши
                        relation.sourceFields.forEach { field ->
                            fieldRelationCache[field]?.add(newValue)
                        }
                        relation.targetFields.forEach { field ->
                            fieldRelationCache[field]?.add(newValue)
                        }
                    }
                    newValue
                }
            }
            
            RelationType.DISJOINT_UNION -> {
                // Для DISJOINT_UNION каждое поле имеет свой набор значений
                val cache = fieldRelationCache[fieldKey] ?: mutableListOf()
                
                return if (cache.isNotEmpty() && random.nextDouble() < 0.7) {
                    // 70% шанс использовать существующее значение
                    cache.random()
                } else {
                    // 30% шанс сгенерировать новое значение
                    val newValue = generateNewValueForRelation(column, recordIndex)
                    if (newValue != null) {
                        cache.add(newValue)
                    }
                    newValue
                }
            }
        }
    }
    
    /**
     * Генерирует новое значение для связанного поля
     */
    private fun generateNewValueForRelation(column: ColumnMetadata, recordIndex: Int): Any? {
        // Используем специальные правила генерации если есть
        val generationRule = config.generationRules.values.find { rule ->
            rule.type.lowercase() in listOf("uuid", "email", "name", "phone")
        }
        
        return if (generationRule != null) {
            generateValueByRule(generationRule, column)
        } else {
            generateValueByType(column, recordIndex)
        }
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
        if (column.nullable && random.nextDouble() < 0.1) {
            return null // 10% шанс на null для nullable колонок
        }
        
        // Обработка пользовательских типов
        if (column.isUserDefinedType) {
            return generateUserDefinedTypeValue(column)
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
     * Генерирует значение для пользовательского типа
     */
    private fun generateUserDefinedTypeValue(column: ColumnMetadata): Any? {
        return when {
            // ENUM тип
            column.enumValues.isNotEmpty() -> {
                column.enumValues.random()
            }
            
            // Составной тип
            column.compositeTypeFields.isNotEmpty() -> {
                generateCompositeTypeValue(column.compositeTypeFields)
            }
            
            else -> {
                // Fallback для неизвестных пользовательских типов
                println("Предупреждение: неизвестный пользовательский тип ${column.typeName}")
                "UDT_${random.nextInt(1000)}"
            }
        }
    }
    
    /**
     * Генерирует значение для составного типа
     */
    private fun generateCompositeTypeValue(fields: List<CompositeTypeField>): String {
        val values = fields.map { field ->
            when (field.typeName.lowercase()) {
                "text", "varchar", "char" -> "\"${faker.lorem().word()}\""
                "integer", "int", "int4" -> random.nextInt(1000).toString()
                "bigint", "int8" -> random.nextLong().toString()
                "boolean", "bool" -> random.nextBoolean().toString()
                "numeric", "decimal" -> (random.nextDouble() * 1000).toString()
                "timestamp", "timestamptz" -> "\"${java.time.LocalDateTime.now()}\""
                "date" -> "\"${java.time.LocalDate.now()}\""
                else -> "\"${faker.lorem().word()}\""
            }
        }
        
        // Возвращаем в формате PostgreSQL ROW constructor
        return "(${values.joinToString(",")})"
    }
    
    /**
     * Устанавливает значение параметра в PreparedStatement
     */
    private fun setParameterValue(stmt: java.sql.PreparedStatement, paramIndex: Int, value: Any?, column: ColumnMetadata) {
        when {
            value == null -> stmt.setNull(paramIndex, column.sqlType)
            value is String -> {
                if (column.isUserDefinedType) {
                    // Для пользовательских типов используем setObject
                    stmt.setObject(paramIndex, value)
                } else {
                    stmt.setString(paramIndex, value)
                }
            }
            value is Int -> stmt.setInt(paramIndex, value)
            value is Long -> stmt.setLong(paramIndex, value)
            value is Double -> stmt.setDouble(paramIndex, value)
            value is Float -> stmt.setFloat(paramIndex, value)
            value is Boolean -> stmt.setBoolean(paramIndex, value)
            value is java.sql.Date -> stmt.setDate(paramIndex, value)
            value is java.sql.Timestamp -> stmt.setTimestamp(paramIndex, value)
            value is java.sql.Time -> stmt.setTime(paramIndex, value)
            value is java.math.BigDecimal -> stmt.setBigDecimal(paramIndex, value)
            else -> stmt.setObject(paramIndex, value)
        }
    }
}
