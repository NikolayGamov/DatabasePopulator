
package com.databasepopulator.database

import com.databasepopulator.core.ColumnMetadata
import com.databasepopulator.core.TableMetadata
import java.sql.Connection

/**
 * Абстрактный провайдер базы данных
 */
abstract class DatabaseProvider {
    
    /**
     * Проверяет, поддерживается ли данный тип БД
     */
    abstract fun supports(connection: Connection): Boolean
    
    /**
     * Получает название схемы по умолчанию
     */
    abstract fun getDefaultSchema(): String?
    
    /**
     * Извлекает пользовательские типы данных
     */
    abstract fun extractUserDefinedTypes(connection: Connection, schema: String?): Map<String, UserDefinedType>
    
    /**
     * Проверяет, является ли тип JSON
     */
    abstract fun isJsonType(typeName: String): Boolean
    
    /**
     * Проверяет, является ли тип массивом
     */
    abstract fun isArrayType(typeName: String): Boolean
    
    /**
     * Проверяет, является ли тип UUID
     */
    abstract fun isUuidType(typeName: String): Boolean
    
    /**
     * Проверяет, является ли тип пользовательским
     */
    abstract fun isUserDefinedType(typeName: String, userDefinedTypes: Map<String, UserDefinedType>): Boolean
    
    /**
     * Получает значения перечисления для enum типов
     */
    abstract fun getEnumValues(connection: Connection, typeName: String): List<String>?
    
    /**
     * Получает поля составного типа
     */
    abstract fun getCompositeTypeFields(typeName: String, userDefinedTypes: Map<String, UserDefinedType>): List<CompositeTypeField>?
    
    /**
     * Получает тип элементов массива
     */
    abstract fun getArrayElementType(typeName: String): String?
    
    /**
     * Создает оптимизированный SQL для вставки данных
     */
    abstract fun createInsertStatement(table: TableMetadata): String
    
    /**
     * Поддерживает ли БД COPY операции
     */
    abstract fun supportsCopyOperations(): Boolean
    
    /**
     * Создает COPY statement для массовой вставки
     */
    abstract fun createCopyStatement(table: TableMetadata): String?
    
    /**
     * Форматирует значение для COPY операции
     */
    abstract fun formatCopyValue(value: Any?, column: ColumnMetadata): String
}
