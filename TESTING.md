
# Тестирование DatabasePopulator

## Подготовка к тестированию

### 1. Настройка PostgreSQL (через Replit Database)
1. Откройте вкладку "Database" в Replit
2. Создайте PostgreSQL базу данных
3. Скопируйте DATABASE_URL из переменных окружения
4. Обновите `config.conf` с правильными параметрами подключения

### 2. Настройка Apache Ignite (локально для тестирования)
```bash
# Скачайте и запустите Ignite в режиме thin client
# Или используйте Docker:
# docker run -p 10800:10800 apacheignite/ignite:latest
```

### 3. Создание тестовых таблиц
Выполните SQL из файла `test-schema.sql` в вашей PostgreSQL базе.

## Запуск тестов

### 1. Тест подключений
```bash
DATABASE_POPULATOR_CONFIG=./config.conf ./gradlew runTests
```

### 2. Полный запуск приложения
```bash
DATABASE_POPULATOR_CONFIG=./config.conf ./gradlew run
```

## Что тестируется

### 1. Подключения к базам данных
- ✅ PostgreSQL подключение
- ✅ Apache Ignite подключение (если доступен)
- ✅ Валидация JDBC драйверов

### 2. Извлечение метаданных
- ✅ Извлечение информации о таблицах
- ✅ Извлечение информации о колонках
- ✅ Извлечение внешних ключей
- ✅ Определение типов данных

### 3. Правила генерации данных
- ✅ Email генерация для полей email
- ✅ Имена для полей name/firstname/lastname
- ✅ UUID генерация
- ✅ Константные значения
- ✅ Последовательности

### 4. Связи между полями
- ✅ SAME_VALUES - одинаковые значения в связанных полях
- ✅ DISJOINT_UNION - непересекающиеся наборы значений

### 5. Производительность
- ✅ COPY операции для PostgreSQL
- ✅ Batch операции для других БД
- ✅ Управление транзакциями

## Проверка результатов

### PostgreSQL
```sql
-- Проверка количества записей
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM orders;

-- Проверка связей
SELECT u.email, COUNT(o.order_id) as order_count 
FROM users u 
LEFT JOIN orders o ON u.user_id = o.user_id 
GROUP BY u.email 
LIMIT 10;

-- Проверка правил генерации
SELECT email FROM users WHERE email LIKE '%@%' LIMIT 5;
SELECT order_status FROM orders GROUP BY order_status;
```

### Apache Ignite (если доступен)
```sql
SELECT COUNT(*) FROM cache_users;
SELECT COUNT(*) FROM cache_sessions;
SELECT email FROM cache_users LIMIT 10;
```

## Ожидаемые результаты

1. **Подключения**: Все настроенные базы данных должны подключаться успешно
2. **Метаданные**: Должны извлекаться все таблицы и их структура
3. **Генерация**: Данные должны соответствовать правилам (email содержит @, UUID имеет правильный формат)
4. **Связи**: Поля с SAME_VALUES должны иметь пересекающиеся значения
5. **Производительность**: Операции должны выполняться быстро благодаря batch/COPY

## Отладка проблем

### Проблемы с подключением
- Проверьте правильность JDBC URL
- Убедитесь, что драйверы включены в build.gradle.kts
- Проверьте доступность баз данных

### Проблемы с метаданными
- Убедитесь, что таблицы существуют
- Проверьте права доступа пользователя БД
- Проверьте схемы (public для PostgreSQL, PUBLIC для Ignite)

### Проблемы с генерацией
- Проверьте правильность правил в config.conf
- Убедитесь, что связи полей настроены корректно
- Проверьте совместимость типов данных
