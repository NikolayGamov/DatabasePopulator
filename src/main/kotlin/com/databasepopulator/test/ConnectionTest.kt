
package com.databasepopulator.test

import com.databasepopulator.config.DatabaseConfig
import com.databasepopulator.database.DatabaseConnectionManager
import java.sql.Connection

/**
 * Утилита для тестирования подключений к базам данных
 */
class ConnectionTest {
    
    private val connectionManager = DatabaseConnectionManager()
    
    /**
     * Тестирует подключение к базе данных
     */
    fun testConnection(dbConfig: DatabaseConfig): Boolean {
        return try {
            println("Тестирование подключения к ${dbConfig.name}...")
            
            val connection = connectionManager.getConnection(dbConfig)
            val isValid = connection.isValid(5)
            
            if (isValid) {
                testBasicQuery(connection, dbConfig)
                println("✓ Подключение к ${dbConfig.name} успешно")
            } else {
                println("✗ Подключение к ${dbConfig.name} не действительно")
            }
            
            connection.close()
            isValid
            
        } catch (e: Exception) {
            println("✗ Ошибка подключения к ${dbConfig.name}: ${e.message}")
            false
        }
    }
    
    /**
     * Выполняет простой тестовый запрос
     */
    private fun testBasicQuery(connection: Connection, dbConfig: DatabaseConfig) {
        try {
            val query = when {
                dbConfig.driver.contains("postgresql") -> "SELECT version()"
                dbConfig.driver.contains("ignite") -> "SELECT 1"
                else -> "SELECT 1"
            }
            
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(query)
            
            if (resultSet.next()) {
                val result = resultSet.getString(1)
                println("  Тестовый запрос выполнен: ${result.take(50)}...")
            }
            
            resultSet.close()
            statement.close()
            
        } catch (e: Exception) {
            println("  Предупреждение: не удалось выполнить тестовый запрос: ${e.message}")
        }
    }
}
