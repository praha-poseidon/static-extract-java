# Legacy sugar inventory (historical)

Public authoring and builtins use the clean surface in
`static-extract-spec/docs/CLEAN-G4.md`.

Legacy forms below are **desugar-only** (still accepted by Java/JS desugar):

| Legacy | Desugars to |
|---|---|
| `find X with annotation @Y` | `find X` + `when annotation @Y on X` |
| `from annotation on elem @Y` | `from annotation @Y on elem` |
| `from decorator on elem Name` | `from decorator Name on elem` |
| `find method Owner.x` | `find call Owner.x` |

Canonical assets (examples, builtins) no longer use those legacy forms.
