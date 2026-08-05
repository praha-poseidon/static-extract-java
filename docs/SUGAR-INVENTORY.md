# Java dialect sugar inventory (step-A3)

Maps every in-repo SER surface that uses forms listed in
`static-extract-spec/docs/CORE-VS-JAVA-DIALECT.md`.

**Scope:** `static-extract-java` sources + sibling `static-extract-spec/examples/java`
(consumed by java CLI tests).  
**Not in scope:** `target/`, generated classes.

IDs: **F** find · **S** from · **W** when (same as CORE-VS-JAVA-DIALECT).

---

## 1. Builtin rule / trace files

| File | Sugar lines | IDs |
|---|---|---|
| `jdt/src/main/resources/static-extract/rules/builtin/spring-mvc-http-inbound.ser` | `find method with annotation @*Mapping`; multiple `from annotation on class/method` | F1, S1, S2 |
| `jdt/src/main/resources/static-extract/rules/builtin/rest-template-http-outbound.ser` | `find method RestTemplate.[...]` | F4, F5 |
| `jdt/src/main/resources/static-extract/traces/builtin/spring-config.trace.ser` | `when annotation @Value on field`; `from annotation on field @Value`; `when method Environment.getProperty` | W1, S3, W2 |

Total builtin `.ser` files: **3** (all use sugar).

---

## 2. Spec conformance examples (sibling repo)

Path root: `../static-extract-spec/examples/java/`  
(Tests resolve via `SpecAssertions.findSpecRoot()`.)

| File | Sugar | IDs |
|---|---|---|
| `annotation-fact/rule.ser` | `find method with annotation @RouteGet`; `from annotation on method @RouteGet` | F1, S1 |
| `call-fact/rule.ser` | `find method HttpClient.get` | F4 |
| `config-field/rule.ser` | `find field with annotation @ConfigProperty`; `from annotation on field @ConfigProperty` | F3, S3 |

---

## 3. Inline SER in Java tests

| File | Sugar usage (summary) | IDs |
|---|---|---|
| `jdt/.../FunctionalCapabilityMatrixTest.java` | F1, F3, F4, F7 (`find field baseUrl`), S1–S3, W1, W2, annotation on parameter | F1, F3, F4, F7, S1–S3, W1, W2 |
| `jdt/.../project/JavaStaticExtractProjectRunnerTest.java` | `find method with annotation @GetMapping`; `from annotation on method` | F1, S1 |
| `jdt/.../load/SerRuleLoaderTest.java` | trace: `when annotation @Value on field`; `from annotation on field` | W1, S3 |
| `core/.../AntlrSerRuleParserTest.java` | Spring-like F1/S1/S2; Action F1/S1; Value W1/S3; Client.[load,save] F5; parameter annotation S*; field with annotation F3 | F1, F3, F5, S1–S3, W1 |

---

## 4. Authoring docs (not executed, but surface contract)

| File | Notes |
|---|---|
| `jdt/vocabulary.md` | Documents full Java sugar vocabulary (F1–F7, S*, W*) |
| `core/README.md` | Spring MVC example uses F1 / S1 / S2 |

---

## 5. Counts by sugar ID (approximate frequency)

| ID | Form | Where it appears |
|---|---|---|
| **F1** | `find method with annotation` | spring-mvc builtin; annotation-fact example; several tests |
| **F3** | `find field with annotation` | config-field example; FunctionalCapabilityMatrixTest |
| **F4/F5** | `find method Owner.name` / `Owner.[a,b]` | rest-template builtin; call-fact example; tests |
| **F7** | `find field name` | FunctionalCapabilityMatrixTest (`find field baseUrl`) |
| **F2/F6** | class with annotation / bare `find class` | vocabulary.md only in this pass (no builtin file) |
| **S1** | `from annotation on method` | spring-mvc; annotation-fact; tests |
| **S2** | `from annotation on class` | spring-mvc |
| **S3** | `from annotation on field` | spring-config trace; config-field; tests |
| **W1** | `when annotation @X on element` | spring-config trace; tests |
| **W2** | `when method Owner.name` | spring-config trace; tests |

---

## 6. Implications for later steps

| Later step | Priority from this inventory |
|---|---|
| **B2** desugar F1 | Unblocks spring-mvc + annotation-fact + many tests |
| **B3** desugar S1–S3 | Same cluster |
| **B4** W1 + W2 | spring-config trace |
| **F4/F5** | rest-template + call-fact — resolve method-vs-call (see CORE-VS-JAVA-DIALECT §3) before changing meaning |

---

## 7. How to re-scan

```bash
# from static-extract-java
grep -rnE 'with annotation|annotation on |when annotation |find method |find field with|when method ' \
  --include='*.ser' --include='*.java' \
  --exclude-dir=target .
# plus sibling examples:
grep -rnE 'with annotation|annotation on |find method |find field with' \
  ../static-extract-spec/examples/java --include='*.ser'
```

---

## 8. step-A3 checklist

- [x] Builtin rules/traces listed
- [x] Spec java examples listed
- [x] Main test fixtures listed
- [x] Vocabulary/docs noted
- [x] Priority hint for B2–B4
