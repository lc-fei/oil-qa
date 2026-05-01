---
name: openspec-archive-change
description: 归档已完成的 OpenSpec 变更。当用户希望在实现完成后最终确认并归档变更时使用。
license: MIT
compatibility: 需要 openspec CLI。
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

归档已完成的 OpenSpec 变更。

**输入**：可以指定变更名称。若未指定，先尝试从对话上下文推断；如果含糊或存在多个可能项，必须让用户从可用变更中选择。

**步骤**

1. **如果没有提供变更名称，要求用户选择**

   运行 `openspec list --json` 获取可用变更。使用 **AskUserQuestion 工具**让用户选择。

   只展示活跃变更，不展示已归档变更。
   如果能获取 schema，也一并展示。

   **重要**：不要猜测或自动选择变更；必须让用户确认。

2. **检查 artifact 完成状态**

   运行：

   ```bash
   openspec status --change "<name>" --json
   ```

   解析 JSON，理解：
   - `schemaName`：当前使用的工作流。
   - `artifacts`：artifact 列表及其状态，通常包括 `done` 或其他状态。

   **如果存在未完成 artifact：**
   - 展示警告并列出未完成项。
   - 使用 **AskUserQuestion 工具**确认用户是否仍要继续归档。
   - 用户确认后继续。

3. **检查任务完成状态**

   读取任务文件，通常是 `tasks.md`，检查是否还有未完成任务。

   统计：
   - `- [ ]`：未完成任务。
   - `- [x]`：已完成任务。

   **如果存在未完成任务：**
   - 展示警告和未完成数量。
   - 使用 **AskUserQuestion 工具**确认用户是否仍要继续归档。
   - 用户确认后继续。

   **如果没有任务文件：** 不展示任务相关警告，继续归档。

4. **评估 delta spec 同步状态**

   检查 `openspec/changes/<name>/specs/` 下是否存在 delta specs。若不存在，跳过同步提示。

   **如果存在 delta specs：**
   - 将每个 delta spec 与主 spec `openspec/specs/<capability>/spec.md` 对比。
   - 判断将要应用的变更，包括新增、修改、删除、重命名。
   - 在询问用户前展示合并后的摘要。

   **提示选项：**
   - 如果需要同步：`立即同步（推荐）`、`不做同步直接归档`。
   - 如果已经同步：`立即归档`、`仍然同步一次`、`取消`。

   如果用户选择同步，使用 Task 工具执行类似：`Use Skill tool to invoke openspec-sync-specs for change '<name>'. Delta spec analysis: <分析摘要>`。无论是否同步，随后继续归档。

5. **执行归档**

   创建归档目录：

   ```bash
   mkdir -p openspec/changes/archive
   ```

   使用当前日期生成目标目录名：`YYYY-MM-DD-<change-name>`。

   **如果目标目录已存在：**
   - 失败并提示原因。
   - 建议重命名已有归档或使用其他日期/名称。

   **如果目标目录不存在：**

   ```bash
   mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
   ```

6. **展示归档摘要**

   展示：
   - 变更名称。
   - 使用的 schema。
   - 归档位置。
   - specs 是否已同步。
   - 如存在未完成 artifact/任务，注明归档时的警告。

**成功输出示例**

```text
## 归档完成

**变更：** <change-name>
**Schema：** <schema-name>
**归档位置：** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs：** ✓ 已同步到主 specs（或“无 delta specs”/“已跳过同步”）

所有 artifact 已完成。所有任务已完成。
```

**约束**

- 未提供变更名称时，必须要求用户选择。
- 使用 artifact graph，即 `openspec status --json`，检查完成状态。
- 不要因为警告直接阻止归档；应清晰告知并让用户确认。
- 移动目录时保留 `.openspec.yaml`，它会随目录一起移动。
- 展示清晰的归档结果摘要。
- 如果用户要求同步 specs，使用 openspec-sync-specs 的 agent 驱动方式。
- 如果存在 delta specs，必须执行同步评估，并在确认前展示合并摘要。
