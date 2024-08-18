-- user_info 表
DROP TABLE IF EXISTS user_info;
CREATE TABLE IF NOT EXISTS user_info
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY NOT NULL, -- OceanBase 支持 AUTO_INCREMENT
    name        VARCHAR(255) DEFAULT NULL,
    age         INT         DEFAULT NULL,
    email       VARCHAR(255) DEFAULT NULL,
    gmt_deleted DATETIME    DEFAULT '2001-01-01 00:00:00'
    );

-- user_role 表
DROP TABLE IF EXISTS user_role;
CREATE TABLE IF NOT EXISTS user_role
(
    id      BIGINT AUTO_INCREMENT PRIMARY KEY NOT NULL,
    user_id BIGINT      DEFAULT NULL,
    role_id VARCHAR(30) DEFAULT NULL
    );

-- role_info 表
DROP TABLE IF EXISTS role_info;
CREATE TABLE IF NOT EXISTS role_info
(
    id        VARCHAR(30) NOT NULL,
    role_name VARCHAR(30) DEFAULT NULL,
    PRIMARY KEY (id)
    );

-- product_info 表
DROP TABLE IF EXISTS product_info;
CREATE TABLE IF NOT EXISTS product_info
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY NOT NULL,
    product_name  VARCHAR(255) DEFAULT NULL,
    product_price DOUBLE PRECISION DEFAULT NULL, -- 将 FLOAT 改为 DOUBLE PRECISION
    tenant_id     VARCHAR(255) DEFAULT NULL
    );

-- product_category 表
DROP TABLE IF EXISTS product_category;
CREATE TABLE IF NOT EXISTS product_category
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY NOT NULL,
    product_id  BIGINT DEFAULT NULL,
    category_id BIGINT DEFAULT NULL,
    tenant_id   VARCHAR(255) DEFAULT NULL
    );