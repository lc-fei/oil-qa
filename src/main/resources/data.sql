INSERT INTO sys_role (id, role_name, role_code, description, status, is_system)
VALUES (1, '超级管理员', 'SUPER_ADMIN', '拥有全部管理端权限', 1, 1),
       (2, '管理员', 'ADMIN', '拥有大部分管理端权限', 1, 1),
       (3, '普通用户', 'CLIENT_USER', '仅可访问C端功能', 1, 1)
ON DUPLICATE KEY UPDATE
role_name = VALUES(role_name),
description = VALUES(description),
status = VALUES(status),
is_system = VALUES(is_system);

INSERT INTO sys_user (id, username, account, password, phone, email, status, is_deleted)
VALUES (1, '超级管理员', 'superadmin', '$2y$10$m7Si1sqdUt7yPqDl5Hikb.8TmFzudG9iNs8FPDftehyXFka9GDF02', '13800000000', 'superadmin@oilqa.local', 1, 0),
       (2, '管理员', 'admin', '$2y$10$m7Si1sqdUt7yPqDl5Hikb.8TmFzudG9iNs8FPDftehyXFka9GDF02', '13900000000', 'admin@oilqa.local', 1, 0),
       (3, '普通用户', 'client', '$2y$10$m7Si1sqdUt7yPqDl5Hikb.8TmFzudG9iNs8FPDftehyXFka9GDF02', '13700000000', 'client@oilqa.local', 1, 0)
ON DUPLICATE KEY UPDATE
username = VALUES(username),
password = VALUES(password),
phone = VALUES(phone),
email = VALUES(email),
status = VALUES(status),
is_deleted = VALUES(is_deleted);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (1, 1, 1),
       (2, 2, 2),
       (3, 3, 3)
ON DUPLICATE KEY UPDATE
user_id = VALUES(user_id),
role_id = VALUES(role_id);

-- 图谱实体类型覆盖爬虫抽取的核心知识对象，重建数据库后仍会自动恢复。
INSERT INTO kg_entity_type (id, type_name, type_code, description, status, sort_no, created_by)
VALUES (1, '油井', 'oil_well', '油井实体类型', 1, 1, 'system'),
       (2, '井段', 'well_section', '井段实体类型', 1, 2, 'system'),
       (3, '地层', 'formation', '地层实体类型', 1, 3, 'system'),
       (4, '设备', 'equipment', '设备实体类型', 1, 4, 'system'),
       (5, '工艺', 'process', '工艺实体类型', 1, 5, 'system'),
       (6, '故障类型', 'fault_type', '故障类型实体类型', 1, 6, 'system'),
       (7, '处理方案', 'solution', '处理方案实体类型', 1, 7, 'system'),
       (8, '工具', 'tool', '钻头、扶正器、打捞工具等井下或地面工具', 1, 8, 'system'),
       (9, '施工参数', 'construction_parameter', '钻压、转速、泵压、排量、密度等施工参数', 1, 9, 'system'),
       (10, '作业规范', 'specification', '标准、规程、操作要求等规范类知识', 1, 10, 'system'),
       (11, '现象', 'phenomenon', '井漏、卡钻、井壁坍塌、泵压异常等现场表现', 1, 11, 'system'),
       (12, '原因', 'cause', '导致故障、风险或现象的原因类知识', 1, 12, 'system'),
       (13, '风险', 'risk', '井控风险、井壁失稳风险、卡钻风险等风险类知识', 1, 13, 'system'),
       (14, '材料介质', 'material', '钻井液、堵漏材料、水泥浆、加重剂等材料或介质', 1, 14, 'system'),
       (15, '作业阶段', 'operation_stage', '一开、二开、固井、完井、起下钻等作业阶段', 1, 15, 'system'),
       (16, '地质条件', 'geological_condition', '高压层、破碎地层、盐膏层、裂缝性地层等地质条件', 1, 16, 'system'),
       (17, '指标评价项', 'indicator', '密度窗口、坍塌压力、破裂压力、摩阻扭矩等评价指标', 1, 17, 'system')
ON DUPLICATE KEY UPDATE
type_name = VALUES(type_name),
description = VALUES(description),
status = VALUES(status),
sort_no = VALUES(sort_no),
created_by = VALUES(created_by);

-- 图谱关系类型用于表达爬虫抽取后的因果、适用、参数和处置链路。
INSERT INTO kg_relation_type (id, type_name, type_code, description, status, sort_no, created_by)
VALUES (1, '属于', 'belongs_to', '归属关系', 1, 1, 'system'),
       (2, '使用', 'uses', '使用关系', 1, 2, 'system'),
       (3, '包含', 'contains', '包含关系', 1, 3, 'system'),
       (4, '影响', 'affects', '影响关系', 1, 4, 'system'),
       (5, '解决', 'solves', '解决关系', 1, 5, 'system'),
       (6, '导致', 'causes', '原因导致故障、风险或现象的因果关系', 1, 6, 'system'),
       (7, '位于', 'located_in', '实体或现象发生位置关系', 1, 7, 'system'),
       (8, '适用于', 'applies_to', '工艺、方案或规范适用场景关系', 1, 8, 'system'),
       (9, '依赖于', 'depends_on', '工艺、方案对参数、设备或条件的依赖关系', 1, 9, 'system'),
       (10, '映射到', 'maps_to', '现象、别名或外部概念到标准实体的映射关系', 1, 10, 'system'),
       (11, '具有参数', 'has_parameter', '工艺、设备或方案关联施工参数', 1, 11, 'system'),
       (12, '表现为', 'has_phenomenon', '故障或风险对应现场现象', 1, 12, 'system'),
       (13, '预防', 'prevents', '措施或方案对风险、故障的预防关系', 1, 13, 'system'),
       (14, '处理', 'handles', '处理方案或措施面向故障、风险、现象的处置关系', 1, 14, 'system'),
       (15, '评价依据', 'evaluated_by', '风险、方案或工况对应评价指标关系', 1, 15, 'system'),
       (16, '受限于', 'constrained_by', '工艺、方案受规范或条件约束的关系', 1, 16, 'system'),
       (17, '发生于', 'occurs_in', '故障、风险或现象发生于作业阶段、井段或地层', 1, 17, 'system')
ON DUPLICATE KEY UPDATE
type_name = VALUES(type_name),
description = VALUES(description),
status = VALUES(status),
sort_no = VALUES(sort_no),
created_by = VALUES(created_by);

INSERT INTO kg_version (id, version_no, version_remark, created_by)
VALUES (1, 'v1.0.0', '初始图谱数据', 'system')
ON DUPLICATE KEY UPDATE
version_remark = VALUES(version_remark),
created_by = VALUES(created_by);

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
