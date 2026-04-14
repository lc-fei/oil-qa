DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    account VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    email VARCHAR(100) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    last_login_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_account UNIQUE (account),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
);

CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    is_system TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (role_code)
);

CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    KEY idx_role_id (role_id)
);

CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    account VARCHAR(50) DEFAULT NULL,
    login_ip VARCHAR(64) DEFAULT NULL,
    login_location VARCHAR(100) DEFAULT NULL,
    login_status TINYINT NOT NULL DEFAULT 1,
    failure_reason VARCHAR(255) DEFAULT NULL,
    login_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_id (user_id),
    KEY idx_login_status (login_status),
    KEY idx_login_at (login_at)
);

CREATE TABLE sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    account VARCHAR(50) DEFAULT NULL,
    module_name VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    request_method VARCHAR(20) DEFAULT NULL,
    request_uri VARCHAR(255) DEFAULT NULL,
    request_param TEXT DEFAULT NULL,
    operation_result TINYINT NOT NULL DEFAULT 1,
    error_message VARCHAR(500) DEFAULT NULL,
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_operation_user_id (user_id),
    KEY idx_module_name (module_name),
    KEY idx_operated_at (operated_at)
);
