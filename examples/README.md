# Static Extract Examples

This module contains a runnable Java example project for `java/jdt`.

这个模块提供可运行的 Java 示例项目，用来演示 `java/jdt`。

Sample SER rules used by the example test live under:

示例测试使用的 SER 规则放在：

```text
src/test/resources/example-rules/
```

(Spring MVC inbound + RestTemplate outbound with embedded trace.) They are **not**
shipped as extractor built-ins; the test loads them explicitly via `rulesFromDirectory`.

（Spring MVC 入站 + RestTemplate 出站及嵌入 trace。）它们**不是** extractor 内置规则；
测试通过 `rulesFromDirectory` 显式加载。

## Runnable Example Project

可运行示例项目。

```text
src/test/resources/example-project
```

It includes:

- `UserController`, with `@RequestMapping`, `@GetMapping`, and `@PostMapping`.
- `UserClient`, with `RestTemplate.getForObject` and `postForObject`.
- Small fake Spring annotations and `RestTemplate` classes (no Spring download).

Run:

```bash
mvn -pl examples -am test
```

The test writes the extracted result to:

```text
examples/target/example-output.txt
```

Expected facts include:

```text
HTTP inbound  GET  /api/users/{param}
HTTP inbound  POST /api/users
HTTP outbound GET  /api/users/{param}
HTTP outbound POST /api/users
```
