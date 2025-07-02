
package com.databasepopulator.database

import java.sql.Connection

/**
 * Фабрика для создания провайдеров баз данных
 */
object DatabaseProviderFactory {
    
    private val providers = listOf(
        PostgreSQLProvider(),
        IgniteProvider()
    )
    
    /**
     * Получает подходящий провайдер для подключения к БД
     */
    fun getProvider(connection: Connection): DatabaseProvider {
        return providers.find { it.supports(connection) }
            ?: throw IllegalArgumentException("Неподдерживаемый тип базы данных: ${connection.metaData.databaseProductName}")
    }
    
    /**
     * Регистрирует новый провайдер
     */
    fun registerProvider(provider: DatabaseProvider) {
        // Для будущего расширения функциональности
    }
}
