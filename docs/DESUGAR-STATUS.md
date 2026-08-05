# JavaSerDesugarer status

| Step | Behavior | Status |
|---|---|---|
| B1 | Wire desugar before ANTLR parse | done |
| B2 | `find X with annotation @Y` → `find X` + `when annotation @Y on X` | done |
| E2 | `from annotation on elem @Y` → `from annotation @Y on elem` | done |
| B5 | All parse entry points use desugar | done |

Public `Ser.g4` (after E1/E5): preferred ref-first annotation sources only for `from annotation`.
Legacy on-first still works via this desugarer.
