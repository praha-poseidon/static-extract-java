# static-extract-java

Java / Eclipse JDT implementation of [static-extract-spec](../static-extract-spec).

最终对应独立 git 项目。开发时请将 `static-extract-spec` 放在同级目录：

```text
codeGraphProjects/
  static-extract-spec/
  static-extract-java/   ← this repo
  static-extract-js/
```

## Modules

```text
core/       SER parser (ANTLR from sibling spec) + rule models
jdt/        JDT executor, value tracing, built-in rules
assistant/  Agent-facing init / try / diagnose / run API
cli/        Picocli CLI
examples/   Runnable Java examples
```

## Prerequisites

- JDK 21+
- Maven 3.9+
- Sibling checkout of `static-extract-spec` **or** `STATIC_EXTRACT_SPEC` pointing
  at a local spec root (must contain `ser/Ser.g4`)

## Build & test

```bash
cd static-extract-java
mvn test
```

The `core` module copies `../static-extract-spec/ser/Ser.g4` during
`generate-sources` and runs ANTLR.

## Run CLI

```bash
mvn -pl cli -am package
# then use appassembler bin or:
java -cp ... com.poseidon.javastatic.extract.cli.JavaStaticExtractCli --help
```

Typical workflow: `init` → `try` / `diagnose` → `run`.

## Spec resolution

| Use case | Path |
|---|---|
| Build-time grammar | `../static-extract-spec/ser` (relative to this repo) |
| Tests (schema / examples) | `SpecAssertions.findSpecRoot()` → sibling or `STATIC_EXTRACT_SPEC` |

## Related

- Spec: `../static-extract-spec`
- JS extractor: `../static-extract-js`
