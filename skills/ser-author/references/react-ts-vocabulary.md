# React / TS vocabulary (free atoms)

Shared grammar is structure-only. Below are **static-extract-js** atoms.

## find

```ser
find jsx button
find jsx [button,a,Button]
find call fetch
find call axios
find call [get,post,put,patch,delete]
find export default
find export [GET,POST]
find decorator Get
find file
find function handleSave
find variable API_PATH
```

## from / take

```ser
from jsx button take text
from children take text
from prop onClick take value
from prop onClick take reference
from argument[0] take value
from call take name
from call take owner
from export default take reference
from decorator take name
from file take path
```
