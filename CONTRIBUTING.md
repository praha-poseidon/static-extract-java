# Contributing

Java Static Extract is currently in alpha. Contributions are welcome, but the
SER syntax and Java API may still change before a stable release.

Java Static Extract 当前处于 alpha 阶段。欢迎贡献，但在稳定版本之前，SER 语法和 Java API 仍可能调整。

## Development Setup

开发环境。

Use JDK 21 and Maven.

请使用 JDK 21 和 Maven。

```bash
mvn test
```

Run the full test suite before sending changes.

提交变更前请先运行完整测试。

## Project Layout

项目结构。

```text
java-static-extract-core
  SER grammar, parser contracts, and runtime-neutral rule models.
  SER 语法、解析接口，以及不依赖具体运行时的规则模型。

java-static-extract-jdt
  Eclipse JDT runtime, project runner, rule loading, value tracing, and build evaluation.
  Eclipse JDT 运行时、项目级 runner、规则加载、值追踪和 build 计算。

java-static-extract-examples
  Example SER rules. This module is optional and not required by the runtime.
  示例 SER 规则。这个模块是可选模块，运行时不依赖它。
```

## Change Guidelines

变更原则。

Keep rule language changes small and documented.

规则语言变更要尽量小，并补充文档。

Keep runtime behavior generic. Framework-specific rules should usually live in
examples or in the caller's own application resources.

运行时行为要保持通用。框架相关规则通常应该放在 examples 模块，或者调用方自己的应用资源里。

Do not add built-in Spring, Dubbo, Feign, or other framework rules to
`java-static-extract-jdt` unless the project explicitly decides to make them
part of the runtime contract.

不要把 Spring、Dubbo、Feign 等框架规则直接内置到 `java-static-extract-jdt`，除非项目明确决定把它们变成运行时契约的一部分。

When adding a new SER feature, update tests and documentation together.

新增 SER 能力时，请同步更新测试和文档。

## Testing

测试。

Run:

运行：

```bash
mvn test
```

For parser changes, add tests under `java-static-extract-core`.

解析器变更请在 `java-static-extract-core` 增加测试。

For JDT runtime behavior, add tests under `java-static-extract-jdt`.

JDT 运行时行为变更请在 `java-static-extract-jdt` 增加测试。

## Documentation

文档。

The root README keeps English and Chinese together. When adding a paragraph,
put the Chinese explanation directly below the English paragraph.

根 README 使用中英文放在一起的方式。新增英文段落时，请把中文说明直接放在英文段落下面。

Avoid exposing JDT internals in user-facing documentation unless the section is
explicitly about low-level extension points.

面向用户的文档里尽量不要暴露 JDT 内部概念，除非该章节明确是在讲低层扩展点。
