# JavaSerDesugarer (compatibility layer)

Rewrites **legacy** SER text to the public clean surface before ANTLR parse.

| Rewrite | Status |
|---|---|
| `find X with annotation @Y` | done |
| `from annotation on elem @Y` | done |
| `from decorator on elem Name` | done |
| `find method Owner.x` | done → `find call Owner.x` |

Public grammar: `static-extract-spec/docs/CLEAN-G4.md`.
JS has a matching desugar in `antlr-ser-parser.ts` / `extractor/ser-desugar.mjs`.
