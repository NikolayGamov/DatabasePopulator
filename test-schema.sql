
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

-- Apache Ignite test schema (создается через код)
-- CREATE TABLE cache_users (
--     user_id UUID PRIMARY KEY,
--     email VARCHAR(100),
--     full_name VARCHAR(100),
--     last_login TIMESTAMP
-- ) WITH "template=replicated";

-- CREATE TABLE cache_sessions (
--     session_id UUID PRIMARY KEY,
--     user_id UUID,
--     created_at TIMESTAMP,
--     expires_at TIMESTAMP,
--     is_active BOOLEAN
-- ) WITH "template=partitioned";
