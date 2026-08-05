# Static Extract Examples

This module contains a runnable Java example project for
`java/jdt`.

这个模块提供可运行的 Java 示例项目，用来演示 `java/jdt`。

The Java built-in rules live in the extractor module now:

Java 内置规则现在放在 extractor 模块下面：

```text
java/jdt/src/main/resources/static-extract/rules/
java/jdt/src/main/resources/static-extract/traces/
```

When this module runs tests, it uses those extractor-owned built-in rules through
the normal classpath loader.

这个模块跑测试时，会通过正常 classpath loader 使用 extractor 自己携带的内置规则。

Current examples:

当前示例：

- Spring MVC HTTP inbound extract rule
- RestTemplate HTTP outbound extract rule
- Spring config trace rule for `@Value("${...}")` and `Environment.getProperty(...)`

- Spring MVC HTTP 入站提取规则
- RestTemplate HTTP 出站提取规则
- 用于 `@Value("${...}")` 和 `Environment.getProperty(...)` 的 Spring 配置 trace 规则

## Runnable Example Project

可运行示例项目。

The module also contains a tiny Spring-style project under:

这个模块还包含一个很小的 Spring 风格示例项目：

```text
src/test/resources/example-project
```

It includes:

里面包含：

- `UserController`, with `@RequestMapping`, `@GetMapping`, and `@PostMapping`.
- `UserController`，包含 `@RequestMapping`、`@GetMapping` 和 `@PostMapping`。
- `UserClient`, with `RestTemplate.getForObject` and `postForObject`.
- `UserClient`，包含 `RestTemplate.getForObject` 和 `postForObject`。
- Small fake Spring annotations and `RestTemplate` classes, so the example can
  run without downloading Spring.
- 很小的模拟 Spring 注解和 `RestTemplate` 类，所以这个示例不需要下载 Spring 也能跑。

Run:

运行：

```bash
mvn -pl java/examples -am test
```

The test writes the extracted result to:

测试会把提取结果写到：

```text
java/examples/target/example-output.txt
```

Expected facts include:

预期提取结果包括：

```text
HTTP inbound  GET  /api/users/{param}
HTTP inbound  POST /api/users
HTTP outbound GET  /api/users/{param}
HTTP outbound POST /api/users
```

Trace rule guide: [../docs/TRACE_RULES.md](../docs/TRACE_RULES.md).

trace 规则说明见 [../docs/TRACE_RULES.md](../docs/TRACE_RULES.md)。
