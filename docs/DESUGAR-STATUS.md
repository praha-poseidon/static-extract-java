# JavaSerDesugarer status

| Step | Behavior | Status |
|---|---|---|
| B2 | `find X with annotation @Y` → `find X` + `when annotation @Y on X` | done |
| E2 | `from annotation on elem @Y` → `from annotation @Y on elem` | done |
| F4 | `find method Owner.name` → `find call Owner.name` | done |
| B5 | All parse entry points use desugar | done |

See also `static-extract-spec/docs/METHOD-VS-CALL.md`.
