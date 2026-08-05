#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

export const REACT_BUTTON_SER = `rule "React Button Text"
fact ui_text

find jsx button

let label =
  from jsx button take text

build {
  component: "react"
  kind: "button"
  text: label
}
`;

export const REACT_BUTTON_ACTION_SER = `rule "React Button Action"
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
`;

export const REACT_FETCH_API_SER = `rule "Fetch API Call"
fact frontend_api_call

find call fetch

let method =
  from call take method

let path =
  from argument[0] take value

build {
  client: "fetch"
  method: method
  path: path
}
`;

export const REACT_AXIOS_API_SER = `rule "Axios API Call"
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
`;

export const JAVA_ANNOTATION_FACT_SER = `rule "Spec Java Annotation Fact"
fact backend_endpoint

find method
when annotation @RouteGet on method

let path =
  from annotation @RouteGet on method take attr(value)

build {
  path: path
}
`;

export const JAVA_CONFIG_FIELD_SER = `rule "Spec Java Config Field"
fact config_key

find field
when annotation @ConfigProperty on field

let configKey =
  from annotation @ConfigProperty on field take attr(value)

let fieldName =
  from field take name

build {
  key: configKey
  field: fieldName
}
`;

async function main(argv) {
  const options = parseArgs(argv);
  if (!options.extractor || !options.request || !options.out) {
    throw new Error("Usage: generate_ser.mjs --extractor <extractor> --request <file> --out <file>");
  }
  const request = await readFile(options.request, "utf8");
  const ser = generateSer(options.extractor, request);
  await mkdir(dirname(resolve(options.out)), { recursive: true });
  await writeFile(options.out, ser, "utf8");
}

export function generateSer(extractor, request) {
  const normalized = request.toLowerCase();
  if (extractor === "react" && hasAny(normalized, ["action", "动作", "点击", "onclick", "handler", "处理函数"])) {
    return REACT_BUTTON_ACTION_SER;
  }
  if (extractor === "react" && hasAny(normalized, ["button", "按钮"]) && hasAny(normalized, ["text", "文案", "中文"])) {
    return REACT_BUTTON_SER;
  }
  if (extractor === "react" && hasAny(normalized, ["axios"])) {
    return REACT_AXIOS_API_SER;
  }
  if (extractor === "react" && hasAny(normalized, ["fetch"])) {
    return REACT_FETCH_API_SER;
  }
  if (extractor === "react" && hasAny(normalized, ["api", "接口", "request", "请求", "call", "调用"])) {
    return REACT_AXIOS_API_SER;
  }
  if (extractor === "java-jdt" && hasAny(normalized, ["config", "configuration", "配置", "配置项", "config_key"])) {
    return JAVA_CONFIG_FIELD_SER;
  }
  if (extractor === "java-jdt" && hasAny(normalized, ["annotation", "annotated", "注解", "endpoint", "端点", "接口", "backend"])) {
    return JAVA_ANNOTATION_FACT_SER;
  }
  throw new Error(`Unsupported SER authoring request for extractor ${extractor}: ${request.trim()}`);
}

function hasAny(value, words) {
  return words.some((word) => value.includes(word.toLowerCase()));
}

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case "--extractor":
        options.extractor = requireValue(argv, ++i, arg);
        break;
      case "--request":
        options.request = requireValue(argv, ++i, arg);
        break;
      case "--out":
        options.out = requireValue(argv, ++i, arg);
        break;
      default:
        throw new Error(`Unknown option: ${arg}`);
    }
  }
  return options;
}

function requireValue(argv, index, option) {
  const value = argv[index];
  if (!value || value.startsWith("--")) {
    throw new Error(`Missing value for ${option}`);
  }
  return value;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main(process.argv.slice(2)).catch((error) => {
    process.stderr.write(JSON.stringify({
      status: "ERROR",
      message: error.message
    }) + "\n");
    process.exitCode = 1;
  });
}
