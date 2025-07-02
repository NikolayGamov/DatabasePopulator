
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.typesafe:config:1.4.3")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("org.apache.ignite:ignite-core:2.15.0")
    implementation("org.apache.ignite:ignite-indexing:2.15.0")
    implementation("com.github.javafaker:javafaker:1.0.2")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.databasepopulator.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.databasepopulator.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
