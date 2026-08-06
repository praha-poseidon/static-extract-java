# Static Extract JDT Extractor

This module executes SER rules against Java source code through Eclipse JDT.

这个模块基于 Eclipse JDT 执行 SER 规则，从 Java 源码中提取结构化信息。

## Recommended Entry Point

推荐入口。

Use `JavaStaticExtractProjectRunner` for application-level extraction. Passing
only a project root is enough for common Maven and Gradle projects.

应用级解析优先使用 `JavaStaticExtractProjectRunner`。对于常见 Maven、Gradle 项目，只传项目根目录就可以开始解析。

```java
JavaStaticExtractProjectRunner runner = JavaStaticExtractProjectRunner.builder()
    .project(Path.of("/my-project"))
    .build();

List<StaticExtractResult> results = runner.extract();
```

If there is no project root, pass source inputs explicitly.

如果没有项目根目录，就显式传入源码位置。

```java
JavaStaticExtractProjectRunner runner = JavaStaticExtractProjectRunner.builder()
    .source(Path.of("/my-project/src/main/java"))
    .classes(Path.of("/my-project/target/classes"))
    .dependency(Path.of("/my-project/target/dependency"))
    .build();
```

`source` is the Java source directory or one `.java` file to analyze.

`source` 是要分析的 Java 源码目录，或者单个 `.java` 文件。

`classes` is the compiled class directory of the current project, such as
`target/classes`.

`classes` 是当前项目编译后的 class 目录，例如 `target/classes`。

`dependency` is one dependency jar, or a directory containing dependency jars.

`dependency` 是一个依赖 jar，或者一个包含依赖 jar 的目录。

`classes` and `dependency` help JDT confirm exact Java types. They are optional,
but rules that need method-owner matching are more accurate with them.

`classes` 和 `dependency` 用来帮助 JDT 确认准确的 Java 类型。它们不是强制项，但如果规则需要判断方法属于哪个类，传入它们会更准确。

## Rule Loading

规则加载。

This extractor ships **no built-in SER rules**. Pass rules per call via:

本 extractor **不携带内置 SER 规则**。每次调用通过以下方式传入规则：

- in-memory SER strings (`rulesFromSources` / `--rule-text`)
- filesystem files (`rulesFromFiles` / `--rule`)
- directories of `*.ser` (`rulesFromDirectory` / `--rule-dir`)

Optional classpath packaging (`static-extract/rules/index.txt`) remains supported for
hosts that embed their own rule set, but this module does not ship one.

可选的 classpath 打包仍支持宿主自带规则集，但本模块不再自带任何规则文件。

## Output

输出协议。

The extractor returns Java objects. It does not force a JSON schema.

运行时返回 Java 对象，不强制固定 JSON 结构。

Each `StaticExtractResult` contains the matched rule, line range, method hint,
JDT anchor node, and the `fields` map produced by the SER `build` block.

每个 `StaticExtractResult` 包含命中的规则、行号范围、方法提示、JDT 锚点节点，以及 SER `build` 块生成的 `fields` 字段 Map。

## Lower-Level Runner

低层入口。

`JavaStaticExtractRunner` is available when the caller already has a JDT
`CompilationUnit`.

如果调用方已经自己拿到了 JDT `CompilationUnit`，可以使用更底层的 `JavaStaticExtractRunner`。

```java
JavaStaticExtractRunner runner = JavaStaticExtractRunner.builder()
    .externalValues(Map.of(
        "config", Map.of("service.base-url", List.of("http://users"))))
    .build();

List<StaticExtractResult> results =
    runner.extract(compilationUnit, projectFilePath, absoluteFilePath);
```

## Package Layout

包结构。

```text
com.poseidon.javastatic.extract.jdt.project
  Project-level source scanning and JDT parsing entry point.
  项目级源码扫描和 JDT 解析入口。

com.poseidon.javastatic.extract.jdt
  Extractor engine and result type.
  运行时引擎和结果类型。

com.poseidon.javastatic.extract.jdt.find
  Executes FindSpec and returns anchor nodes.
  执行 FindSpec，找到规则命中的代码位置。

com.poseidon.javastatic.extract.jdt.source
  Executes LetSpec/SourceSpec and reads values from Java elements.
  执行 LetSpec/SourceSpec，从 Java 元素中读取值。

com.poseidon.javastatic.extract.jdt.trace
  Resolves semantic values for take value, such as variables and fields.
  解析 take value 的语义值，例如变量和字段。

com.poseidon.javastatic.extract.jdt.build
  Executes BuildSpec and produces output fields.
  执行 BuildSpec，生成最终输出字段。
```

The extractor relies on JDT bindings for method-owner matching. It does not guess
owner types from variable names when bindings are unavailable.

运行时依赖 JDT binding 判断方法属于哪个类型。binding 不可用时，不会根据变量名猜测 owner 类型。

## Value-trace

Value-trace patches live in the same `.ser` rule file as an optional
`trace { … }` block after `build`. There are no standalone trace files.

算值补丁写在同一条 `.ser` 的可选 `trace { … }` 块里（在 `build` 之后），不再使用独立 trace 文件。

For JDT behavior that cannot be represented by the default model, implement
`JdtTraceResolver` and register it with the runner builder.

若默认模型表达不了，可实现 `JdtTraceResolver` 并注册到 runner builder。

## Shared AST vs independent parse

| Mode | API | Who parses |
|------|-----|------------|
| **B independent** | `JavaStaticExtractProjectRunner.extract()` or CLI | Runner reads files + `ASTParser` |
| **A shared** | `extract(CompilationUnit, projectPath, absPath)` or `JavaStaticExtractRunner.extract(cu, …)` | Host already has CU |

```java
// A — reuse graph's CompilationUnit
runner.extract(existingCu, "src/Main.java", absPath);

// B — full project scan (CLI / standalone)
projectRunner.extract();
```
