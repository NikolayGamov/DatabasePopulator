
package com.databasepopulator.database

import com.databasepopulator.config.DatabaseConfig
import java.sql.Connection
import java.sql.DriverManager

/**
 * Менеджер подключений к базам данных
 */
class DatabaseConnectionManager {
    
    /**
     * Создает подключение к базе данных
     */
    fun getConnection(config: DatabaseConfig): Connection {
        try {
            // Загружаем драйвер
            Class.forName(config.driver)
            
            // Создаем подключение
            val connection = DriverManager.getConnection(
                config.jdbcUrl,
                config.username,
                config.password
            )
            
            // Отключаем автокоммит для повышения производительности
            connection.autoCommit = false
            
            println("Успешное подключение к базе данных: ${config.name}")
            return connection
            
        } catch (e: Exception) {
            throw RuntimeException("Ошибка подключения к базе данных ${config.name}: ${e.message}", e)
        }
    }
}
