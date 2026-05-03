## Context

当前 C 端问答的记忆能力在 `ClientQaServiceImpl.appendConversationHistory()` 中实现：先查询当前会话全部消息，再取最后 3 条问答拼接进 Prompt。该实现只解决了最简单的追问场景，但无法在长会话中稳定保留早期信息，也无法表达“当前讨论主题、关键实体、用户在本会话中表达的偏好或约束”。

本变更将记忆模块升级为“会话内滚动摘要记忆”：

```text
当前会话记忆 = 会话滚动摘要 + pending overflow 问答 + 最近 2 轮原文问答 + 结构化记忆 key
```

记忆只存在于当前会话，不跨会话，不形成用户长期画像。每次问答都会读取该记忆并注入问题理解和 Prompt。回答成功后，系统异步维护摘要和 key，但始终保留最近 2 轮原文问答；如果摘要异步任务尚未完成，则把尚未进入摘要的溢出问答一并携带，避免上下文丢失。

## Goals / Non-Goals

**Goals:**

- 新增会话记忆服务，统一管理当前会话内的滚动摘要、pending overflow 问答、最近 2 轮原文问答和记忆 key。
- 每次 C 端问答都把会话记忆传入问题理解和 Prompt。
- 回答成功后维护记忆：保存本轮问答；第 3 轮起每轮将“更早的溢出问答”滚入摘要，最近 2 轮继续保留原文。
- 设计记忆 key，覆盖关键实体、当前主题、用户个性化偏好、约束条件、待澄清问题等。
- 避免长会话全量拼接，控制 Prompt 长度。
- workflow/归档记录本次使用的记忆快照。

**Non-Goals:**

- 不做管理端配置持久记忆。
- 不做跨会话长期记忆。
- 不做用户画像或用户全局偏好。
- 不接向量库或 embedding。
- 不让模型直接写数据库；模型只输出结构化摘要建议，后端校验后保存。

## Decisions

### Decision 1: 记忆边界限定为当前会话

记忆只按 `sessionId` 生效，同一用户的不同会话之间不共享记忆。

理由：当前目标是增强追问和长会话连续性，不是建立用户长期画像。会话内记忆更安全、可控，也更容易解释。

备选方案：用户级长期记忆。暂不采用，因为涉及隐私、删除权、偏好污染和跨主题干扰。

### Decision 2: 采用 rolling summary memory

每次问答使用：

```text
memory = summary + pending overflow turns + recent raw turns + memory keys
```

核心字段建议：

- `session_id`
- `summary`
- `summarized_until_message_id`
- `recent_window_size`
- `last_summarized_message_id`
- `pending_overflow_count`
- `memory_keys_json`
- `summary_version`
- `last_memory_at`
- `created_at`
- `updated_at`

推荐新增独立表 `qa_session_memory`，而不是把字段直接塞进 `qa_session`。

理由：记忆字段会继续演进，独立表便于后续扩展摘要版本、key 结构和重建任务，也避免污染会话主表。

### Decision 3: 始终保留最近 2 轮原文问答

当前系统一条 `qa_message` 同时保存 `question_text` 和 `answer_text`，因此一条成功消息就是一轮问答。

默认窗口：

```text
recent_window_size = 2
```

记忆读取时：

```text
用户发送问题
  -> 读取旧 summary + pending overflow 问答 + 最近 2 轮原文问答 + memory keys
  -> 生成回答
  -> 保存本轮 question/answer
  -> 维护摘要和 memory keys
```

摘要覆盖范围：

```text
summary 覆盖：当前会话中已经完成摘要的更早内容
pending overflow turns 覆盖：摘要异步任务未完成时，摘要游标之后且早于最近 2 轮的内容
recent raw turns 覆盖：最近 2 轮原始 question/answer
```

理由：最近 2 轮原文对追问、指代消解最重要，不能因为摘要成功就清零。摘要负责压缩更早的历史；摘要未完成的溢出轮次必须暂时保留在 Prompt 上下文中。

### Decision 4: 第 4 轮起每轮成功后更新摘要

摘要维护不是“累计到超过窗口后把窗口全部清空”，而是滑动窗口策略：

```text
第 1-2 轮：summary 可以为空，Prompt 使用最近原文即可
第 3 轮：用旧 summary + 第 1 轮生成新 summary，最近原文保留第 2-3 轮
第 4 轮：用旧 summary + 第 2 轮生成新 summary，最近原文保留第 3-4 轮
第 N 轮：summary 覆盖 1..N-2 轮，最近原文保留 N-1..N 轮
```

这样不会出现“未摘要会话为 0”的空窗期。

摘要游标含义：

```text
summarized_until_message_id = 已经进入 summary 的最后一轮消息 ID
```

每次成功回答后，系统计算“应该进入摘要但尚未进入摘要”的溢出问答：

```text
overflow turns = effective turns before latest 2 and after summarized_until_message_id
```

如果 overflow turns 非空，则异步生成新摘要；在摘要完成前，构建记忆时必须携带这些 pending overflow turns，不能因为摘要未完成而丢失上下文。

pending overflow 携带规则：

```text
本次 Prompt 记忆 = summary + pending overflow turns + latest 2 raw turns + memory keys
```

如果 pending overflow 过长，则按时间顺序保留并做总长度截断，同时在归档中记录 `truncated=true`。

### Decision 5: 摘要更新默认不阻塞核心对话响应

摘要生成会调用阿里百炼 JSON Mode，属于额外模型调用。如果同步阻塞核心链路：

- 非流式接口会延迟最终 HTTP 响应。
- SSE 接口会延迟 `done` 事件。
- 摘要模型失败会增加主链路异常复杂度。

因此默认策略：

```text
回答生成成功
  -> 消息、监控、workflow 先完成
  -> 返回非流式响应或发送 SSE done
  -> 后台异步维护会话摘要
```

如果下一次用户提问发生在摘要完成前：

- 系统仍使用旧 summary。
- 最近 2 轮原文仍然包含最新上下文。
- 如存在摘要未完成的溢出轮次，必须随本次记忆上下文一并携带，并受总长度限制。

理由：摘要是记忆维护，不应影响核心问答 SLA；但摘要未完成也不能丢上下文，因此需要携带 pending overflow turns 作为兜底。

### Decision 6: 记忆 key 采用结构化 JSON

`memory_keys_json` 建议结构：

```json
{
  "currentTopic": "井壁失稳机理",
  "keyEntities": [
    {"name": "井壁失稳", "type": "risk"},
    {"name": "钻井液密度", "type": "parameter"}
  ],
  "userPreferences": [
    "希望回答偏工程实践",
    "希望解释机理差异"
  ],
  "constraints": [
    "限定深井条件"
  ],
  "openQuestions": [
    "尚未明确地层类型"
  ],
  "lastIntent": "MECHANISM_ANALYSIS"
}
```

key 来源：

- 问题理解结果中的 entities、intent、complexity。
- 图谱命中的实体和关系。
- 模型摘要阶段输出的结构化 key。
- 后端规则校验后的合并结果。

合并原则：

- key 只在当前会话有效。
- 同名实体去重。
- 用户偏好必须来自本会话显式表达，不从模型猜测。
- 约束条件优先保留最近表达。
- openQuestions 可被后续回答或用户补充清除。

### Decision 7: 摘要生成使用大模型 JSON Mode，但必须有规则兜底

摘要更新输入：

- 旧 summary。
- 溢出问答列表，也就是早于最近 2 轮且尚未进入 summary 的问答。
- 当前 memory keys。

模型输出 JSON：

- `summary`
- `currentTopic`
- `keyEntities`
- `userPreferences`
- `constraints`
- `openQuestions`
- `lastIntent`
- `summaryNotes`

如果模型失败：

- 保留旧 summary。
- 不移动 `summarized_until_message_id`。
- 不修改 recent window。
- 记录失败原因，但不影响本次问答成功。

理由：记忆维护是辅助能力，不能反向破坏 QA 主链路。

### Decision 8: 问题理解阶段必须读取会话记忆

当前调用：

```java
questionUnderstandingService.understand(question, "")
```

应改为：

```java
questionUnderstandingService.understand(question, memoryContext.getUnderstandingContext())
```

其中 understanding context 包含：

- 当前主题。
- 关键实体。
- 最近 2 轮原文问答。
- 必要摘要。

理由：追问改写、指代消解、无关上下文剔除都应该在问题理解阶段发生，而不是只依赖最终生成模型。

### Decision 9: Prompt 注入使用明确分区

Prompt 建议结构：

```text
会话记忆摘要：
{summary}

会话记忆 key：
- 当前主题：...
- 关键实体：...
- 用户偏好：...
- 约束条件：...
- 待澄清问题：...

最近 2 轮原文对话：
用户：...
助手：...

当前问题：
{question}
```

优先级：

1. 当前用户问题。
2. 图谱证据。
3. 当前会话记忆。
4. 模型通用知识。

如果会话记忆和当前问题冲突，以当前问题为准；如果会话记忆和图谱证据冲突，以图谱证据为准，并提示不确定性。

### Decision 10: 本次使用的记忆必须归档

归档内容：

- `summarySnapshot`
- `memoryKeysSnapshot`
- `usedMessageIds`
- `summarizedUntilMessageId`
- `recentWindowSize`
- `pendingOverflowTurnCount`
- `truncated`

可选实现：

- 优先扩展 `qa_orchestration_trace` 增加 `memory_json` 字段。
- 若不想改表，可暂存在现有 `timings_json` 之外的合适 JSON 字段不推荐。

理由：模型回答会受记忆影响，必须能回放“当时带入了什么记忆”。

## Risks / Trade-offs

- [Risk] 摘要错误导致后续回答偏离 → Mitigation：始终保留最近 2 轮原文问答，摘要失败不推进游标，Prompt 中当前问题和图谱证据优先。
- [Risk] 用户偏好被模型臆造 → Mitigation：只保存本会话明确表达的偏好，后端校验空泛偏好。
- [Risk] Prompt 过长 → Mitigation：限制 summary、key 和最近问答长度，超过则截断并归档 truncated=true。
- [Risk] 摘要模型调用增加延迟 → Mitigation：摘要默认在回答成功后异步执行，不阻塞非流式响应或 SSE done；失败不影响回答结果。
- [Risk] 记忆状态并发更新冲突 → Mitigation：按 sessionId 更新，可使用 version 或 updated_at 做乐观锁。

## Migration Plan

1. 新增 `qa_session_memory` 表和增量 SQL。
2. 新增会话记忆 Entity、DTO、Mapper 和 Service。
3. 新增最近 2 轮有效消息查询和 pending overflow 问答查询方法。
4. 修改非流式和 SSE 主链路，在问题理解前读取会话记忆。
5. 修改 Prompt 构建逻辑，注入 summary、memory keys、pending overflow 问答和最近 2 轮原文问答。
6. 回答成功后异步调用记忆维护逻辑，将早于最近 2 轮的 pending overflow 问答滚入摘要。
7. 扩展 `qa_orchestration_trace` 保存 memory snapshot。
8. 更新用户端文档和流式分析文档。
9. 编译验证，并用连续追问、长会话、摘要异步延迟、摘要失败、contextMode=OFF 场景联调。

## Open Questions

- 异步摘要任务是否需要任务状态表，还是先使用应用内线程池。
- 最近原文窗口是否固定为 2 轮，还是配置化。
- workflow 是否要把 memory keys 返回给前端 Agent 面板。
- 是否需要管理端查看某个会话的 memory summary，用于调试。
