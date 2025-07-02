
package com.databasepopulator.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File

/**
 * Загрузчик конфигурации из HOCON файлов
 */
object ConfigLoader {
    
    /**
     * Загружает конфигурацию из указанного файла
     */
    fun loadConfig(configPath: String): PopulatorConfig {
        val configFile = File(configPath)
        if (!configFile.exists()) {
            throw IllegalArgumentException("Конфигурационный файл не найден: $configPath")
        }
        
        val config = ConfigFactory.parseFile(configFile)
        return parseConfig(config)
    }
    
    /**
     * Парсит конфигурацию из Config объекта
     */
    private fun parseConfig(config: Config): PopulatorConfig {
        val databases = config.getConfigList("databases").map { dbConfig ->
            DatabaseConfig(
                name = dbConfig.getString("name"),
                jdbcUrl = dbConfig.getString("jdbcUrl"),
                username = dbConfig.getString("username"),
                password = dbConfig.getString("password"),
                driver = dbConfig.getString("driver"),
                defaultRecordCount = if (dbConfig.hasPath("defaultRecordCount")) 
                    dbConfig.getInt("defaultRecordCount") else 1000
            )
        }
        
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
        
        val fieldRelations = if (config.hasPath("fieldRelations")) {
            config.getConfigList("fieldRelations").map { relationConfig ->
                FieldRelation(
                    type = RelationType.valueOf(relationConfig.getString("type").uppercase()),
                    sourceFields = relationConfig.getStringList("sourceFields"),
                    targetFields = relationConfig.getStringList("targetFields")
                )
            }
        } else emptyList()
        
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
        
        return PopulatorConfig(
            databases = databases,
            defaultRecordCount = if (config.hasPath("defaultRecordCount")) 
                config.getInt("defaultRecordCount") else 1000,
            tableSettings = tableSettings,
            fieldRelations = fieldRelations,
            generationRules = generationRules
        )
    }
}
