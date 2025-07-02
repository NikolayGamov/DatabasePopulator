package com.databasepopulator.generator

import com.databasepopulator.config.PopulatorConfig
import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.TableMetadata
import com.databasepopulator.core.UserDefinedType
import com.databasepopulator.core.CompositeTypeField
import com.github.javafaker.Faker
import com.mifmif.common.regex.Generex
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import java.io.StringReader
import java.sql.Connection
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Генератор синтетических данных для таблиц
 */
class DataGenerator(private val config: PopulatorConfig) {

    private val faker = Faker(Locale("ru"))

    // Thread-safe кэш для значений связанных полей
    private val fieldRelationCache = ConcurrentHashMap<String, MutableList<Any>>()

    // Счетчики для генерации уникальных значений
    private val sequenceCounters = ConcurrentHashMap<String, AtomicInteger>()

    init {
        // Инициализируем кэш для связанных полей
        config.fieldRelations.forEach { relation ->
            relation.sourceFields.forEach { field ->
                fieldRelationCache[field] = Collections.synchronizedList(mutableListOf())
            }
            relation.targetFields.forEach { field ->
                fieldRelationCache[field] = Collections.synchronizedList(mutableListOf())
            }
        }
        println("Инициализирован потокобезопасный кэш для ${fieldRelationCache.size} связанных полей")
    }

    /**
     * Наполняет таблицу синтетическими данными
     */
    fun populateTable(connection: Connection, table: TableMetadata, recordCount: Int) {
        if (recordCount <= 0) return

        println("Генерация $recordCount записей для таблицы ${table.name} (Thread: ${Thread.currentThread().name})")

        // Проверяем, поддерживает ли драйвер COPY
        if (recordCount > 1000 && supportsCopyOperations(table)) {
            populateTableWithCopy(connection, table, recordCount)
        } else {
            // Fallback на batch insert для других БД
            populateTableWithBatch(connection, table, recordCount)
        }
    }

    /**
     * Использует COPY для быстрой вставки в PostgreSQL
     */
    private fun populateTableWithCopy(connection: Connection, table: TableMetadata, recordCount: Int) {
        val provider = table.databaseProvider

        if (provider == null || !provider.supportsCopyOperations()) {
            throw IllegalArgumentException("БД не поддерживает COPY операции")
        }

        val copyStatement = provider.createCopyStatement(table)
            ?: throw IllegalArgumentException("Не удалось создать COPY statement")

        val columns = table.columns.filter { !it.autoIncrement }

        // Создаем CSV данные в памяти
        val csvData = StringBuilder()

        repeat(recordCount) { recordIndex ->
            val values = mutableListOf<String>()

            columns.forEach { column ->
                val value = generateColumnValue(table.name, column, recordIndex)
                val formattedValue = table.databaseProvider?.formatCopyValue(value, column) ?: formatValueForCopy(value, column)
                values.add(formattedValue)
            }

            csvData.append(values.joinToString("\t")).append("\n")
        }

        // Используем COPY для вставки данных
        val copyManager = CopyManager(connection.unwrap(BaseConnection::class.java))
        val reader = StringReader(csvData.toString())

        try {
            val rowsInserted = copyManager.copyIn(copyStatement, reader)
            println("Вставлено $rowsInserted записей через COPY в таблицу ${table.name}")
            connection.commit()
        } catch (e: Exception) {
            println("Ошибка при COPY операции для таблицы ${table.name}: ${e.message}")
            connection.rollback()
            throw e
        }
    }

    /**
     * Наполняет таблицу используя batch insert (fallback)
     */
    private fun populateTableWithBatch(connection: Connection, table: TableMetadata, recordCount: Int) {
        println("Используется batch insert для таблицы ${table.name} (COPY недоступен)")

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
            value is String -> {
                when (column.typeName.lowercase()) {
                    "jsonb", "json" -> {
                        // Для JSON экранируем кавычки и переносы строк
                        value.replace("\"", "\\\"")
                            .replace("\t", "\\t")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                    }
                    else -> {
                        if (column.sqlType == Types.ARRAY || column.typeName.contains("[]")) {
                            // Массивы передаем как есть
                            value
                        } else {
                            value.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
                        }
                    }
                }
            }
            value is Boolean -> if (value) "t" else "f"
            value is UUID -> value.toString()
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
     * Генерирует значение для конкретной колонки (thread-safe)
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
     * Генерирует значение для связанного поля (thread-safe)
     */
    private fun generateRelatedValue(fieldKey: String, relation: FieldRelation, column: ColumnMetadata, recordIndex: Int): Any? {
        when (relation.type) {
            RelationType.SAME_VALUES -> {
                // Для SAME_VALUES используем общий пул значений
                val sourceField = relation.sourceFields.first()
                val cache = fieldRelationCache[sourceField]

                return if (cache != null && cache.isNotEmpty() && ThreadLocalRandom.current().nextDouble() < 0.8) {
                    // 80% шанс использовать существующее значение (thread-safe)
                    synchronized(cache) {
                        if (cache.isNotEmpty()) cache.random() else null
                    }
                } else {
                    // 20% шанс сгенерировать новое значение
                    val newValue = generateNewValueForRelation(column, recordIndex)
                    if (newValue != null) {
                        // Добавляем значение во все связанные кэши (thread-safe)
                        relation.sourceFields.forEach { field ->
                            fieldRelationCache[field]?.let { fieldCache ->
                                synchronized(fieldCache) {
                                    fieldCache.add(newValue)
                                }
                            }
                        }
                        relation.targetFields.forEach { field ->
                            fieldRelationCache[field]?.let { fieldCache ->
                                synchronized(fieldCache) {
                                    fieldCache.add(newValue)
                                }
                            }
                        }
                    }
                    newValue
                }
            }

            RelationType.DISJOINT_UNION -> {
                // Для DISJOINT_UNION каждое поле имеет свой набор значений
                val cache = fieldRelationCache[fieldKey]

                return if (cache != null && cache.isNotEmpty() && ThreadLocalRandom.current().nextDouble() < 0.7) {
                    // 70% шанс использовать существующее значение (thread-safe)
                    synchronized(cache) {
                        if (cache.isNotEmpty()) cache.random() else null
                    }
                } else {
                    // 30% шанс сгенерировать новое значение
                    val newValue = generateNewValueForRelation(column, recordIndex)
                    if (newValue != null && cache != null) {
                        synchronized(cache) {
                            cache.add(newValue)
                        }
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
     * Генерирует значение по правилу из конфигурации (thread-safe)
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
            "sequence" -> {
                val start = rule.parameters["start"]?.toIntOrNull() ?: 1
                val counter = sequenceCounters.computeIfAbsent("${rule.type}_${start}") { AtomicInteger(start) }
                counter.getAndIncrement()
            }
            "regex" -> {
                val pattern = rule.parameters["pattern"]
                if (pattern != null) {
                    generateValueByRegex(pattern, column)
                } else {
                    println("Предупреждение: не указан параметр 'pattern' для regex правила")
                    generateValueByType(column, 0)
                }
            }
            else -> generateValueByType(column, 0)
        }
    }

    /**
     * Генерирует значение по regex-паттерну используя библиотеку Generex
     */
    private fun generateValueByRegex(pattern: String, column: ColumnMetadata): Any? {
        return try {
            val generex = Generex(pattern)
            val value = generex.random()

            // Проверяем ограничения по длине колонки
            if (column.size > 0 && value.length > column.size) {
                value.take(column.size)
            } else {
                value
            }
        } catch (e: Exception) {
            println("Ошибка генерации по regex '$pattern': ${e.message}")
            // Fallback на обычную генерацию
            generateValueByType(column, 0)
        }
    }

    /**
     * Генерирует значение по типу данных колонки (thread-safe)
     */
    private fun generateValueByType(column: ColumnMetadata, recordIndex: Int): Any? {
        if (column.nullable && ThreadLocalRandom.current().nextDouble() < 0.1) {
            return null // 10% шанс на null для nullable колонок
        }

        // Обработка пользовательских типов
        if (column.isUserDefinedType) {
            return generateUserDefinedTypeValue(column)
        }

        // Обработка специальных типов
        if (column.isJsonType) {
            return generateJsonValue(ThreadLocalRandom.current())
        }

        if (column.isArrayType) {
            return generateArrayValue(column, ThreadLocalRandom.current())
        }

        if (column.isUuidType) {
            return UUID.randomUUID()
        }

        val random = ThreadLocalRandom.current()

        return when (column.sqlType) {
            Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR -> {
                generateStringValue(column)
            }
            Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                random.nextInt(100000)
            }
            Types.BIGINT -> {
                random.nextLong(0, Long.MAX_VALUE / 2)
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
            Types.ARRAY -> {
                generateArrayValue(column, random)
            }
            Types.OTHER -> {
                // Обработка специальных типов PostgreSQL и Ignite
                when (column.typeName.lowercase()) {
                    "jsonb", "json" -> generateJsonValue(random)
                    "uuid" -> UUID.randomUUID()
                    else -> "Generated_${recordIndex}_${random.nextInt(1000)}"
                }
            }
            else -> {
                when (column.typeName.lowercase()) {
                    "jsonb", "json" -> generateJsonValue(random)
                    "uuid" -> UUID.randomUUID()
                    else -> "Generated_${recordIndex}_${random.nextInt(1000)}"
                }
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
            else -> faker.lorem().characters(ThreadLocalRandom.current().nextInt(maxLength) + 1)
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
                "UDT_${ThreadLocalRandom.current().nextInt(1000)}"
            }
        }
    }

    /**
     * Генерирует значение для составного типа
     */
    private fun generateCompositeTypeValue(fields: List<CompositeTypeField>): String {
        val random = ThreadLocalRandom.current()

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
     * Генерирует значение для JSON/JSONB колонки
     */
    private fun generateJsonValue(random: ThreadLocalRandom): String {
        val jsonTypes = listOf("user_profile", "settings", "metadata", "properties")
        val selectedType = jsonTypes.random()

        return when (selectedType) {
            "user_profile" -> """{
                "age": ${random.nextInt(18, 80)},
                "interests": ["${faker.hobby().activity()}", "${faker.hobby().activity()}"],
                "location": {
                    "city": "${faker.address().city()}",
                    "country": "${faker.address().country()}"
                },
                "is_verified": ${random.nextBoolean()}
            }""".trimIndent()

            "settings" -> """{
                "theme": "${listOf("dark", "light", "auto").random()}",
                "notifications": {
                    "email": ${random.nextBoolean()},
                    "push": ${random.nextBoolean()}
                },
                "language": "${listOf("ru", "en", "de", "fr").random()}"
            }""".trimIndent()

            "metadata" -> """{
                "created_by": "${faker.name().username()}",
                "tags": ["${faker.lorem().word()}", "${faker.lorem().word()}"],
                "version": "${random.nextInt(1, 10)}.${random.nextInt(0, 10)}.${random.nextInt(0, 10)}",
                "last_modified": "${java.time.LocalDateTime.now()}"
            }""".trimIndent()

            else -> """{
                "key1": "${faker.lorem().word()}",
                "key2": ${random.nextInt(1000)},
                "key3": ${random.nextBoolean()}
            }""".trimIndent()
        }
    }

    /**
     * Генерирует значение для массива
     */
    private fun generateArrayValue(column: ColumnMetadata, random: ThreadLocalRandom): String {
        val arraySize = random.nextInt(1, 6) // От 1 до 5 элементов

        return when {
            column.typeName.contains("text", ignoreCase = true) ||
            column.typeName.contains("varchar", ignoreCase = true) -> {
                val elements = (1..arraySize).map { "\"${faker.lorem().word()}\"" }
                "{${elements.joinToString(",")}}"
            }

            column.typeName.contains("integer", ignoreCase = true) ||
            column.typeName.contains("int", ignoreCase = true) -> {
                val elements = (1..arraySize).map { random.nextInt(1000) }
                "{${elements.joinToString(",")}}"
            }

            column.typeName.contains("boolean", ignoreCase = true) -> {
                val elements = (1..arraySize).map { random.nextBoolean() }
                "{${elements.joinToString(",")}}"
            }

            column.typeName.contains("decimal", ignoreCase = true) ||
            column.typeName.contains("numeric", ignoreCase = true) -> {
                val elements = (1..arraySize).map { (random.nextDouble() * 1000).toBigDecimal().setScale(2) }
                "{${elements.joinToString(",")}}"
            }

            column.typeName.contains("uuid", ignoreCase = true) -> {
                val elements = (1..arraySize).map { "\"${UUID.randomUUID()}\"" }
                "{${elements.joinToString(",")}}"
            }

            else -> {
                // Fallback для неизвестных типов массивов
                val elements = (1..arraySize).map { "\"element_$it\"" }
                "{${elements.joinToString(",")}}"
            }
        }
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
            value is UUID -> stmt.setObject(paramIndex, value)
            else -> {
                // Специальная обработка для JSONB и массивов
                when (column.typeName.lowercase()) {
                    "jsonb", "json" -> {
                        // Для PostgreSQL JSONB используем setObject с типом OTHER
                        stmt.setObject(paramIndex, value, Types.OTHER)
                    }
                    else -> {
                        if (column.sqlType == Types.ARRAY || column.typeName.contains("[]")) {
                            // Для массивов используем setObject
                            stmt.setObject(paramIndex, value)
                        } else {
                            stmt.setObject(paramIndex, value)
                        }
                    }
                }
            }
        }
    }

    /**
     * Проверяет, поддерживает ли БД COPY операции
     */
    private fun supportsCopyOperations(table: TableMetadata): Boolean {
        return table.databaseProvider?.supportsCopyOperations() ?: false
    }
}