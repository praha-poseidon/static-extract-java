# React/TS Vocabulary

Source of truth: `ts/vocabulary.md`.

Use generic JSX and call vocabulary first. Specific names such as `button`,
`ActionButton`, `fetch`, `axios`, or `request` are selector values, not separate
extractor features.

```ser
rule "React Button Text"
fact ui_text

find jsx button

let label =
  from jsx button take text

build {
  component: "react"
  kind: "button"
  text: label
}
```

```ser
rule "Generic Component Text"
fact ui_text

find jsx ActionButton

let label =
  from children take text

build {
  component: "ActionButton"
  text: label
}
```

```ser
rule "React Button Action"
fact ui_action

find jsx button

let label =
  from jsx button take text

let handler =
  from prop onClick take reference

build {
  component: "react"
  kind: "button"
  event: "click"
  text: label
  handler: handler
}
```

```ser
rule "Axios API Call"
fact frontend_api_call

find call axios

let method =
  from call take method

let path =
  from argument[0] take value

build {
  client: "axios"
  method: method
  path: path
}
```

```ser
rule "Import Shape"
fact import_shape

find import react

let moduleName =
  from import take module

let namedImports =
  from import take named

build {
  module: moduleName
  namedImports: namedImports
}
```

```ser
rule "Class Shape"
fact class_shape

find class UserPanel

let className =
  from class take name

let baseClass =
  from class take extends

build {
  name: className
  extends: baseClass
}
```

Use this for requests such as:

- extract React button text
- extract Chinese text from React buttons
- extract React button click actions
- list onClick handlers for buttons
- list button labels in `.tsx` or `.jsx` files
- extract frontend API calls from fetch or axios
- list API paths used by React pages
- list imports used by a file or component
- extract class component names and base classes
