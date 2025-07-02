
-- PostgreSQL test schema
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS orders (
    order_id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    order_number VARCHAR(20) UNIQUE NOT NULL,
    order_status VARCHAR(20) DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Пользовательские типы для тестирования
CREATE TYPE user_status AS ENUM ('active', 'inactive', 'suspended', 'pending');
CREATE TYPE contact_info AS (
    email VARCHAR(100),
    phone VARCHAR(20),
    address TEXT
);

-- Таблица с пользовательскими типами
CREATE TABLE IF NOT EXISTS user_profiles (
    profile_id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    status user_status DEFAULT 'pending',
    contact contact_info,
    preferences JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица с массивами и JSONB
CREATE TABLE IF NOT EXISTS advanced_data (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    tags TEXT[] DEFAULT '{}',
    scores INTEGER[] DEFAULT '{}',
    settings JSONB DEFAULT '{}',
    metadata JSON DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для тестирования различных типов массивов
CREATE TABLE IF NOT EXISTS array_tests (
    id SERIAL PRIMARY KEY,
    text_array TEXT[],
    int_array INTEGER[],
    bool_array BOOLEAN[],
    decimal_array DECIMAL(10,2)[],
    uuid_array UUID[],
    timestamp_array TIMESTAMP[],
    json_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Apache Ignite test schema (создается через код)
-- Ignite поддерживает следующие таблицы:

-- CREATE TABLE cache_users (
--     user_id UUID PRIMARY KEY,
--     email VARCHAR(100),
--     full_name VARCHAR(100),
--     cache_data VARCHAR(1000), -- для JSON данных в Ignite
--     tags VARCHAR(500), -- для массивов в виде строки в Ignite
--     metadata VARCHAR(2000), -- дополнительные JSON метаданные
--     last_login TIMESTAMP,
--     is_active BOOLEAN DEFAULT true,
--     login_count INTEGER DEFAULT 0
-- ) WITH "template=replicated,cache_name=cache_users";

-- CREATE TABLE cache_sessions (
--     session_id UUID PRIMARY KEY,
--     user_id UUID,
--     session_data VARCHAR(2000), -- JSON данные сессии
--     ip_address VARCHAR(45),
--     user_agent VARCHAR(500),
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     expires_at TIMESTAMP,
--     is_valid BOOLEAN DEFAULT true
-- ) WITH "template=partitioned,cache_name=cache_sessions,backups=1";

-- CREATE TABLE cache_analytics (
--     event_id UUID PRIMARY KEY,
--     user_id UUID,
--     event_type VARCHAR(50),
--     event_data VARCHAR(5000), -- JSON данные события
--     tags VARCHAR(1000), -- массив тегов как строка
--     timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     processed BOOLEAN DEFAULT false
-- ) WITH "template=partitioned,cache_name=cache_analytics,backups=2";ns (
--     session_id UUID PRIMARY KEY,
--     user_id UUID,
--     created_at TIMESTAMP,
--     expires_at TIMESTAMP,
--     is_active BOOLEAN
-- ) WITH "template=partitioned";
