
import com.databasepopulator.config.ConfigLoader
import com.databasepopulator.core.DatabasePopulator
import com.databasepopulator.test.ConnectionTest

/**
 * Тестовый раннер для проверки функциональности приложения
 */
fun main() {
    println("=== Тестирование DatabasePopulator ===")
    
    try {
        // Загружаем конфигурацию
        val configPath = System.getenv("DATABASE_POPULATOR_CONFIG") ?: "./config.conf"
        println("Загрузка тестовой конфигурации: $configPath")
        
        val config = ConfigLoader.loadConfig(configPath)
        
        // Тестируем подключения
        println("\n1. Тестирование подключений к базам данных:")
        val connectionTest = ConnectionTest()
        
        config.databases.forEach { dbConfig ->
            connectionTest.testConnection(dbConfig)
        }
        
        // Проверяем конфигурацию связей
        println("\n2. Проверка конфигурации связей полей:")
        config.fieldRelations.forEach { relation ->
            println("Связь ${relation.type}:")
            println("  Источники: ${relation.sourceFields}")
            println("  Цели: ${relation.targetFields}")
        }
        
        // Проверяем правила генерации
        println("\n3. Проверка правил генерации:")
        config.generationRules.forEach { (field, rule) ->
            println("$field -> ${rule.type} ${rule.parameters}")
        }
        
        // Запускаем популятор (с предупреждением)
        println("\n4. Внимание! Запуск популятора заполнит базы данных тестовыми данными.")
        println("Продолжить? (y/N)")
        
        val input = readlnOrNull()?.lowercase()
        if (input == "y" || input == "yes") {
            println("Запуск популятора...")
            val populator = DatabasePopulator(config)
            populator.populate()
        } else {
            println("Тестирование завершено без наполнения БД.")
        }
        
    } catch (e: Exception) {
        println("Ошибка при тестировании: ${e.message}")
        e.printStackTrace()
    }
}
