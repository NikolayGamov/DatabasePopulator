
# DatabasePopulator

Консольное приложение на Kotlin для быстрого наполнения реляционных баз данных синтетическими данными.

## Описание

DatabasePopulator - это инструмент, который позволяет автоматически заполнить базы данных тестовыми данными на основе конфигурационного файла. Приложение анализирует структуру базы данных, извлекает метаданные и генерирует синтетические данные с учетом ограничений и связей между таблицами.

## Возможности

- Поддержка PostgreSQL и Apache Ignite
- Гибкая конфигурация через HOCON файлы
- Автоматическое извлечение метаданных базы данных
- Учет ограничений и внешних ключей
- Настраиваемые правила генерации данных
- Связи между полями разных таблиц
- Высокая производительность благодаря batch-операциям
- Умная сортировка таблиц по зависимостям

## Требования

- JDK 21+
- Gradle 8.0+
- Kotlin 1.9.20+

## Сборка и запуск

### Сборка проекта

```bash
./gradlew build
```

### Создание JAR файла

```bash
./gradlew jar
```

### Запуск приложения

1. Настройте конфигурационный файл `config.conf`
2. Установите переменную окружения:

```bash
export DATABASE_POPULATOR_CONFIG=/path/to/config.conf
```

3. Запустите приложение:

```bash
java -jar build/libs/DatabasePopulator-1.0.0.jar
```

Или используя Gradle:

```bash
DATABASE_POPULATOR_CONFIG=./config.conf ./gradlew run
```

## Конфигурация

Конфигурация задается в HOCON формате. Пример:

```hocon
defaultRecordCount = 1000

databases = [
  {
    name = "postgres_main"
    jdbcUrl = "jdbc:postgresql://localhost:5432/testdb"
    username = "postgres"
    password = "password"
    driver = "org.postgresql.Driver"
    defaultRecordCount = 5000
  }
]

tableSettings = [
  {
    databaseName = "postgres_main"
    tableName = "users"
    recordCount = 10000
  }
]

generationRules = [
  {
    databaseName = "postgres_main"
    tableName = "users"
    columnName = "email"
    type = "email"
  }
]
```

### Параметры конфигурации

#### Databases
- `name` - уникальное имя базы данных
- `jdbcUrl` - JDBC URL для подключения
- `username` - имя пользователя
- `password` - пароль
- `driver` - класс JDBC драйвера
- `defaultRecordCount` - количество записей по умолчанию для этой БД

#### Table Settings
- `databaseName` - имя базы данных
- `tableName` - имя таблицы
- `recordCount` - количество записей для этой таблицы

#### Generation Rules
- `databaseName` - имя базы данных
- `tableName` - имя таблицы
- `columnName` - имя колонки
- `type` - тип генератора (email, name, phone, uuid, constant, etc.)
- `parameters` - дополнительные параметры

#### Field Relations
- `type` - тип связи (SAME_VALUES, DISJOINT_UNION)
- `sourceFields` - исходные поля
- `targetFields` - целевые поля

## Поддерживаемые типы генераторов

- `name` - полное имя
- `firstname` - имя
- `lastname` - фамилия
- `email` - email адрес
- `phone` - номер телефона
- `address` - адрес
- `company` - название компании
- `uuid` - UUID
- `constant` - константное значение
- `sequence` - последовательность чисел

## Поддерживаемые базы данных

- PostgreSQL
- Apache Ignite

## Архитектура

Проект состоит из следующих основных компонентов:

- **Config** - загрузка и парсинг конфигурации
- **Core** - основная логика популятора и метаданные
- **Database** - работа с подключениями и извлечение метаданных
- **Generator** - генерация синтетических данных

## Примеры использования

### Базовое использование

```bash
export DATABASE_POPULATOR_CONFIG=./config.conf
java -jar DatabasePopulator-1.0.0.jar
```

### С Docker

```dockerfile
FROM openjdk:21-jre-slim
COPY DatabasePopulator-1.0.0.jar /app/
COPY config.conf /app/
ENV DATABASE_POPULATOR_CONFIG=/app/config.conf
WORKDIR /app
CMD ["java", "-jar", "DatabasePopulator-1.0.0.jar"]
```

## Лицензия

MIT License
