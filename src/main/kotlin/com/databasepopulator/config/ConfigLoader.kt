
package com.databasepopulator.config

import com.typesafe.config.ConfigFactory
import java.io.File

/**
 * Загрузчик конфигурации из HOCON файлов
 */
object ConfigLoader {
    
    /**
     * Загружает конфигурацию из файла
     */
    fun loadConfig(configPath: String): PopulatorConfig {
        try {
            println("Загрузка конфигурации из файла: $configPath")
            
            val configFile = File(configPath)
            if (!configFile.exists()) {
                throw IllegalArgumentException("Конфигурационный файл не найден: $configPath")
            }
            
            val config = ConfigFactory.parseFile(configFile)
            
            val defaultRecordCount = if (config.hasPath("defaultRecordCount")) 
                config.getInt("defaultRecordCount") else 1000
            
            // Загружаем конфигурации баз данных
            val databases = config.getConfigList("databases").map { dbConfig ->
                DatabaseConfig(
                    name = dbConfig.getString("name"),
                    jdbcUrl = dbConfig.getString("jdbcUrl"),
                    username = dbConfig.getString("username"),
                    password = dbConfig.getString("password"),
                    driver = dbConfig.getString("driver"),
                    defaultRecordCount = if (dbConfig.hasPath("defaultRecordCount")) 
                        dbConfig.getInt("defaultRecordCount") else defaultRecordCount
                )
            }
            
            // Загружаем настройки таблиц
            val tableSettings = if (config.hasPath("tableSettings")) {
                config.getConfigList("tableSettings").map { tableConfig ->
                    TableSetting(
                        databaseName = tableConfig.getString("databaseName"),
                        tableName = tableConfig.getString("tableName"),
                        recordCount = if (tableConfig.hasPath("recordCount")) 
                            tableConfig.getInt("recordCount") else null
                    )
                }
            } else emptyList()
            
            // Загружаем связи полей
            val fieldRelations = if (config.hasPath("fieldRelations")) {
                config.getConfigList("fieldRelations").map { relationConfig ->
                    FieldRelation(
                        type = RelationType.valueOf(relationConfig.getString("type").uppercase()),
                        sourceFields = relationConfig.getStringList("sourceFields"),
                        targetFields = relationConfig.getStringList("targetFields")
                    )
                }
            } else emptyList()
            
            // Загружаем правила генерации
            val generationRules = if (config.hasPath("generationRules")) {
                config.getConfigList("generationRules").associate { ruleConfig ->
                    val fieldKey = "${ruleConfig.getString("databaseName")}.${ruleConfig.getString("tableName")}.${ruleConfig.getString("columnName")}"
                    fieldKey to GenerationRule(
                        type = ruleConfig.getString("type"),
                        parameters = if (ruleConfig.hasPath("parameters")) 
                            ruleConfig.getConfig("parameters").entrySet().associate { 
                                it.key to it.value.unwrapped().toString() 
                            } else emptyMap()
                    )
                }
            } else emptyMap()
            
            println("Конфигурация успешно загружена:")
            println("- Баз данных: ${databases.size}")
            println("- Настроек таблиц: ${tableSettings.size}")
            println("- Связей полей: ${fieldRelations.size}")
            println("- Правил генерации: ${generationRules.size}")
            
            return PopulatorConfig(
                databases = databases,
                defaultRecordCount = defaultRecordCount,
                tableSettings = tableSettings,
                fieldRelations = fieldRelations,
                generationRules = generationRules
            )
            
        } catch (e: Exception) {
            throw RuntimeException("Ошибка загрузки конфигурации: ${e.message}", e)
        }
    }
}
