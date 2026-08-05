---
name: static-extract
description: Use this skill when a user wants to run Static Extract on a Java, TypeScript, JavaScript, or React project, validate SER rules with the matching CLI, diagnose missing matches, or produce structured JSON/JSONL facts.
---

# Static Extract

Use the extractor CLI that matches the target project. Do not ask the user to
write SER rules unless they explicitly want to; inspect the project, draft or
reuse SER rules, run them, and iterate.

## Workflow

1. Detect the target extractor from the project or user request:
   - Java/JDT: use `static-extract-java`
   - TypeScript, JavaScript, JSX, TSX, or React: use `static-extract-ts`
2. Confirm the matching CLI is available:
   ```bash
   static-extract-java --help
   static-extract-ts --help
   ```
3. Initialize the workspace in the target project:
   ```bash
   static-extract-java init --project /path/to/project
   static-extract-ts init --project /path/to/project
   ```
4. Inspect source files and identify concrete code shapes to extract:
   annotations, method calls, fields, constants, return values, string paths,
   JSX elements, JSX props, imports, and callback references.
5. Decide extraction targets from the user request. If the user asks for broad
   service or UI extraction, consider:
   - HTTP inbound endpoints
   - HTTP outbound calls
   - UI text and user actions
   - Kafka producers and consumers
   - MySQL/JDBC/MyBatis/JPA database operations
   - Redis operations and keys
   - Scheduled jobs
   - RPC clients and servers when visible in code
6. Read the relevant vocabulary before writing or repairing rules:
   - Java/JDT: `references/capabilities.md`
   - SER syntax: `references/ser-cheatsheet.md`
   - TS/React: `references/react-ts-vocabulary.md`
7. If target values may depend on extractor configuration, prepare an
   external-values JSON file. Java/JDT and TS both use trace-ser target names
   `call`, `field`, `parameter`, `method`, `return`, and `assignment`; TS
   applies them to syntax-level value tracing and external value lookup. Read
   `references/external-values.md`.
8. Write generated rules under:
   ```text
   /path/to/project/.ser/generated
   ```
9. Validate one rule on one or more representative files:
   ```bash
   static-extract-java try --project /path/to/project --file /path/to/File.java --rule /path/to/project/.ser/generated/name.ser
   static-extract-ts try --project /path/to/project --source /path/to/Component.tsx --rule /path/to/project/.ser/generated/name.ser
   ```
10. If no result is emitted, run:
   ```bash
   static-extract-java diagnose --project /path/to/project --file /path/to/File.java --rule /path/to/project/.ser/generated/name.ser
   static-extract-ts diagnose --project /path/to/project --source /path/to/Component.tsx --rule /path/to/project/.ser/generated/name.ser
   ```
   Use returned facts to adjust the rule.
11. Repeat `try` until representative files emit expected fields.
12. Run extraction:
   ```bash
   static-extract-java run --project /path/to/project --rule /path/to/project/.ser/generated/name.ser --out /path/to/project/.ser/result/extract.jsonl
   static-extract-ts run --project /path/to/project --source /path/to/project/src --rule /path/to/project/.ser/generated/name.ser --out /path/to/project/.ser/result/extract.jsonl
   ```

One `.ser` file may contain both `rule ...` and `trace ...` blocks for Java/JDT workflows. For TS CLI execution, pass trace-ser files through `--trace-rule` or `--trace-rules`.

## Rule Writing

Read `references/ser-cheatsheet.md` before writing or repairing SER rules.

Rules should be based on observable code shapes, not framework assumptions.
Prefer one rule per extraction shape, for example one for annotation-based HTTP
inbound endpoints, one for fluent router calls, one for JSX button text, and
one for frontend API calls.

The CLI executes SER rules. It does not decide product scope or framework semantics by itself. The agent must inspect the project, understand the actual code shapes, express those shapes with SER primitives, validate the rules, and report unsupported or ambiguous cases.

## Output Contract

The CLI prints JSON. `run --out` writes JSON Lines. Each extracted record contains:

- `rule`
- `factType`
- `classifiers`
- `fields`
- `projectFilePath`
- `absoluteFilePath`
- `startLine`
- `endLine`
- `enclosingMethod`

The `fields` object is the main product. It is defined entirely by the rule `build` block. `classifiers` contains optional rule-header classification metadata, such as category and direction for older `endpoint` declarations.

## Final User Response

After running extraction, summarize results for the user. Do not paste raw JSON unless the user asks for it.

Use this shape:

```text
已完成静态提取。

本次生成/使用的规则：
- .ser/generated/http-inbound.ser: Spring MVC HTTP inbound endpoints
- .ser/generated/http-outbound.ser: RestTemplate HTTP outbound calls
- .ser/generated/react-button-text.ser: React button text

验证情况：
- try 通过：2 个代表文件
- diagnose：没有剩余未匹配问题
- run 输出：.ser/result/extract.jsonl

提取结果：
| 类型 | 方向 | 方法 | 路径 | 代码位置 |
| --- | --- | --- | --- | --- |
| HTTP | inbound | GET | /api/users/{param} | UserController.java:11 |
| HTTP | inbound | POST | /api/users | UserController.java:16 |
| HTTP | outbound | GET | /api/users/{param} | UserClient.java:14 |
| HTTP | outbound | POST | /api/users | UserClient.java:19 |
| UI text | button | - | 保存 | UserForm.tsx:22 |

需要注意：
- 如果有依赖没有传入，涉及框架类型判断的规则可能会漏报。
- 如果有动态拼接、运行时配置，结果依赖 trace 规则和 external-values。
```

When results are many, group by `factType` and useful `classifiers`, show counts first, then list the top representative records and point to the JSONL output file.

When no result is found, say what was checked, include the most useful diagnose facts, and suggest the next concrete rule adjustment.

## External Values

When trace blocks need extractor configuration, pass a JSON dictionary with `--external-values`:

```json
{
  "config": {
    "service.base-url": ["http://users"]
  }
}
```

Do not connect to databases or remote systems from the skill. Ask the user for an exported JSON file or create one from local project configuration when available.

When external values are missing, still run extraction with unresolved or default values when possible, but state the limitation clearly in the final response. Do not silently invent configuration values.
