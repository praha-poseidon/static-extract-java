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

