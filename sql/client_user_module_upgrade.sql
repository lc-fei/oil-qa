-- 用户端后端模块数据库增量脚本
-- 用途：在现有 oil_qa 数据库基础上补齐用户端会话、消息、收藏、反馈、推荐问题表

CREATE TABLE IF NOT EXISTS qa_session (
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

CREATE TABLE IF NOT EXISTS qa_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_no VARCHAR(64) NOT NULL,
    session_id BIGINT NOT NULL,
    request_no VARCHAR(64) DEFAULT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ASSISTANT',
    question_text TEXT NOT NULL,
    answer_text LONGTEXT DEFAULT NULL,
    partial_answer LONGTEXT DEFAULT NULL,
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

CREATE TABLE IF NOT EXISTS qa_orchestration_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    session_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    pipeline_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    current_stage VARCHAR(50) DEFAULT NULL,
    stage_trace_json LONGTEXT DEFAULT NULL,
    tool_calls_json LONGTEXT DEFAULT NULL,
    question_understanding_json LONGTEXT DEFAULT NULL,
    planning_json LONGTEXT DEFAULT NULL,
    evidence_json LONGTEXT DEFAULT NULL,
    ranking_json LONGTEXT DEFAULT NULL,
    generation_json LONGTEXT DEFAULT NULL,
    quality_json LONGTEXT DEFAULT NULL,
    memory_json LONGTEXT DEFAULT NULL,
    timings_json LONGTEXT DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_orchestration_request_no UNIQUE (request_no),
    KEY idx_qa_orchestration_session_id (session_id),
    KEY idx_qa_orchestration_message_id (message_id),
    KEY idx_qa_orchestration_user_id (user_id),
    KEY idx_qa_orchestration_status (pipeline_status),
    KEY idx_qa_orchestration_stage (current_stage),
    KEY idx_qa_orchestration_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS qa_session_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    summary LONGTEXT DEFAULT NULL,
    summarized_until_message_id BIGINT DEFAULT NULL,
    recent_window_size INT NOT NULL DEFAULT 2,
    pending_overflow_count INT NOT NULL DEFAULT 0,
    memory_keys_json LONGTEXT DEFAULT NULL,
    summary_version INT NOT NULL DEFAULT 0,
    last_memory_at DATETIME DEFAULT NULL,
    last_error_message VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_qa_session_memory_session_id UNIQUE (session_id),
    KEY idx_qa_session_memory_user_id (user_id),
    KEY idx_qa_session_memory_cursor (summarized_until_message_id),
    KEY idx_qa_session_memory_updated_at (updated_at)
);

DELIMITER //

DROP PROCEDURE IF EXISTS drop_qa_answer_summary_columns//

CREATE PROCEDURE drop_qa_answer_summary_columns()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_message' AND COLUMN_NAME = 'answer_summary'
    ) THEN
        ALTER TABLE qa_message DROP COLUMN answer_summary;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_request' AND COLUMN_NAME = 'answer_summary'
    ) THEN
        ALTER TABLE qa_request DROP COLUMN answer_summary;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_qa_message_stream_columns//

CREATE PROCEDURE add_qa_message_stream_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_message' AND COLUMN_NAME = 'partial_answer'
    ) THEN
        ALTER TABLE qa_message ADD COLUMN partial_answer LONGTEXT DEFAULT NULL AFTER answer_text;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_message' AND COLUMN_NAME = 'stream_sequence'
    ) THEN
        ALTER TABLE qa_message ADD COLUMN stream_sequence INT NOT NULL DEFAULT 0 AFTER message_status;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_message' AND COLUMN_NAME = 'last_stream_at'
    ) THEN
        ALTER TABLE qa_message ADD COLUMN last_stream_at DATETIME DEFAULT NULL AFTER sequence_no;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_message' AND COLUMN_NAME = 'interrupted_reason'
    ) THEN
        ALTER TABLE qa_message ADD COLUMN interrupted_reason VARCHAR(255) DEFAULT NULL AFTER last_stream_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_message' AND INDEX_NAME = 'idx_qa_message_last_stream_at'
    ) THEN
        ALTER TABLE qa_message ADD INDEX idx_qa_message_last_stream_at (last_stream_at);
    END IF;
END//

DROP PROCEDURE IF EXISTS add_qa_orchestration_memory_column//

CREATE PROCEDURE add_qa_orchestration_memory_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qa_orchestration_trace' AND COLUMN_NAME = 'memory_json'
    ) THEN
        ALTER TABLE qa_orchestration_trace ADD COLUMN memory_json LONGTEXT DEFAULT NULL AFTER quality_json;
    END IF;
END//

DELIMITER ;

CALL drop_qa_answer_summary_columns();
DROP PROCEDURE drop_qa_answer_summary_columns;

CALL add_qa_message_stream_columns();
DROP PROCEDURE add_qa_message_stream_columns;

CALL add_qa_orchestration_memory_column();
DROP PROCEDURE add_qa_orchestration_memory_column;

CREATE TABLE IF NOT EXISTS qa_message_favorite (
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

CREATE TABLE IF NOT EXISTS qa_message_feedback (
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

CREATE TABLE IF NOT EXISTS qa_recommend_question (
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

INSERT INTO qa_recommend_question (id, question_text, question_type, sort_no, status)
VALUES (1, '什么是井壁失稳？', 'CONCEPT', 1, 1),
       (2, '钻井液密度过高会带来哪些风险？', 'MECHANISM', 2, 1),
       (3, '发生井漏时一般怎么处理？', 'PROCESS', 3, 1),
       (4, '深井条件下卡钻机理有什么差异？', 'RISK', 4, 1)
ON DUPLICATE KEY UPDATE
question_text = VALUES(question_text),
question_type = VALUES(question_type),
sort_no = VALUES(sort_no),
status = VALUES(status);
