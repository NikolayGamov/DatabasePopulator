
package com.databasepopulator

import com.databasepopulator.config.ConfigLoader
import com.databasepopulator.core.DatabasePopulator
import kotlin.system.exitProcess

/**
 * Главная точка входа в приложение DatabasePopulator
 */
fun main() {
    try {
        println("=== DatabasePopulator v1.0.0 ===")
        println("Запуск приложения для наполнения баз данных синтетическими данными...")
        
        // Получаем путь к конфигурационному файлу из переменной окружения
        val configPath = System.getenv("DATABASE_POPULATOR_CONFIG") 
            ?: throw IllegalArgumentException("Переменная окружения DATABASE_POPULATOR_CONFIG не установлена")
        
        println("Загрузка конфигурации из: $configPath")
        val config = ConfigLoader.loadConfig(configPath)
        
        // Создаем и запускаем популятор
        val populator = DatabasePopulator(config)
        populator.populate()
        
        println("Наполнение базы данных завершено успешно!")
        
    } catch (e: Exception) {
        println("Ошибка выполнения: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
