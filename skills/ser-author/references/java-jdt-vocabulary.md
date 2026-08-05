# Java/JDT vocabulary (free atoms)

Shared grammar is structure-only (`CLEAN-G4.md`). Below are **Java extractor**
atoms after `find` / `from` / `when` / `take`.

## find

```ser
find method
when annotation @*Mapping on method

find field
when annotation @Value on field

find call RestTemplate.getForObject
find call RestTemplate.[getForObject,postForObject]
find class
when annotation @Controller on class
```

## from / take

```ser
from annotation @*Mapping on method take attr(value)
from annotation @RequestMapping on class take attr(path)
from argument[0] take value
from call take owner
from method take name
from field take name
from field take value
```

## when (trace)

```ser
when annotation @Value on field
when method Environment.getProperty
when field name url
```
