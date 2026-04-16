DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS kg_import_task;
DROP TABLE IF EXISTS kg_version;
DROP TABLE IF EXISTS kg_relation_type;
DROP TABLE IF EXISTS kg_entity_type;
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

CREATE TABLE kg_entity_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_name VARCHAR(100) NOT NULL,
    type_code VARCHAR(100) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    sort_no INT NOT NULL DEFAULT 0,
    created_by VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_kg_entity_type_code UNIQUE (type_code),
    KEY idx_kg_entity_type_status (status),
    KEY idx_kg_entity_type_sort_no (sort_no)
);

CREATE TABLE kg_relation_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_name VARCHAR(100) NOT NULL,
    type_code VARCHAR(100) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    sort_no INT NOT NULL DEFAULT 0,
    created_by VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_kg_relation_type_code UNIQUE (type_code),
    KEY idx_kg_relation_type_status (status),
    KEY idx_kg_relation_type_sort_no (sort_no)
);

CREATE TABLE kg_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_no VARCHAR(50) NOT NULL,
    version_remark VARCHAR(255) DEFAULT NULL,
    created_by VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kg_version_no UNIQUE (version_no),
    KEY idx_kg_version_created_at (created_at)
);

CREATE TABLE kg_import_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_type VARCHAR(20) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    error_rows TEXT DEFAULT NULL,
    version_id BIGINT DEFAULT NULL,
    created_by VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME DEFAULT NULL,
    KEY idx_kg_import_task_import_type (import_type),
    KEY idx_kg_import_task_status (status),
    KEY idx_kg_import_task_created_at (created_at)
);
