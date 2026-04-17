# 仓库指南

## 项目结构与模块组织

本仓库是油井问答系统的 Spring Boot 后台管理服务。主要代码位于 `src/main/java/org/example/springboot`，按职责划分如下：

* `controller`：REST 接口层
* `service` 和 `service/impl`：业务逻辑层
* `mapper`：MyBatis 映射接口及 SQL 提供类
* `repository`：Neo4j 数据访问
* `dto`：请求/响应模型
* `entity`：持久化实体与通用响应模型
* `config`、`security`、`interceptor`、`handler`：框架配置、JWT 认证与异常处理

数据库初始化文件位于 `src/main/resources/schema.sql`、`data.sql` 和 `application.properties`。SQL 说明或手动脚本可放在 `sql/` 目录中。

## 构建、测试与开发命令

* `./mvnw compile`：编译项目，并尽早发现 Java 或 Lombok 相关问题
* `./mvnw test`：运行当前测试用例
* `./mvnw spring-boot:run`：在本地启动应用
* `./mvnw clean`：清理 `target/` 目录下的构建产物

在迭代开发过程中，可使用 `./mvnw -DskipTests compile` 进行快速验证。

## 代码风格与命名规范

使用 4 空格缩进并遵循标准 Java 编码风格。包名使用小写，类名使用 `UpperCamelCase`。方法和字段使用 `lowerCamelCase`。Controller 类以 `Controller` 结尾，Service 类以 `Service` / `ServiceImpl` 结尾，Mapper 类以 `Mapper` 结尾，请求/响应模型以 `Request` / `Response` / `Query` 结尾。

项目已启用 Lombok，推荐用于生成简单的 getter、builder 和构造函数。新增类与非直观逻辑需添加简洁的中文注释，并与现有代码风格保持一致。

## 测试指南

测试代码位于 `src/test/java`。当前项目使用 Spring Boot 与 MyBatis 的测试依赖，并提供了基础测试类 `SpringbootApplicationTests.java`。新增测试应放在对应业务包下，并命名为 `*Tests.java`。

对于 service 或 controller 的改动，至少执行 `./mvnw test`，若仅为结构性修改可执行 `./mvnw -DskipTests compile`。当业务逻辑发生变化时，优先覆盖认证、用户管理、图谱及监控相关流程。

## 提交与 Pull Request 指南

最近的提交历史使用简短的约定式前缀，例如 `feat:`、`style:`、`init`。请遵循该规范，例如：`feat: 用户管理分页筛选` 或 `fix: 图谱可视化中心实体查询`。

PR 应包含：

* 业务变更的简要说明
* 受影响的模块或接口
* 数据库或配置变更
* 接口变更时提供示例请求/响应数据

## 安全与配置提示

不要提交真实的敏感信息。本地数据库为 MySQL `oil_qa` 和 Neo4j `oil-qa-graph`，凭证仅保存在本地配置中。在合并前，请对涉及认证的改动进行验证，包括 JWT 拦截器与角色权限校验。
