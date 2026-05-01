---
name: openspec-propose
description: 一次性提出新变更并生成所有 artifact。当用户想快速描述要构建的内容，并得到可直接进入实现的 proposal、design、specs 和 tasks 时使用。
license: MIT
compatibility: 需要 openspec CLI。
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

提出一个新变更，并一次性创建该变更所需 artifact。

我将创建包含以下内容的变更：
- `proposal.md`：说明要做什么以及为什么做。
- `design.md`：说明如何实现。
- `tasks.md`：说明实现步骤。

准备实现时，运行 `/opsx:apply`。

---

**输入**：用户请求应包含一个 kebab-case 的变更名称，或包含清晰的构建/修复描述。

**步骤**

1. **如果没有清晰输入，询问用户想构建什么**

   使用 **AskUserQuestion 工具**提出开放问题，不提供预设选项：

   > “你想处理哪个变更？请描述你想构建或修复的内容。”

   根据用户描述生成 kebab-case 名称，例如 `add user authentication` → `add-user-auth`。

   **重要**：在理解用户想构建什么之前，不要继续。

2. **创建变更目录**

   ```bash
   openspec new change "<name>"
   ```

   该命令会在 `openspec/changes/<name>/` 下创建带 `.openspec.yaml` 的变更脚手架。

3. **获取 artifact 构建顺序**

   ```bash
   openspec status --change "<name>" --json
   ```

   解析 JSON，获取：
   - `applyRequires`：实现前必须完成的 artifact ID 数组，例如 `["tasks"]`。
   - `artifacts`：所有 artifact 及其状态、依赖关系。

4. **按顺序创建 artifact，直到满足 apply-ready**

   使用 **TodoWrite 工具**跟踪 artifact 创建进度。

   按依赖顺序循环处理 artifact，优先处理没有未完成依赖的 artifact：

   a. **对每个状态为 `ready` 的 artifact：**
      - 获取说明：

        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```

      - instructions JSON 包含：
        - `context`：项目背景约束，只用于指导，不要写入输出文件。
        - `rules`：artifact 特定规则，只用于指导，不要写入输出文件。
        - `template`：输出文件结构模板。
        - `instruction`：该 artifact 类型的 schema 指导。
        - `outputPath`：应写入的目标路径。
        - `dependencies`：需要先读取的已完成依赖 artifact。
      - 读取所有已完成的依赖文件。
      - 使用 `template` 结构创建 artifact 文件。
      - 遵守 `context` 与 `rules`，但不要把它们原样复制到 artifact 文件中。
      - 简要展示进度：`已创建 <artifact-id>`。

   b. **持续处理，直到所有 applyRequires artifact 完成**
      - 每创建一个 artifact 后，重新运行：

        ```bash
        openspec status --change "<name>" --json
        ```

      - 检查 `applyRequires` 中的每个 artifact ID 是否都在 artifacts 数组中为 `status: "done"`。
      - 全部完成后停止。

   c. **如果 artifact 需要用户输入**
      - 使用 **AskUserQuestion 工具**澄清。
      - 澄清后继续创建。

5. **展示最终状态**

   ```bash
   openspec status --change "<name>"
   ```

**输出**

完成所有 artifact 后，总结：
- 变更名称和位置。
- 已创建 artifact 列表及简要说明。
- 当前状态：`所有 artifact 已创建，可以开始实现。`
- 提示：`运行 /opsx:apply 或直接让我实现，即可开始处理任务。`

**Artifact 创建准则**

- 遵循 `openspec instructions` 中每个 artifact 的 `instruction` 字段。
- schema 决定 artifact 应包含什么内容，必须遵循。
- 创建新 artifact 前，先读取依赖 artifact。
- 使用 `template` 作为输出文件结构，并填充对应内容。
- **重要**：`context` 和 `rules` 是给你的约束，不是输出内容。
  - 不要把 `<context>`、`<rules>`、`<project_context>` 之类块复制进 artifact。
  - 它们用于指导写作，但不应出现在最终文件中。

**约束**

- 创建实现所需的全部 artifact，以 schema 的 `apply.requires` 为准。
- 创建新 artifact 前必须读取依赖 artifact。
- 如果上下文严重不清，询问用户；但优先做出合理决策以保持推进。
- 如果同名变更已经存在，询问用户是继续该变更还是创建新变更。
- 每创建一个 artifact 后，验证目标文件确实存在，再继续下一个。
