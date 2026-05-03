## ADDED Requirements

### Requirement: Orchestrated AI QA pipeline

The system SHALL process each client QA request through a structured orchestration pipeline consisting of question understanding, task planning, evidence retrieval and tool execution, evidence fusion and ranking, answer generation, quality validation, and result archiving.

#### Scenario: Successful graph-enhanced question

- **WHEN** an authenticated client submits a graph-enhanced QA request
- **THEN** the system MUST create or confirm the session, create a processing message, execute the orchestration pipeline, persist the final answer, and return a successful chat result.

#### Scenario: Streaming question

- **WHEN** an authenticated client submits a streaming QA request
- **THEN** the system MUST use the same orchestration pipeline and emit SSE events without changing the persisted message and monitoring semantics.

#### Scenario: Workflow visible to client

- **WHEN** a client QA request is processed
- **THEN** the system MUST expose workflow status, stage states, tool calls, timings, and archive identifier in the chat response or SSE events.

### Requirement: Question understanding

The system SHALL call the configured LLM provider with JSON Mode enabled to produce a structured question understanding result including rewritten question, cleaned context, standardized terms, expanded queries, intent, recognized entities, complexity level, and confidence. Rule-based understanding MUST only be used as fallback when model understanding fails.

#### Scenario: Oil engineering term normalization

- **WHEN** the user question contains oil engineering aliases or non-standard terminology
- **THEN** the system MUST normalize the terminology before retrieval and preserve the original question for display and auditing.

#### Scenario: Irrelevant context removal

- **WHEN** context mode is enabled and historical context contains unrelated turns
- **THEN** the system MUST exclude irrelevant context from the retrieval and prompt-building stages.

#### Scenario: Model understanding fallback

- **WHEN** the model understanding call fails or returns invalid structured output
- **THEN** the system MUST use fallback understanding and record the model failure in the orchestration archive.

#### Scenario: JSON Mode understanding

- **WHEN** the system calls the model for question understanding
- **THEN** the request MUST require JSON object output and the system MUST validate the parsed structure before using it.

### Requirement: Task planning

The system SHALL create a planning result that determines whether graph retrieval, backend-managed internal tools, network retrieval, multi-hop retrieval, question decomposition, and subtask ordering are required. Vector retrieval MUST NOT be executed in this change and MUST be represented as disabled or not implemented when exposed.

#### Scenario: Simple definition question

- **WHEN** the question is classified as a simple definition or explanation question
- **THEN** the planner MUST prefer direct graph retrieval and skip unnecessary multi-hop or decomposition steps.

#### Scenario: Complex mechanism comparison question

- **WHEN** the question requires comparison, mechanism analysis, or multiple constraints
- **THEN** the planner MUST mark the question as complex and produce ordered subtasks for retrieval and answer generation.

#### Scenario: Vector retrieval not implemented

- **WHEN** the planner evaluates vector retrieval
- **THEN** the system MUST mark vector retrieval as not implemented and MUST NOT call any vector database.

### Requirement: Evidence retrieval and tool execution

The system SHALL execute backend-managed internal tools according to the planning result and convert all retrieval outputs into standardized evidence items. The LLM provider MUST NOT directly access Neo4j, MySQL, vector databases, or arbitrary network resources.

#### Scenario: Graph retrieval required

- **WHEN** the planner requires graph retrieval
- **THEN** the system MUST query Neo4j using the normalized question, expanded queries, and recognized entities, then return graph entities and relations as standardized evidence.

#### Scenario: Network retrieval disabled

- **WHEN** network retrieval is disabled
- **THEN** the system MUST not perform network access and MUST continue using enabled local knowledge sources.

#### Scenario: Tool call recorded

- **WHEN** the system invokes an internal retrieval tool
- **THEN** the system MUST record tool name, input summary, status, duration, output summary, and error message if any.

#### Scenario: Backend tool whitelist

- **WHEN** the planner requests a tool call
- **THEN** the system MUST allow only registered backend tools and reject unknown tool names or invalid parameters.

### Requirement: Evidence fusion and ranking

The system SHALL deduplicate evidence, detect conflicts, rerank evidence, apply source weighting, and calculate confidence scores before answer generation.

#### Scenario: Duplicate evidence

- **WHEN** multiple retrieval tools return equivalent facts or repeated graph relations
- **THEN** the ranker MUST keep one canonical evidence item and retain source metadata for traceability.

#### Scenario: Conflicting evidence

- **WHEN** retrieved evidence contains conflicting descriptions or conclusions
- **THEN** the ranker MUST mark the conflict and the generated answer MUST mention uncertainty or prefer higher-confidence sources.

### Requirement: Answer generation

The system SHALL generate answers from the user question, planning result, ranked evidence, relevant context, and system prompt.

#### Scenario: Evidence available

- **WHEN** ranked evidence is available
- **THEN** the answer MUST prioritize cited evidence over unsupported model knowledge.

#### Scenario: Evidence unavailable

- **WHEN** no reliable evidence is available
- **THEN** the answer MUST clearly indicate that the knowledge base did not provide sufficient evidence and provide a conservative response or clarification question.

### Requirement: Quality validation

The system SHALL call the configured LLM provider with JSON Mode enabled to validate whether the generated answer addresses the user question, references evidence when required, avoids unsupported claims, and needs degradation or clarification. Rule-based validation MUST only be used as fallback when model validation fails.

#### Scenario: Answer passes validation

- **WHEN** the generated answer covers the user question and aligns with ranked evidence
- **THEN** the system MUST persist the message as SUCCESS.

#### Scenario: Answer requires degradation

- **WHEN** the generated answer has insufficient evidence or high hallucination risk
- **THEN** the system MUST return a degraded answer, mark the quality conclusion, and avoid presenting unsupported claims as facts.

#### Scenario: Clarification required

- **WHEN** the question is too ambiguous to answer safely
- **THEN** the system MUST return follow-up clarification suggestions instead of forcing a definitive answer.

#### Scenario: Model validation fallback

- **WHEN** the model validation call fails or returns invalid structured output
- **THEN** the system MUST use fallback validation and record the validation failure in the orchestration archive.

#### Scenario: JSON Mode quality validation

- **WHEN** the system calls the model for quality validation
- **THEN** the request MUST require JSON object output and the system MUST validate the parsed structure before applying degradation or clarification decisions.

### Requirement: Answer streaming compatibility

The system SHALL keep final answer generation compatible with SSE natural-language streaming and MUST NOT force JSON Mode for streaming answer chunks.

#### Scenario: Streaming answer generation

- **WHEN** the system generates an answer through SSE
- **THEN** chunk events MUST contain natural-language deltas and the final done event MUST contain backend-assembled structured workflow data.

### Requirement: Result archiving and observability

The system SHALL archive final and intermediate QA results to a dedicated orchestration archive table and continue writing existing session, message, evidence, monitoring, and exception logging structures.

#### Scenario: Successful archive

- **WHEN** a QA request completes successfully
- **THEN** the system MUST persist the final answer, evidence summary, workflow stages, tool calls, timings, monitor records, archive record, and evidence source data linked by requestNo.

#### Scenario: Failed archive

- **WHEN** a QA request fails during any orchestration stage
- **THEN** the system MUST update the message and request status, write available partial workflow results, update the archive record, and create an exception log if applicable.

### Requirement: Client workflow response

The system SHALL extend chat and SSE responses with workflow fields while preserving existing response fields.

#### Scenario: Synchronous chat response

- **WHEN** synchronous chat completes
- **THEN** the response MUST include workflow status, current stage, stage list, tool call list, and archive identifier.

#### Scenario: SSE stage event

- **WHEN** a stage starts, completes, or fails during streaming chat
- **THEN** the system MUST emit a stage SSE event containing requestNo, sessionId, messageId, stage code, stage name, status, duration, and error message if any.

#### Scenario: SSE tool call event

- **WHEN** an internal tool call starts, completes, or fails during streaming chat
- **THEN** the system MUST emit a tool_call SSE event containing tool name, status, duration, input summary, output summary, and error message if any.
