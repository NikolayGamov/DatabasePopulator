
package com.databasepopulator.database

import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.TableMetadata
import java.sql.Connection
import java.sql.Types
import java.util.*

/**
 * Провайдер для Apache Ignite
 */
class IgniteProvider : DatabaseProvider() {
    
    override fun supports(connection: Connection): Boolean {
        return try {
            connection.metaData.databaseProductName.contains("Ignite", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getDefaultSchema(): String? = "PUBLIC"
    
    override fun extractUserDefinedTypes(connection: Connection, schema: String?): Map<String, UserDefinedType> {
        val userDefinedTypes = mutableMapOf<String, UserDefinedType>()
        
        try {
            // Ignite поддерживает пользовательские типы через Java классы
            // Попробуем извлечь информацию о пользовательских типах из системных таблиц
            val query = """
                SELECT COLUMN_NAME, TYPE_NAME, DATA_TYPE 
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_SCHEMA = ? 
                AND DATA_TYPE NOT IN (${Types.VARCHAR}, ${Types.INTEGER}, ${Types.BIGINT}, 
                                     ${Types.DECIMAL}, ${Types.BOOLEAN}, ${Types.DATE}, 
                                     ${Types.TIMESTAMP}, ${Types.DOUBLE}, ${Types.FLOAT})
            """.trimIndent()
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, schema ?: "PUBLIC")
                val rs = stmt.executeQuery()
                
                val customTypes = mutableSetOf<String>()
                while (rs.next()) {
                    val typeName = rs.getString("TYPE_NAME")
                    if (!isStandardIgniteType(typeName)) {
                        customTypes.add(typeName)
                    }
                }
                
                // Создаем базовые определения для найденных типов
                customTypes.forEach { typeName ->
                    userDefinedTypes[typeName] = UserDefinedType(
                        name = typeName,
                        category = "o", // other
                        enumValues = null,
                        compositeFields = null
                    )
                }
            }
            
        } catch (e: Exception) {
            println("Предупреждение: не удалось извлечь пользовательские типы Ignite: ${e.message}")
        }
        
        return userDefinedTypes
    }
    
    private fun isStandardIgniteType(typeName: String): Boolean {
        val standardTypes = setOf(
            "VARCHAR", "CHAR", "INTEGER", "INT", "BIGINT", "LONG",
            "DECIMAL", "NUMERIC", "DOUBLE", "FLOAT", "REAL",
            "BOOLEAN", "DATE", "TIME", "TIMESTAMP",
            "BINARY", "VARBINARY", "UUID", "ARRAY", "GEOMETRY"
        )
        return standardTypes.contains(typeName.uppercase())
    }
    
    override fun isJsonType(typeName: String): Boolean {
        // Ignite не имеет встроенного JSON типа, но можно использовать VARCHAR для JSON
        return typeName.lowercase().contains("json") || 
               typeName.lowercase() == "varchar" && typeName.lowercase().contains("json")
    }
    
    override fun isArrayType(typeName: String): Boolean {
        // Ignite поддерживает массивы через тип ARRAY или суффикс ARRAY
        return typeName.uppercase() == "ARRAY" || 
               typeName.uppercase().contains("ARRAY") ||
               typeName.contains("[]")
    }
    
    override fun isUuidType(typeName: String): Boolean {
        return typeName.uppercase() == "UUID"
    }
    
    override fun isUserDefinedType(typeName: String, userDefinedTypes: Map<String, UserDefinedType>): Boolean {
        return userDefinedTypes.containsKey(typeName) || !isStandardIgniteType(typeName)
    }
    
    override fun getEnumValues(connection: Connection, typeName: String): List<String>? {
        // Ignite не поддерживает enum типы на уровне БД
        // Можно реализовать через CHECK constraints, но это сложно извлечь
        return null
    }
    
    override fun getCompositeTypeFields(typeName: String, userDefinedTypes: Map<String, UserDefinedType>): List<CompositeTypeField>? {
        return userDefinedTypes[typeName]?.compositeFields
    }
    
    override fun getArrayElementType(typeName: String): String? {
        return when {
            typeName.contains("[]") -> typeName.replace("[]", "")
            typeName.uppercase().startsWith("ARRAY") -> {
                // Попробуем извлечь тип из ARRAY(type)
                val match = Regex("ARRAY\\((.+)\\)", RegexOption.IGNORE_CASE).find(typeName)
                match?.groupValues?.get(1)
            }
            else -> null
        }
    }
    
    override fun createInsertStatement(table: TableMetadata): String {
        val columns = table.columns.filter { !it.autoIncrement }
        val columnNames = columns.joinToString(", ") { "\"${it.name}\"" } // Экранируем имена колонок
        val placeholders = columns.joinToString(", ") { "?" }
        
        return "INSERT INTO \"${table.name}\" ($columnNames) VALUES ($placeholders)"
    }
    
    override fun supportsCopyOperations(): Boolean = false
    
    override fun createCopyStatement(table: TableMetadata): String? = null
    
    override fun formatCopyValue(value: Any?, column: ColumnMetadata): String {
        // Не используется, так как Ignite не поддерживает COPY
        return value?.toString() ?: ""
    }
}
