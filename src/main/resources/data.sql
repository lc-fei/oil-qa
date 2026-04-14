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
