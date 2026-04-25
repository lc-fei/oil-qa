-- 图谱类型增量脚本：用于在不重建数据库时补齐爬虫入库所需的实体类型和关系类型。
INSERT INTO kg_entity_type (id, type_name, type_code, description, status, sort_no, created_by)
VALUES (8, '工具', 'tool', '钻头、扶正器、打捞工具等井下或地面工具', 1, 8, 'system'),
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

-- 关系类型覆盖因果、适用、参数、现象、预防和处置链路，支撑后续混合导入。
INSERT INTO kg_relation_type (id, type_name, type_code, description, status, sort_no, created_by)
VALUES (6, '导致', 'causes', '原因导致故障、风险或现象的因果关系', 1, 6, 'system'),
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
