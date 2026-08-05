# SER Cheatsheet

Public surface only (`docs/CLEAN-G4.md`). Grammar = structure keywords;
words after `find` / `from` / `when` / `take` are free atoms for each extractor.

## Basic rule (Java vocabulary example)

```ser
rule "Spring MVC HTTP Inbound"
fact backend_endpoint

find method
when annotation @*Mapping on method

let basePath =
  from annotation @RequestMapping on class take attr(value)
  from annotation @RequestMapping on class take attr(path)
  fallback ""

let methodPath =
  from annotation @*Mapping on method take attr(value)
  from annotation @*Mapping on method take attr(path)
  fallback ""

let httpMethod =
  from annotation @*Mapping on method take name
  map {
    GetMapping: GET
    PostMapping: POST
    RequestMapping: GET
  }

build {
  httpMethod: httpMethod
  path: concat(basePath, methodPath) | normalize slash
}
```

## Structure keywords

```text
rule  fact  endpoint  find  when  let  from  take  fallback  map  build  trace
if and or not exists matches contains in
concat normalize regex replace group
```

## Free atoms (examples — meaning is vocabulary-specific)

```text
method  class  field  call  jsx  prop  annotation  decorator
@GetMapping  on  attr  name  value  argument[0]  Owner.name
```

## Common patterns

```ser
find method
when annotation @GetMapping on method

find call RestTemplate.getForObject
find call [get,post,delete]

find jsx button

from annotation @GetMapping on method take attr(value)
from argument[0] take value
from call take owner
from jsx button take text
from prop onClick take reference

let x =
  from literal GET take value
  fallback ""
```

## Trace

```ser
trace "Spring Value"
from field
when annotation @Value on field

let rawValue =
  from annotation @Value on field take attr(value)

build {
  namespace: config
  lookup: rawValue | normalize placeholderLookup
}
```

## CLI

```bash
static-extract-java try --project DIR --source FILE --rule rule.ser
static-extract-ts try --project DIR --source FILE --rule rule.ser
```
