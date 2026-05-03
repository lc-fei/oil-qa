## 1. 数据库与归档结构

- [x] 1.1 设计并新增 `qa_orchestration_trace` 表，保存 requestNo、会话、消息、用户、流程状态、阶段轨迹、工具调用、各阶段 JSON、耗时和错误信息
- [x] 1.2 更新 `schema.sql`、增量 SQL 和用户端数据库设计文档，确保重启初始化与手动升级脚本一致
- [x] 1.3 新增归档表 entity、mapper、DTO，并提供按 requestNo/messageId 查询归档记录的能力

## 2. 编排基础结构

- [x] 2.1 新增 `QaOrchestrationContext` 和阶段结果 DTO，覆盖问题理解、规划、证据、排序、生成、质检、归档和耗时
- [x] 2.2 新增 workflow 响应 DTO、阶段状态 DTO、工具调用 DTO，供 chat 响应和 SSE 事件复用
- [x] 2.3 新增统一问答编排器，负责串联七阶段流程，并把阶段状态写入上下文和归档表

## 3. 大模型问题理解与任务规划

- [x] 3.1 实现问题理解服务，调用阿里百炼 JSON Mode 输出结构化 JSON，包含问题重写、上下文剔除、术语标准化、查询扩展、意图、实体、复杂度和置信度
- [x] 3.2 实现问题理解 JSON schema 校验与兜底规则，模型失败或 JSON 无法解析时仍可继续问答并记录失败原因
- [x] 3.3 实现后端 planner，判断是否需要图谱、后端内部工具、网络查询、多跳查询、问题分解和子任务顺序；向量库必须标记为本次不实现且不执行
- [x] 3.4 将问题理解和 planner 结果写入归档表，并继续写入现有 NLP 监控记录

## 4. 检索、工具调用与证据排序

- [x] 4.1 新增标准证据模型，将图谱实体、关系和属性摘要转换为统一 evidence item
- [x] 4.2 将现有 Neo4j 检索逻辑迁移到图谱检索工具，支持 normalized question、expanded queries 和 recognized entities
- [x] 4.3 建立后端内部工具调用白名单、参数校验、超时控制和 toolCalls 记录；本次不依赖百炼原生 function calling；网络查询默认关闭
- [x] 4.4 实现 evidence ranker 默认规则，支持去重、冲突标记、来源权重、rerank 分数和可信度评分

## 5. 答案生成与大模型质量校验

- [x] 5.1 改造 Prompt 构建逻辑，使用 planner、排序证据、相关上下文、原始问题和质量约束生成模型输入
- [x] 5.2 保留阿里百炼同步与流式调用能力，SSE 的 chunk 只在答案生成阶段输出，最终答案流式阶段不得强制 JSON Mode
- [x] 5.3 实现质量校验服务，调用阿里百炼 JSON Mode 输出覆盖性、证据引用、幻觉风险、降级、追问和质量分
- [x] 5.4 实现质量校验 JSON schema 校验与兜底规则，模型失败时不丢失可用答案，并把失败原因写入归档表

## 6. 接口响应与 SSE 事件

- [x] 6.1 扩展 `/api/client/qa/chat` 响应结构，新增 workflow、stages、toolCalls、archiveId 等字段，同时保留旧字段
- [x] 6.2 扩展 SSE 事件，新增 `stage` 和 `tool_call` 事件，并在 `done`/`error` 事件中返回完整 workflow
- [x] 6.3 更新用户端接口文档、Rust SDK 设计方案和前端设计方案，明确新增字段、事件格式和可空规则

## 7. 归档、监控与验证

- [x] 7.1 新增归档服务，统一写入归档表、消息表、会话表、监控表、知识依据数据和异常日志
- [x] 7.2 确保 SUCCESS、FAILED、PARTIAL_SUCCESS、INTERRUPTED 和 NEED_CLARIFICATION 等状态能稳定归档并返回
- [x] 7.3 核查 `/api/client/qa/chat`、SSE chat、取消生成、会话详情和知识依据接口响应是否符合更新后的接口文档
- [x] 7.4 执行编译和必要测试，重点验证消息落库、归档表、知识依据、监控记录和异常日志闭环
