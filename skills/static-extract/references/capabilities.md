# SER capabilities (by extractor vocabulary)

Shared SER grammar is **structure only** (`docs/CLEAN-G4.md`). What you can
`find` / `from` depends on the extractor vocabulary.

## Product goals (not grammar)

- HTTP inbound / outbound
- UI text and actions
- Messaging, DB, Redis, jobs, RPC when visible in code

## Java/JDT free-atom vocabulary (examples)

Anchors:

```ser
find method
when annotation @SomeAnnotation on method

find class
when annotation @SomeAnnotation on class

find field
when annotation @SomeAnnotation on field

find call SomeType.someMethod
```

Sources:

```ser
from annotation @X on method take attr(value)
from argument[0] take value
from field take name
from call take owner
from literal SOME_VALUE take value
  fallback ""
```

## JS/TS free-atom vocabulary (examples)

```ser
find jsx button
find call fetch
find export default
from prop onClick take reference
from children take text
from argument[0] take value
```

## Build

```ser
build {
  path: concat(basePath, methodPath) | normalize slash
}
```

## Trace

Use when values leave pure source (config placeholders, external maps). Trace
`from` / `when` free atoms are also vocabulary-specific.
