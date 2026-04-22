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
    answer_summary TEXT DEFAULT NULL,
    message_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    sequence_no INT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME DEFAULT NULL,
    CONSTRAINT uk_qa_message_no UNIQUE (message_no),
    KEY idx_qa_message_session_id (session_id),
    KEY idx_qa_message_request_no (request_no),
    KEY idx_qa_message_status (message_status),
    KEY idx_qa_message_created_at (created_at)
);

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
