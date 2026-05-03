## 1. 数据库与模型

- [x] 1.1 新增 `qa_session_memory` 表，保存 sessionId、summary、summarizedUntilMessageId、recentWindowSize、memoryKeysJson、summaryVersion、lastMemoryAt 和审计时间
- [x] 1.2 扩展 `qa_orchestration_trace`，新增 memory snapshot JSON 字段，保存本次 QA 使用的记忆快照
- [x] 1.3 更新 `schema.sql` 和增量 SQL，确保初始化库与已有库升级一致
- [x] 1.4 新增会话记忆 Entity、DTO 和 Mapper，覆盖 summary、memory keys、recent window、summary cursor 和版本字段

## 2. 记忆读取

- [x] 2.1 新增 Mapper 方法，按 sessionId/userId 查询当前会话记忆记录
- [x] 2.2 新增 Mapper 方法，查询当前会话最近 2 轮有效原文问答
- [x] 2.3 新增 Mapper 方法，查询早于最近 2 轮且晚于 `summarizedUntilMessageId` 的 pending overflow 问答
- [x] 2.4 新增 `ConversationMemoryService.buildMemoryContext`，输出 summary、memory keys、pending overflow 问答、最近 2 轮原文问答、usedMessageIds、truncated 和 memoryText
- [x] 2.5 实现 `contextMode=OFF` 空记忆兜底，确保不读取、不注入、不摘要

## 3. 记忆 key 设计与维护

- [x] 3.1 定义 `ConversationMemoryKeys` 结构，包含 currentTopic、keyEntities、userPreferences、constraints、openQuestions、lastIntent
- [x] 3.2 实现 key 合并规则：实体按名称去重，用户偏好只保留会话内显式表达，约束条件优先保留最近表达
- [x] 3.3 将问题理解结果和图谱命中实体作为 key 更新输入
- [x] 3.4 为 memory keys 提供 JSON 序列化、反序列化和异常兜底

## 4. QA 主链路接入

- [x] 4.1 扩展 `QaOrchestrationContext`，保存本次使用的 `ConversationMemoryContext`
- [x] 4.2 修改非流式 `chat` 主链路，在问题理解前读取会话记忆
- [x] 4.3 修改 SSE `streamChat` 主链路，在问题理解前读取会话记忆
- [x] 4.4 将 `memoryContext.getUnderstandingContext()` 传入 `QuestionUnderstandingService.understand(question, contextText)`
- [x] 4.5 修改 Prompt 构建逻辑，按 summary、memory keys、pending overflow 问答、最近 2 轮原文问答分区注入会话记忆

## 5. 滚动摘要维护

- [x] 5.1 新增 `ConversationMemoryService.updateAfterSuccessAsync`，在回答成功后异步维护本轮问答记忆
- [x] 5.2 当存在早于最近 2 轮且尚未进入 summary 的 pending overflow 问答时，调用阿里百炼 JSON Mode 生成新 summary 和 memory keys
- [x] 5.3 摘要成功后更新 summary、memoryKeysJson、summarizedUntilMessageId、summaryVersion；若摘要异步未完成，则继续携带 pending overflow 问答和最近 2 轮原文问答
- [x] 5.4 摘要失败时保留旧记忆状态，不影响本次 QA 成功响应或 SSE done，并记录失败原因
- [x] 5.5 确认 `FAILED` 或无有效回答的消息不会推进会话摘要游标
- [x] 5.6 确认摘要异步执行不会阻塞非流式响应或 SSE done 事件

## 6. 归档与响应

- [x] 6.1 扩展 `QaOrchestrationArchiveService`，将 memory snapshot 写入 `qa_orchestration_trace`
- [x] 6.2 评估并实现 workflow 可选 memory 字段，用于前端 Agent 面板展示当前使用的摘要和 key
- [x] 6.3 评估 evidence sources，本次不新增 `CONVERSATION_MEMORY` 来源，避免把对话记忆误展示为知识依据

## 7. 文档同步

- [x] 7.1 更新 `/Users/a123/Desktop/mds-c/用户端后端整体设计.md`，说明滚动摘要记忆方案
- [x] 7.2 更新 `/Users/a123/Desktop/mds-c/用户端接口文档.md`，说明 workflow memory 字段和可空规则
- [x] 7.3 更新 `/Users/a123/Desktop/mds-c/用户端数据库变更设计文档.md`，补充 `qa_session_memory` 和 trace memory 字段
- [x] 7.4 更新 `/Users/a123/Desktop/mds-c/主对话流式接口代码分析文档.md`，说明会话记忆读取、摘要维护和 key 设计

## 8. 验证

- [x] 8.1 执行 `./mvnw -DskipTests compile`
- [ ] 8.2 验证同一会话内连续追问可读取 summary、pending overflow 问答和最近 2 轮原文问答
- [ ] 8.3 验证不同会话之间记忆隔离
- [ ] 8.4 验证第 3 轮起每轮成功后将 pending overflow 问答异步滚入摘要，同时最近 2 轮原文仍保留
- [ ] 8.5 验证摘要异步延迟或失败不影响 QA 主响应
- [ ] 8.6 验证 `contextMode=OFF` 时不读取、不注入、不更新记忆
