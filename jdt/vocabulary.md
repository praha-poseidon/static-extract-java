# Java/JDT vocabulary (free atoms)

Structure keywords live in shared Ser.g4; words below are free atoms for this extractor.
See `static-extract-spec/docs/CLEAN-G4.md`.

# Static Extract Java/JDT Extractor Vocabulary

This file defines the main SER vocabulary currently implemented by the Java/JDT
extractor. SER authoring tools and Skills must stay within this vocabulary unless
they also update the extractor.

## Find Selectors

```ser
find class
find class
when annotation @AnnotationName on class
find call Owner.methodName
find call Owner.[methodA,methodB]
find method
when annotation @AnnotationName on method
find method
when annotation @*Mapping on method
find field fieldName
find field
when annotation @AnnotationName on field
```

## Filtering: `where` vs `when` (different roles)

| keyword | meaning | examples |
|---------|---------|----------|
| **find** | what shape | `method`, `call`, `field` |
| **where** | **where it lives** (scope) | enclosing class name, annotation **on class**, package |
| **when** | **what the element is** (anchor) | annotation **on method/field**, call owner |

Order: `find` → `where*` → `when*` → `let` → `build`.

```ser
find method
where class name UserController              # scope: only this type
where annotation @RestController on class  # scope: only controller types
when annotation @PostMapping on method     # anchor: method has PostMapping
```

### Regex (preferred for flexible filters)

Use the `matches` keyword with a quoted Java regex:

```ser
# class simple name or FQN
where class name matches ".*Controller$"
where class matches ".*Client$|.*Facade$"

# still exact when no matches
where class name UserController
```

Patterns use `java.util.regex` (`find` semantics — not forced full-string unless you add `^…$`).

### Same URL / annotation on A and B — only B

```ser
find field url
where class name ConfigB

find method
where class name UserController
when annotation @GetMapping on method
```

### Controller vs Client

```ser
# inbound-ish: mapping on controller type
find method
where annotation @RestController on class
when annotation @PostMapping on method

# client-ish: mapping on Feign type
find method
where annotation @FeignClient on class
when annotation @PostMapping on method

# or outbound by call shape
find call RestTemplate.[postForObject,postForEntity]
```

## Source Expressions

```ser
from annotation @AnnotationName on class take attr(value)
from annotation @AnnotationName on method take attr(value)
from annotation @AnnotationName on field take attr(value)
from class take name
from method take name
from method take signature
from field take name
from field take value
from argument[0] take value
from call take name
from call take owner
from literal "value" take value
```

## Pipelines (build and let)

Same steps on build fields and, after parse, on let values:

```ser
let path =
  from argument[0] take value
  | normalize slash
  | normalize pathVariable

build {
  path: path | regex "(.+)" group 1 | replace "old" "new"
}
```

Normalize names include: `slash`, `pathVariable`, `extractPath`, `placeholderLookup`,
`placeholderDefault`, `kebab`.

## Value-trace (optional `trace { }` in the same rule file)

```ser
trace {
  from field
  when annotation @Value on field

  let rawValue =
    from annotation @Value on field take attr(value)

  build {
    namespace: "config"
    lookup: rawValue | normalize placeholderLookup
    default: rawValue | normalize placeholderDefault
  }

  from call
  when method Environment.getProperty

  let configLookup =
    from argument[0] take value

  build {
    namespace: "config"
    lookup: configLookup
  }
}
```
