# Changelog

All notable changes to this project will be documented in this file.

本文件记录项目的重要变更。

The project is currently in alpha. Version numbers and public APIs may change
before the first stable release.

项目当前处于 alpha 阶段。在第一个稳定版本之前，版本号和公开 API 可能调整。

## 0.0.1-SNAPSHOT

Initial alpha version.

初始 alpha 版本。

### Added

新增能力。

- SER rule language for describing Java static extraction rules.
- 新增 SER 规则语言，用来描述 Java 静态提取规则。
- Runtime-neutral rule model in `java-static-extract-core`.
- 在 `java-static-extract-core` 中提供不依赖具体运行时的规则模型。
- Eclipse JDT runtime in `java-static-extract-jdt`.
- 在 `java-static-extract-jdt` 中提供 Eclipse JDT 运行时。
- Project-level `JavaStaticExtractProjectRunner`.
- 新增项目级入口 `JavaStaticExtractProjectRunner`。
- Classpath and filesystem SER rule loading.
- 支持从 classpath 和文件系统加载 SER 规则。
- Java value tracing for literals, variables, fields, concatenation, and return expressions.
- 支持追踪字面量、变量、字段、字符串拼接和 return 表达式。
- Declarative trace rules for external values such as configuration keys.
- 支持用于外部值的声明式 trace 规则，例如配置 key。
- Custom JDT trace extension through `JdtTraceResolver`.
- 支持通过 `JdtTraceResolver` 扩展 JDT trace 行为。
- Example rules for Spring MVC inbound HTTP and RestTemplate outbound HTTP.
- 提供 Spring MVC 入站 HTTP 和 RestTemplate 出站 HTTP 的示例规则。
