# JavaSerDesugarer status

| Step | Behavior | Status |
|---|---|---|
| B1 | Wire desugar before ANTLR parse | done |
| B2 | `find X with annotation @Y` → `find X` + `when annotation @Y on X`; merge when into FindSpec | done |
| B3 | `from annotation on element @Y` | pass-through (g4 + SourceSpec still need it) |
| B4 | `when annotation` / `when method` (trace) | pass-through (trace engine already uses them) |
| B5 | All parse entry points use desugar | done (via `AntlrSerRuleParser`) |

**C3 done:** F1–F3 removed from public `Ser.g4`. Author sugar still works via this desugarer.

Still in shared g4 (not yet removed): `from annotation on …`, specialized `when` forms, `find method Owner.name` patterns.
