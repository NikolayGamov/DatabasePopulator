
package com.databasepopulator.database

import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.TableMetadata
import java.sql.Connection
import java.sql.Types
import java.util.*

/**
 * Провайдер для PostgreSQL
 */
class PostgreSQLProvider : DatabaseProvider() {
    
    override fun supports(connection: Connection): Boolean {
        return try {
            connection.metaData.databaseProductName.contains("PostgreSQL", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getDefaultSchema(): String? = "public"
    
    override fun extractUserDefinedTypes(connection: Connection, schema: String?): Map<String, UserDefinedType> {
        val userDefinedTypes = mutableMapOf<String, UserDefinedType>()
        
        try {
            // Извлекаем enum типы
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
            
            // Извлекаем composite типы
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
            
        } catch (e: Exception) {
            println("Предупреждение: не удалось извлечь пользовательские типы PostgreSQL: ${e.message}")
        }
        
        return userDefinedTypes
    }
    
    override fun isJsonType(typeName: String): Boolean {
        return typeName.lowercase() in setOf("json", "jsonb")
    }
    
    override fun isArrayType(typeName: String): Boolean {
        return typeName.contains("[]") || typeName.startsWith("_")
    }
    
    override fun isUuidType(typeName: String): Boolean {
        return typeName.lowercase() == "uuid"
    }
    
    override fun isUserDefinedType(typeName: String, userDefinedTypes: Map<String, UserDefinedType>): Boolean {
        return userDefinedTypes.containsKey(typeName)
    }
    
    override fun getEnumValues(connection: Connection, typeName: String): List<String>? {
        return try {
            val query = """
                SELECT e.enumlabel 
                FROM pg_type t 
                JOIN pg_enum e ON t.oid = e.enumtypid 
                WHERE t.typname = ?
                ORDER BY e.enumsortorder
            """.trimIndent()
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, typeName)
                val rs = stmt.executeQuery()
                val values = mutableListOf<String>()
                while (rs.next()) {
                    values.add(rs.getString("enumlabel"))
                }
                values.ifEmpty { null }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getCompositeTypeFields(typeName: String, userDefinedTypes: Map<String, UserDefinedType>): List<CompositeTypeField>? {
        return userDefinedTypes[typeName]?.compositeFields
    }
    
    override fun getArrayElementType(typeName: String): String? {
        return if (isArrayType(typeName)) {
            typeName.replace("[]", "").replace("_", "")
        } else null
    }
    
    override fun createInsertStatement(table: TableMetadata): String {
        val columns = table.columns.filter { !it.autoIncrement }
        val columnNames = columns.joinToString(", ") { it.name }
        val placeholders = columns.joinToString(", ") { "?" }
        
        return "INSERT INTO ${table.name} ($columnNames) VALUES ($placeholders)"
    }
    
    override fun supportsCopyOperations(): Boolean = true
    
    override fun createCopyStatement(table: TableMetadata): String? {
        val columns = table.columns.filter { !it.autoIncrement }
        val columnNames = columns.joinToString(", ") { it.name }
        
        return "COPY ${table.name} ($columnNames) FROM STDIN WITH (FORMAT CSV, DELIMITER E'\\t', NULL '\\N', ESCAPE E'\\\\')"
    }
    
    override fun formatCopyValue(value: Any?, column: ColumnMetadata): String {
        return when {
            value == null -> "\\N"
            value is String -> {
                if (column.isJsonType) {
                    value
                } else {
                    value.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
                }
            }
            value is Boolean -> if (value) "t" else "f"
            value is UUID -> value.toString()
            else -> value.toString()
        }
    }
}
