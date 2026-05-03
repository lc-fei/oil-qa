## Context

当前用户端问答由 `ClientQaServiceImpl` 串行完成：创建会话和消息、规则 NLP、Neo4j 图谱检索、Prompt 拼接、阿里百炼调用、消息回写和监控落库。该实现能支撑基础问答，但无法向前端展示完整执行过程，也缺少可审计的阶段轨迹、工具调用记录、大模型问题理解和大模型质量校验。

本变更将现有线性链路升级为专业方向 Agent 编排链路。外部仍以 `/api/client/qa/chat` 和 SSE 问答接口为主入口，但响应结构需要新增流程状态、阶段耗时、工具调用和归档标识，以便前端展示“本次 QA 正在做什么、调用了哪些工具、最终依据和质量结论是什么”。

## Goals / Non-Goals

**Goals:**

- 将问答主链路拆分为问题理解、任务规划、检索工具、证据融合排序、答案生成、质量校验和结果归档七个阶段。
- 问题理解和质量校验调用阿里百炼大模型完成，避免继续依赖过于粗糙的规则 NLP。
- 使用 Neo4j 图谱作为本次实现的主要专业知识源，网络查询默认关闭。
- 新增专门的问答编排归档表，保存完整阶段轨迹、工具调用、证据、质量结论、耗时、错误和最终答案。
- 扩展同步 chat 和 SSE 响应，让前端可以展示 QA 流程状态和工具调用过程。
- 保留现有会话、消息、知识依据、监控和异常日志链路，避免破坏管理端观测能力。

**Non-Goals:**

- 本变更不实现向量库检索，只在 planner 和证据模型中保留扩展边界。
- 本变更不默认启用网络查询；即使模型接口支持搜索参数，也必须通过配置和 planner 双重控制。
- 本变更不更换阿里百炼模型提供方。
- 本变更不把单体应用拆成微服务。

## Decisions

### Decision 1: 使用阶段化编排上下文承载一次 QA 全链路

新增 `QaOrchestrationContext`，贯穿一次问答请求，保存用户、会话、消息、requestNo、traceId、原始问题、上下文、问题理解、任务规划、工具调用、标准证据、排序结果、生成结果、质量结论、阶段耗时和错误信息。

理由：当前主链路依赖散装 `Map<String, Object>`，字段不可控，无法稳定归档或返回给前端。结构化上下文可以让编排器、归档服务和响应组装都读取同一份事实。

备选方案：继续在 `ClientQaServiceImpl` 中扩展 Map。该方案短期改动小，但会让工具调用、UI 状态和归档字段持续失控。

### Decision 2: 问题理解调用大模型，规则只作为兜底

`QuestionUnderstandingService` 调用阿里百炼模型，要求模型输出结构化 JSON：

- `rewrittenQuestion`
- `cleanedContext`
- `standardTerms`
- `expandedQueries`
- `intent`
- `entities`
- `complexity`
- `confidence`
- `reasoningSummary`

规则算法只在模型调用失败、返回 JSON 无法解析或耗时超限时兜底。

理由：问题重写、无关上下文剔除、术语标准化、意图识别和复杂度识别属于语义理解问题，规则实现容易漏召回或误判。调用模型更符合专业 Agent 流程。

备选方案：继续规则 NLP。该方案稳定、便宜，但能力上限不足。

### Decision 3: planner 本次不规划向量库

`QaPlannerService` 只规划以下能力：

- 是否需要图谱。
- 是否需要工具。
- 是否需要多跳查询。
- 是否需要问题分解。
- 是否允许网络查询。
- 子任务执行顺序。

向量库本次不进入执行路径，planner 输出中可包含 `vectorRequired=false` 和 `vectorReason="NOT_IMPLEMENTED"`，用于前端或归档展示“本版本未启用向量库”。

理由：向量库需要文档切分、embedding、索引维护、召回评估和权限治理，不适合混在本次主链路重构中实现。先把编排边界做正确，后续单独扩展向量库能力。

### Decision 4: Tool call 采用后端托管执行，本次不依赖百炼原生 function calling

本变更中的 `tool call` 指“后端内部工具调用记录”，不是让百炼模型直接执行工具。当前版本采用后端 planner 决策和后端工具执行：

1. 问题理解阶段调用百炼模型输出结构化理解结果。
2. 后端 `QaPlannerService` 根据理解结果生成执行计划。
3. 后端从工具白名单中选择并执行内部工具，例如 `graph_search`。
4. 工具执行结果被标准化为 evidence item。
5. 工具调用过程写入 `qa_orchestration_trace.tool_calls_json`，并在 SSE 中通过 `tool_call` 事件推送。

本次明确落地的工具：

- `graph_search`：查询 Neo4j 知识图谱，输入为标准化问题、扩展查询词、识别实体和多跳标记。

本次不落地的工具：

- `vector_search`：向量库本次不实现。
- `web_search`：网络查询默认关闭；即使后续启用，也必须由后端实现白名单工具，不允许模型直接任意联网。

理由：后端托管工具调用更利于权限控制、参数校验、超时控制、审计归档和前端过程展示。百炼原生 function calling 可以作为后续优化，但本次不把它作为主路径，避免模型工具协议、流式事件和内部工具执行同时重构导致风险过高。

备选方案：使用百炼原生 `tools/function calling` 让模型决定工具调用。该方案更接近通用 Agent 框架，但实现复杂度更高，且仍然需要后端执行工具和回填工具结果。

### Decision 5: 阿里百炼能力按阶段复用，并对结构化阶段启用 JSON Mode

本变更继续使用阿里百炼 OpenAI 兼容接口：

- 问题理解：非流式模型调用，要求 JSON 输出。
- 答案生成：同步接口或流式接口，SSE 场景只在答案生成阶段向前端输出 token/chunk。
- 质量校验：非流式模型调用，要求 JSON 输出质量结论。
- 联网搜索：即使百炼接口支持搜索参数，也默认关闭，必须配置启用后由 planner 显式允许。

问题理解、planner 辅助判断和质量校验这些“结构化输出阶段”必须启用 JSON Mode：

- 请求体设置 `response_format: {"type":"json_object"}`。
- system/user prompt 必须包含 `JSON/json` 关键词。
- 不开启 thinking 模式，避免与 JSON Mode 冲突。
- 不主动设置过小的 `max_tokens`，避免 JSON 被截断。
- 服务端必须做 JSON schema 校验；解析失败时重试一次，仍失败则走规则兜底。

答案生成阶段分两种：

- 同步 chat：可以让模型直接输出自然语言答案，不强制 JSON。
- SSE chat：不启用 JSON Mode，保持自然语言 token/chunk 流式输出；最终 `done` 事件由后端根据编排上下文组装结构化 JSON。

理由：JSON Mode 能显著降低问题理解和质量校验的解析成本，但最终答案如果强制 JSON，会破坏前端逐 token 渲染体验，也会让用户看到不自然的 JSON 片段。SSE 的结构化数据应由后端事件层承载，而不是要求模型把最终回答包装成 JSON。

### Decision 6: 新增问答编排归档表

新增 `qa_orchestration_trace` 表，按 `request_no` 唯一关联一次问答。建议核心字段：

- `id`
- `request_no`
- `session_id`
- `message_id`
- `user_id`
- `pipeline_status`
- `current_stage`
- `stage_trace_json`
- `tool_calls_json`
- `question_understanding_json`
- `planning_json`
- `evidence_json`
- `ranking_json`
- `generation_json`
- `quality_json`
- `timings_json`
- `error_message`
- `created_at`
- `updated_at`

`stage_trace_json` 保存阶段列表，供前端展示流程状态；`tool_calls_json` 保存工具名称、入参摘要、状态、耗时和结果摘要；其他 JSON 字段保存各阶段结构化结果。

理由：现有监控表偏运维观测，不适合承载完整 Agent 轨迹。单独归档表可以支撑前端过程展示、知识依据回放、问题排查和后续评估。

备选方案：继续写入现有 `qa_request`、`qa_nlp_record`、`qa_graph_record` 等表。该方案无需建表，但字段分散且无法完整表达阶段状态。

### Decision 7: 主接口响应新增 workflow 和 toolCalls

同步 chat 最终响应新增：

- `workflow.traceId`
- `workflow.status`
- `workflow.currentStage`
- `workflow.stages`
- `workflow.toolCalls`
- `workflow.archiveId`

SSE 新增阶段事件：

- `stage`：阶段开始、完成、失败。
- `tool_call`：工具调用开始、完成、失败。
- `chunk`：答案生成增量内容。
- `done`：最终结果，包含完整 `workflow`。
- `error`：失败结果，包含已完成阶段和错误信息。

理由：前端需要展示流程状态和工具调用，仅在最终 evidence 接口中查看依据不够。响应结构新增字段属于向后兼容扩展，旧客户端可忽略。

### Decision 8: 质量校验调用大模型并决定降级策略

`AnswerQualityService` 调用阿里百炼模型，输入原始问题、重写问题、规划结果、排序证据和生成答案，输出：

- `answeredQuestion`
- `evidenceReferenced`
- `hallucinationRisk`
- `needsDegradation`
- `needsClarification`
- `suggestedStatus`
- `qualityScore`
- `reviewNotes`

若质量校验失败但可安全回答，则返回降级回答；若问题歧义过高，则返回追问建议；若模型调用失败，则使用规则兜底，不阻断已经可用的答案归档。

## Risks / Trade-offs

- [Risk] 多次模型调用导致延迟上升 → Mitigation：问题理解和质检使用非流式短 prompt；SSE 先返回阶段事件；可通过配置关闭部分高级阶段。
- [Risk] 模型 JSON 输出不稳定 → Mitigation：强制 JSON prompt、解析校验、失败后规则兜底，并将错误写入归档表。
- [Risk] 新响应字段导致前端/SDK 类型变更 → Mitigation：新增字段保持可选，旧字段不删除；接口文档同步更新。
- [Risk] 归档 JSON 字段过大 → Mitigation：只保存摘要和结构化核心字段，原始大文本按长度截断。
- [Risk] 工具调用被模型误用 → Mitigation：工具注册白名单、参数校验、超时控制、结果摘要化、全量记录 `tool_calls_json`。
- [Risk] 网络查询带来合规和不稳定风险 → Mitigation：默认关闭，并要求配置开关和 planner 同时允许。

## Migration Plan

1. 新增 `qa_orchestration_trace` 表、entity、mapper 和初始化/增量 SQL。
2. 新增编排上下文、阶段 DTO、workflow 响应 DTO 和工具调用 DTO。
3. 新增问题理解、planner、检索、ranker、答案生成、质量校验、归档服务。
4. 将现有图谱检索和百炼调用迁移到阶段服务，保留原接口入口。
5. 改造同步 chat 响应和 SSE 事件，新增流程状态与工具调用数据。
6. 归档服务同时写新归档表、现有消息表、监控表和异常日志表。
7. 更新用户端接口文档、后端设计文档和数据库设计文档。
8. 编译并验证同步问答、SSE 问答、取消生成、知识依据和监控链路。

## Open Questions

- 后续是否要升级为百炼原生 `tools/function calling` 决策工具调用，还是长期保持后端 planner 决策工具调用。
- 如果后续启用网络查询，是否只允许百炼搜索参数，还是实现独立的后端 `web_search` 工具。
- 问题理解、planner 和质量校验是否使用同一个模型配置，还是拆分为低延迟理解模型与高质量生成模型。
- 前端流程 UI 是否需要展示模型理解结果的详细内容，还是只展示阶段状态和工具调用摘要。
