
plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "com.databasepopulator"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // Конфигурация (Typesafe Config)
    implementation("com.typesafe:config:1.4.3")
    
    // JDBC драйверы
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("org.apache.ignite:ignite-core:2.16.0")
    implementation("org.apache.ignite:ignite-indexing:2.16.0")
    
    // Генерация данных
    implementation("com.github.javafaker:javafaker:1.0.2")
    
    // Логирование
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Тестирование
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    mainClass.set("com.databasepopulator.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xjsr305=strict")
    }
}

tasks.jar {
    archiveBaseName.set("DatabasePopulator")
    archiveVersion.set("1.0.0")
    
    // Создание "fat jar" с зависимостями
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes(
            "Main-Class" to "com.databasepopulator.MainKt",
            "Implementation-Title" to "DatabasePopulator",
            "Implementation-Version" to version
        )
    }
}

// Дополнительная задача для запуска тестов
tasks.register<JavaExec>("runTests") {
    description = "Run comprehensive tests"
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("TestRunnerKt")
    
    // Передаем переменные окружения
    environment("DATABASE_POPULATOR_CONFIG", "./config.conf")
}
