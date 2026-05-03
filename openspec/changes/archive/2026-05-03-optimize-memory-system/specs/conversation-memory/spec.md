## ADDED Requirements

### Requirement: Session-scoped conversation memory
The system SHALL maintain conversation memory only within the current QA session and MUST NOT share it across sessions.

#### Scenario: Same session memory
- **WHEN** a user continues asking questions in the same session
- **THEN** the system MUST use the memory maintained for that session

#### Scenario: Different session isolation
- **WHEN** the same user starts or opens another session
- **THEN** the system MUST NOT use memory from the previous session

### Requirement: Rolling summary memory
The system SHALL represent session memory as rolling summary, pending overflow turns, recent raw turns, and structured memory keys.

#### Scenario: Build memory for QA
- **WHEN** a C-end QA request is processed with context mode enabled
- **THEN** the system MUST build memory from the current session summary, pending overflow turns, the latest configured raw QA turns, and memory keys

#### Scenario: Context mode disabled
- **WHEN** a C-end QA request is processed with context mode disabled
- **THEN** the system MUST skip memory loading and Prompt memory injection

### Requirement: Memory participates in question understanding
The system SHALL pass conversation memory context to the question understanding stage.

#### Scenario: Follow-up question
- **WHEN** the user asks a follow-up question such as “它的原因是什么”
- **THEN** the question understanding stage MUST receive session memory so it can rewrite or interpret the question with context

#### Scenario: Empty memory
- **WHEN** no session memory exists
- **THEN** the question understanding stage MUST process the original question normally

### Requirement: Memory participates in Prompt construction
The system SHALL inject session memory into the answer-generation Prompt using clear sections and bounded length.

#### Scenario: Memory injected into Prompt
- **WHEN** session memory is available
- **THEN** the Prompt MUST include sections for summary, pending overflow turns, memory keys, and recent raw turns

#### Scenario: Memory length bounded
- **WHEN** session memory exceeds configured text limits
- **THEN** the system MUST truncate injected memory deterministically and mark the memory context as truncated

### Requirement: Memory keys are structured
The system SHALL maintain structured memory keys for the current session.

#### Scenario: Key entities recorded
- **WHEN** question understanding or graph retrieval identifies relevant entities
- **THEN** the system MUST be able to store key entities in session memory

#### Scenario: User preferences recorded
- **WHEN** the user explicitly states a preference within the session
- **THEN** the system MUST be able to store that preference as session-scoped memory

#### Scenario: Constraints recorded
- **WHEN** the user states constraints such as operating condition, scenario, or answer style within the session
- **THEN** the system MUST be able to store those constraints as session-scoped memory keys

### Requirement: Memory summary maintenance
The system SHALL maintain a rolling summary after successful QA turns while preserving the latest configured raw turns.

#### Scenario: Overflow turns exist
- **WHEN** a successful QA turn creates turns that are older than the latest configured raw-turn window and not yet summarized
- **THEN** the system MUST generate a new summary from the previous summary and those overflow turns

#### Scenario: Summary succeeds
- **WHEN** summary generation succeeds
- **THEN** the system MUST update summary, memory keys, summarized cursor, summary version, and keep the latest configured raw turns available as raw memory

#### Scenario: Summary fails
- **WHEN** summary generation fails
- **THEN** the system MUST keep the previous memory state, MUST NOT advance the summarized cursor, MUST keep recent raw turns available, and MUST NOT fail the completed QA response

#### Scenario: Summary does not block answer
- **WHEN** summary generation is pending or slow after a successful QA turn
- **THEN** the system MUST NOT delay the non-streaming chat response or SSE done event solely for summary generation

#### Scenario: Pending overflow is still carried
- **WHEN** summary generation has not yet incorporated overflow turns
- **THEN** the system MUST include those pending overflow turns in subsequent memory contexts subject to configured length limits

### Requirement: Memory usage is archived
The system SHALL record the memory snapshot used by each QA request in the orchestration archive.

#### Scenario: Memory snapshot archived
- **WHEN** a QA request uses session memory
- **THEN** the system MUST record summary snapshot, memory keys snapshot, used message identifiers, summarized cursor, recent window size, pending overflow count, and truncated flag

#### Scenario: Memory absent
- **WHEN** no session memory is used
- **THEN** the workflow and archive MUST remain valid and indicate an empty memory context

### Requirement: Existing C-end API compatibility
The system SHALL keep existing C-end QA request parameters compatible while improving memory behavior internally.

#### Scenario: Existing client request
- **WHEN** an existing client calls `POST /api/client/qa/chat` or `POST /api/client/qa/chat/stream` without new memory parameters
- **THEN** the system MUST process the request successfully using the configured default memory behavior
