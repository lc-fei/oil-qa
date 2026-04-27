DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS qa_daily_stat;
DROP TABLE IF EXISTS qa_recommend_question;
DROP TABLE IF EXISTS qa_message_feedback;
DROP TABLE IF EXISTS qa_message_favorite;
DROP TABLE IF EXISTS qa_message;
DROP TABLE IF EXISTS qa_session;
DROP TABLE IF EXISTS sys_exception_log;
DROP TABLE IF EXISTS qa_ai_call_record;
DROP TABLE IF EXISTS qa_prompt_record;
DROP TABLE IF EXISTS qa_graph_record;
DROP TABLE IF EXISTS qa_nlp_record;
DROP TABLE IF EXISTS qa_request;
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

CREATE TABLE qa_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    user_account VARCHAR(50) DEFAULT NULL,
    question TEXT NOT NULL,
    request_source VARCHAR(20) NOT NULL DEFAULT 'CLIENT_WEB',
    request_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    final_answer LONGTEXT DEFAULT NULL,
    answer_summary TEXT DEFAULT NULL,
    total_duration_ms INT DEFAULT NULL,
    graph_hit TINYINT NOT NULL DEFAULT 0,
    ai_call_status VARCHAR(20) DEFAULT NULL,
    exception_flag TINYINT NOT NULL DEFAULT 0,
    request_uri VARCHAR(255) DEFAULT NULL,
    request_method VARCHAR(20) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME DEFAULT NULL,
    CONSTRAINT uk_qa_request_no UNIQUE (request_no),
    KEY idx_qa_request_trace_id (trace_id),
    KEY idx_qa_request_user_id (user_id),
    KEY idx_qa_request_status (request_status),
    KEY idx_qa_request_source (request_source),
    KEY idx_qa_request_graph_hit (graph_hit),
    KEY idx_qa_request_exception_flag (exception_flag),
    KEY idx_qa_request_created_at (created_at)
);

CREATE TABLE qa_nlp_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    tokenize_result TEXT DEFAULT NULL,
    keyword_list TEXT DEFAULT NULL,
    entity_list TEXT DEFAULT NULL,
    intent VARCHAR(100) DEFAULT NULL,
    confidence DECIMAL(6,4) DEFAULT NULL,
    raw_result LONGTEXT DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qa_nlp_request_no (request_no)
);

CREATE TABLE qa_graph_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    query_condition TEXT DEFAULT NULL,
    hit_entity_list LONGTEXT DEFAULT NULL,
    hit_relation_list LONGTEXT DEFAULT NULL,
    hit_property_summary LONGTEXT DEFAULT NULL,
    result_count INT NOT NULL DEFAULT 0,
    valid_hit TINYINT NOT NULL DEFAULT 0,
    duration_ms INT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qa_graph_request_no (request_no),
    KEY idx_qa_graph_valid_hit (valid_hit)
);

CREATE TABLE qa_prompt_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    original_question TEXT NOT NULL,
    graph_summary TEXT DEFAULT NULL,
    prompt_summary TEXT DEFAULT NULL,
    prompt_content LONGTEXT DEFAULT NULL,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms INT DEFAULT NULL,
    KEY idx_qa_prompt_request_no (request_no),
    KEY idx_qa_prompt_generated_at (generated_at)
);

CREATE TABLE qa_ai_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) DEFAULT NULL,
    provider VARCHAR(100) DEFAULT NULL,
    call_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ai_call_status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    response_status_code INT DEFAULT NULL,
    result_summary TEXT DEFAULT NULL,
    error_message VARCHAR(500) DEFAULT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    duration_ms INT DEFAULT NULL,
    KEY idx_qa_ai_call_request_no (request_no),
    KEY idx_qa_ai_call_status (ai_call_status),
    KEY idx_qa_ai_call_time (call_time)
);

CREATE TABLE sys_exception_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exception_no VARCHAR(64) NOT NULL,
    request_no VARCHAR(64) DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    exception_module VARCHAR(100) NOT NULL,
    exception_level VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    exception_type VARCHAR(100) NOT NULL,
    exception_message VARCHAR(500) DEFAULT NULL,
    stack_trace LONGTEXT DEFAULT NULL,
    request_uri VARCHAR(255) DEFAULT NULL,
    request_method VARCHAR(20) DEFAULT NULL,
    request_param_summary TEXT DEFAULT NULL,
    context_info LONGTEXT DEFAULT NULL,
    handle_status VARCHAR(20) NOT NULL DEFAULT 'UNHANDLED',
    handle_remark VARCHAR(500) DEFAULT NULL,
    handler_id BIGINT DEFAULT NULL,
    handler_name VARCHAR(50) DEFAULT NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at DATETIME DEFAULT NULL,
    CONSTRAINT uk_sys_exception_no UNIQUE (exception_no),
    KEY idx_sys_exception_request_no (request_no),
    KEY idx_sys_exception_trace_id (trace_id),
    KEY idx_sys_exception_module (exception_module),
    KEY idx_sys_exception_level (exception_level),
    KEY idx_sys_exception_handle_status (handle_status),
    KEY idx_sys_exception_occurred_at (occurred_at)
);

CREATE TABLE qa_daily_stat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    exception_count INT NOT NULL DEFAULT 0,
    avg_response_time_ms DECIMAL(12,2) NOT NULL DEFAULT 0,
    p95_response_time_ms DECIMAL(12,2) NOT NULL DEFAULT 0,
    graph_hit_count INT NOT NULL DEFAULT 0,
    ai_call_count INT NOT NULL DEFAULT 0,
    ai_fail_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_daily_stat_date UNIQUE (stat_date)
);

CREATE TABLE qa_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) DEFAULT NULL,
    session_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_message_at DATETIME DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_session_no UNIQUE (session_no),
    KEY idx_qa_session_user_id (user_id),
    KEY idx_qa_session_status (session_status),
    KEY idx_qa_session_last_message_at (last_message_at),
    KEY idx_qa_session_created_at (created_at)
);

CREATE TABLE qa_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_no VARCHAR(64) NOT NULL,
    session_id BIGINT NOT NULL,
    request_no VARCHAR(64) DEFAULT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ASSISTANT',
    question_text TEXT NOT NULL,
    answer_text LONGTEXT DEFAULT NULL,
    partial_answer LONGTEXT DEFAULT NULL,
    answer_summary TEXT DEFAULT NULL,
    message_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    stream_sequence INT NOT NULL DEFAULT 0,
    sequence_no INT NOT NULL DEFAULT 1,
    last_stream_at DATETIME DEFAULT NULL,
    interrupted_reason VARCHAR(255) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME DEFAULT NULL,
    CONSTRAINT uk_qa_message_no UNIQUE (message_no),
    KEY idx_qa_message_session_id (session_id),
    KEY idx_qa_message_request_no (request_no),
    KEY idx_qa_message_status (message_status),
    KEY idx_qa_message_last_stream_at (last_stream_at),
    KEY idx_qa_message_created_at (created_at)
);

CREATE TABLE qa_message_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_message_favorite UNIQUE (user_id, message_id),
    KEY idx_qa_message_favorite_user_id (user_id),
    KEY idx_qa_message_favorite_message_id (message_id),
    KEY idx_qa_message_favorite_session_id (session_id),
    KEY idx_qa_message_favorite_created_at (created_at)
);

CREATE TABLE qa_message_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    feedback_reason VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_message_feedback UNIQUE (user_id, message_id),
    KEY idx_qa_message_feedback_user_id (user_id),
    KEY idx_qa_message_feedback_message_id (message_id),
    KEY idx_qa_message_feedback_type (feedback_type),
    KEY idx_qa_message_feedback_created_at (created_at)
);

CREATE TABLE qa_recommend_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_text VARCHAR(500) NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_qa_recommend_question_type (question_type),
    KEY idx_qa_recommend_question_status (status),
    KEY idx_qa_recommend_question_sort_no (sort_no)
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
