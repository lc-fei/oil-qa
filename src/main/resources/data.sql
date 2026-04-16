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

INSERT INTO kg_entity_type (id, type_name, type_code, description, status, sort_no, created_by)
VALUES (1, '油井', 'oil_well', '油井实体类型', 1, 1, 'system'),
       (2, '井段', 'well_section', '井段实体类型', 1, 2, 'system'),
       (3, '地层', 'formation', '地层实体类型', 1, 3, 'system'),
       (4, '设备', 'equipment', '设备实体类型', 1, 4, 'system'),
       (5, '工艺', 'process', '工艺实体类型', 1, 5, 'system'),
       (6, '故障类型', 'fault_type', '故障类型实体类型', 1, 6, 'system'),
       (7, '处理方案', 'solution', '处理方案实体类型', 1, 7, 'system')
ON DUPLICATE KEY UPDATE
type_name = VALUES(type_name),
description = VALUES(description),
status = VALUES(status),
sort_no = VALUES(sort_no),
created_by = VALUES(created_by);

INSERT INTO kg_relation_type (id, type_name, type_code, description, status, sort_no, created_by)
VALUES (1, '属于', 'belongs_to', '归属关系', 1, 1, 'system'),
       (2, '使用', 'uses', '使用关系', 1, 2, 'system'),
       (3, '包含', 'contains', '包含关系', 1, 3, 'system'),
       (4, '影响', 'affects', '影响关系', 1, 4, 'system'),
       (5, '解决', 'solves', '解决关系', 1, 5, 'system')
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
