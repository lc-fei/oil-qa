---
name: openspec-apply-change
description: 根据 OpenSpec 变更实现任务。当用户希望开始实现、继续实现或逐项完成任务时使用。
license: MIT
compatibility: 需要 openspec CLI。
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

根据 OpenSpec 变更实现任务。

**输入**：可以指定变更名称。若未指定，先尝试从对话上下文推断；如果含糊或存在多个可能项，必须让用户从可用变更中选择。

**步骤**

1. **选择变更**

   如果用户提供了名称，直接使用。否则：
   - 如果对话中提到过变更，则从上下文推断。
   - 如果当前只有一个活跃变更，则可自动选择。
   - 如果存在歧义，运行 `openspec list --json` 获取可用变更，并使用 **AskUserQuestion 工具**让用户选择。

   必须说明：“使用变更：<name>”，并提示如何覆盖，例如 `/opsx:apply <other>`。

2. **检查状态并理解工作流 schema**

   ```bash
   openspec status --change "<name>" --json
   ```

   解析 JSON，理解：
   - `schemaName`：当前使用的工作流，例如 `spec-driven`。
   - 哪个 artifact 包含任务，通常 spec-driven 使用 `tasks`，其他 schema 以 status 输出为准。

3. **获取实现说明**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   该命令返回：
   - `contextFiles`：artifact ID 到具体文件路径数组的映射，不同 schema 可能是 proposal/specs/design/tasks，也可能是 spec/tests/implementation/docs。
   - 进度：总数、已完成数、剩余数。
   - 任务列表及状态。
   - 根据当前状态生成的动态说明。

   **状态处理：**
   - 如果 `state: "blocked"`，说明缺少必要 artifact：展示原因，并建议使用 openspec-continue-change。
   - 如果 `state: "all_done"`，说明任务已完成：提示可以归档。
   - 其他情况：继续实现。

4. **读取上下文文件**

   读取 apply instructions 输出中 `contextFiles` 下列出的所有文件路径。
   文件内容取决于 schema：
   - **spec-driven**：proposal、specs、design、tasks。
   - 其他 schema：严格按 CLI 输出的 contextFiles 读取，不要猜测文件名。

5. **展示当前进度**

   展示：
   - 当前使用的 schema。
   - 进度，例如 `N/M tasks complete`。
   - 剩余任务概览。
   - CLI 返回的动态说明。

6. **实现任务，循环直到完成或阻塞**

   对每个待办任务：
   - 说明正在处理哪个任务。
   - 做必要的代码修改。
   - 保持改动最小、聚焦。
   - 完成后立即更新 tasks 文件中的复选框：`- [ ]` → `- [x]`。
   - 继续下一个任务。

   **以下情况必须暂停：**
   - 任务含义不清，需要用户澄清。
   - 实现过程中发现设计问题，需要建议更新 artifact。
   - 遇到错误或阻塞，需要报告并等待指示。
   - 用户中断。

7. **完成或暂停时展示状态**

   展示：
   - 本轮完成的任务。
   - 总体进度，例如 `N/M tasks complete`。
   - 如果全部完成，建议归档。
   - 如果暂停，说明原因并等待用户指示。

**实现过程中的输出示例**

```text
## 正在实现：<change-name>（schema: <schema-name>）

正在处理任务 3/7：<task description>
[...执行实现...]
✓ 任务完成

正在处理任务 4/7：<task description>
[...执行实现...]
✓ 任务完成
```

**完成时输出示例**

```text
## 实现完成

**变更：** <change-name>
**Schema：** <schema-name>
**进度：** 7/7 tasks complete ✓

### 本轮完成
- [x] Task 1
- [x] Task 2
...

所有任务已完成，可以归档该变更。
```

**暂停时输出示例**

```text
## 实现暂停

**变更：** <change-name>
**Schema：** <schema-name>
**进度：** 4/7 tasks complete

### 遇到的问题
<问题描述>

**可选方案：**
1. <方案 1>
2. <方案 2>
3. 其他方案

你希望怎么处理？
```

**约束**

- 持续推进任务，直到全部完成或明确阻塞。
- 开始实现前必须读取 CLI 返回的 contextFiles。
- 如果任务有歧义，先暂停并询问，不要猜测实现。
- 如果实现暴露设计问题，暂停并建议更新 artifact。
- 代码改动必须小而聚焦。
- 每完成一个任务，立即更新任务复选框。
- 遇到错误、阻塞或需求不清时暂停，不要硬做。
- 使用 CLI 输出的 contextFiles，不要假设固定文件名。

**与流式工作流的集成**

本 skill 支持“围绕变更执行动作”的模式：

- **可随时调用**：artifact 尚未全部完成但已有任务时、实现到一半时、或与其他动作交错时均可调用。
- **允许更新 artifact**：如果实现过程中发现设计问题，应建议更新 artifact；不要把流程理解成僵硬阶段，保持灵活推进。
