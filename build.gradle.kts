
plugins {
    kotlin("jvm") version "1.8.20"
    application
}

group = "com.databasepopulator"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Database drivers
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.apache.ignite:ignite-core:2.15.0")
    implementation("org.apache.ignite:ignite-clients:2.15.0")
    
    // Configuration
    implementation("com.typesafe:config:1.4.2")
    
    // Data generation
    implementation("com.github.javafaker:javafaker:1.0.2")
    implementation("com.github.mifmif:generex:1.0.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.slf4j:slf4j-api:2.0.7")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.databasepopulator.MainKt")
}

tasks.register<JavaExec>("runTests") {
    group = "verification"
    description = "Запуск тестов подключения к базам данных"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("TestRunnerKt")
    
    doFirst {
        println("Запуск тестов подключения...")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    
    doFirst {
        val configPath = System.getenv("DATABASE_POPULATOR_CONFIG")
        if (configPath.isNullOrEmpty()) {
            throw GradleException("Переменная окружения DATABASE_POPULATOR_CONFIG не установлена")
        }
        println("Использование конфигурации: $configPath")
    }
}
