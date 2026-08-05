# Static Extract Java/JDT Extractor Vocabulary

This file defines the main SER vocabulary currently implemented by the Java/JDT
extractor. SER authoring tools and Skills must stay within this vocabulary unless
they also update the extractor.

## Find Selectors

```ser
find class
find class with annotation @AnnotationName
find method Owner.methodName
find method Owner.[methodA,methodB]
find method with annotation @AnnotationName
find method with annotation @*Mapping
find field fieldName
find field with annotation @AnnotationName
```

## Source Expressions

```ser
from annotation on class @AnnotationName take attr(value)
from annotation on method @AnnotationName take attr(value)
from annotation on field @AnnotationName take attr(value)
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

## Trace Sources

```ser
from field
when annotation @Value on field

from call
when method Environment.getProperty
```

Trace rules may build external values such as:

```ser
build {
  namespace: "config"
  lookup: rawValue | normalize placeholderLookup
  default: rawValue | normalize placeholderDefault
}
```

