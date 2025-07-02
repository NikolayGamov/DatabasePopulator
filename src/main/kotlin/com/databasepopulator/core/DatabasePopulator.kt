
package com.databasepopulator.core

import com.databasepopulator.config.PopulatorConfig
import com.databasepopulator.database.DatabaseConnectionManager
import com.databasepopulator.database.MetadataExtractor
import com.databasepopulator.generator.DataGenerator
import java.sql.Connection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.*

/**
 * Основной класс для наполнения баз данных синтетическими данными
 */
class DatabasePopulator(private val config: PopulatorConfig) {
    
    private val connectionManager = DatabaseConnectionManager()
    private val metadataExtractor = MetadataExtractor()
    private val dataGenerator = DataGenerator(config)
    
    // Пул потоков для параллельной обработки
    private val executorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtMost(8)
    )
    
    /**
     * Запускает процесс наполнения всех баз данных
     */
    fun populate() {
        println("Начало процесса наполнения баз данных...")
        
        try {
            // Обрабатываем базы данных параллельно
            val futures = config.databases.map { dbConfig ->
                CompletableFuture.supplyAsync({
                    println("\nОбработка базы данных: ${dbConfig.name}")
                    populateDatabase(dbConfig)
                }, executorService)
            }
            
            // Ждем завершения всех баз данных
            CompletableFuture.allOf(*futures.toTypedArray()).join()
            
        } finally {
            executorService.shutdown()
        }
        
        println("\nВсе базы данных успешно наполнены!")
    }
    
    /**
     * Наполняет конкретную базу данных
     */
    private fun populateDatabase(dbConfig: com.databasepopulator.config.DatabaseConfig) {
        val connection = connectionManager.getConnection(dbConfig)
        
        try {
            // Извлекаем метаданные о структуре базы данных
            println("Извлечение метаданных...")
            val tableMetadata = metadataExtractor.extractTableMetadata(connection, dbConfig.name)
            
            if (tableMetadata.isEmpty()) {
                println("В базе данных ${dbConfig.name} не найдено таблиц для наполнения")
                return
            }
            
            println("Найдено таблиц: ${tableMetadata.size}")
            
            // Сортируем таблицы по зависимостям и создаем волны обработки
            val tableWaves = createTableWaves(tableMetadata)
            
            // Обрабатываем каждую волну таблиц параллельно
            tableWaves.forEachIndexed { waveIndex, tablesInWave ->
                println("Обработка волны ${waveIndex + 1}: ${tablesInWave.size} таблиц")
                processTableWaveInParallel(connection, dbConfig, tablesInWave)
            }
            
        } finally {
            connection.close()
        }
    }
    
    /**
     * Создает волны таблиц для параллельной обработки
     */
    private fun createTableWaves(tables: List<TableMetadata>): List<List<TableMetadata>> {
        val waves = mutableListOf<List<TableMetadata>>()
        val processed = mutableSetOf<String>()
        val remaining = tables.toMutableList()
        
        while (remaining.isNotEmpty()) {
            val currentWave = remaining.filter { table ->
                // Таблицы без зависимостей или с уже обработанными зависимостями
                table.foreignKeys.all { fk ->
                    processed.contains(fk.referencedTable) || fk.referencedTable == table.name
                }
            }
            
            if (currentWave.isEmpty()) {
                // Если есть циклические зависимости, добавляем оставшиеся таблицы в текущую волну
                waves.add(remaining.toList())
                break
            }
            
            waves.add(currentWave)
            processed.addAll(currentWave.map { it.name })
            remaining.removeAll(currentWave)
        }
        
        return waves
    }
    
    /**
     * Обрабатывает волну таблиц параллельно
     */
    private fun processTableWaveInParallel(
        baseConnection: Connection, 
        dbConfig: com.databasepopulator.config.DatabaseConfig, 
        tables: List<TableMetadata>
    ) {
        val futures = tables.map { table ->
            CompletableFuture.supplyAsync({
                // Создаем отдельное соединение для каждого потока
                val connection = connectionManager.getConnection(dbConfig)
                
                try {
                    val recordCount = getRecordCountForTable(dbConfig.name, table.name, dbConfig.defaultRecordCount)
                    println("Наполнение таблицы ${table.name} ($recordCount записей)...")
                    
                    dataGenerator.populateTable(connection, table, recordCount)
                    println("Таблица ${table.name} успешно наполнена")
                    
                } catch (e: Exception) {
                    println("Ошибка при наполнении таблицы ${table.name}: ${e.message}")
                    throw e
                } finally {
                    connection.close()
                }
            }, executorService)
        }
        
        // Ждем завершения всех таблиц в текущей волне
        CompletableFuture.allOf(*futures.toTypedArray()).join()
    }
    
    /**
     * Определяет количество записей для конкретной таблицы
     */
    private fun getRecordCountForTable(databaseName: String, tableName: String, defaultCount: Int): Int {
        return config.tableSettings
            .find { it.databaseName == databaseName && it.tableName == tableName }
            ?.recordCount ?: defaultCount
    }
}
