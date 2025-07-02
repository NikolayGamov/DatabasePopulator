
package com.databasepopulator.core

import com.databasepopulator.config.PopulatorConfig
import com.databasepopulator.database.DatabaseConnectionManager
import com.databasepopulator.database.MetadataExtractor
import com.databasepopulator.generator.DataGenerator
import java.sql.Connection

/**
 * Основной класс для наполнения баз данных синтетическими данными
 */
class DatabasePopulator(private val config: PopulatorConfig) {
    
    private val connectionManager = DatabaseConnectionManager()
    private val metadataExtractor = MetadataExtractor()
    private val dataGenerator = DataGenerator(config)
    
    /**
     * Запускает процесс наполнения всех баз данных
     */
    fun populate() {
        println("Начало процесса наполнения баз данных...")
        
        config.databases.forEach { dbConfig ->
            println("\nОбработка базы данных: ${dbConfig.name}")
            populateDatabase(dbConfig)
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
            
            // Сортируем таблицы по зависимостям (сначала таблицы без внешних ключей)
            val sortedTables = sortTablesByDependencies(tableMetadata)
            
            // Наполняем каждую таблицу
            sortedTables.forEach { table ->
                val recordCount = getRecordCountForTable(dbConfig.name, table.name, dbConfig.defaultRecordCount)
                println("Наполнение таблицы ${table.name} ($recordCount записей)...")
                
                dataGenerator.populateTable(connection, table, recordCount)
                println("Таблица ${table.name} успешно наполнена")
            }
            
        } finally {
            connection.close()
        }
    }
    
    /**
     * Сортирует таблицы по зависимостям (foreign keys)
     */
    private fun sortTablesByDependencies(tables: List<TableMetadata>): List<TableMetadata> {
        val sorted = mutableListOf<TableMetadata>()
        val remaining = tables.toMutableList()
        
        while (remaining.isNotEmpty()) {
            val tablesToAdd = remaining.filter { table ->
                // Добавляем таблицы, все foreign keys которых уже обработаны
                table.foreignKeys.all { fk ->
                    sorted.any { it.name == fk.referencedTable } || fk.referencedTable == table.name
                }
            }
            
            if (tablesToAdd.isEmpty()) {
                // Если есть циклические зависимости, добавляем оставшиеся таблицы
                sorted.addAll(remaining)
                break
            }
            
            sorted.addAll(tablesToAdd)
            remaining.removeAll(tablesToAdd)
        }
        
        return sorted
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
