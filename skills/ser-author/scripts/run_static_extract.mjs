#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { mkdir, readdir, writeFile } from "node:fs/promises";
import { delimiter, dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { generateSer } from "./generate_ser.mjs";

// skills live under this extractor repo; workspace root is parent of repo root.
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
// sibling layout: codeGraphProjects/{static-extract-spec,static-extract-java,static-extract-js}
const workspaceRoot = resolve(repoRoot, "..");

async function main(argv) {
  const options = parseArgs(argv);
  if (!options.project || !options.mode || !options.outDir) {
    throw new Error("Usage: run_static_extract.mjs --project <dir> --mode <generate|extract|generate-and-extract> --request <file> --out-dir <dir>");
  }

  const project = resolve(options.project);
  const outDir = resolve(options.outDir);
  await mkdir(outDir, { recursive: true });

  const extractor = options.extractor ?? await detectExtractor(project);
  const generatedRule = options.rule ? resolve(options.rule) : join(outDir, "generated.ser");
  const factsFile = options.out ?? join(outDir, "facts.jsonl");
  const reportFile = join(outDir, "report.json");

  if (options.mode === "generate" || options.mode === "generate-and-extract") {
    if (!options.request) {
      throw new Error("--request is required when mode generates SER.");
    }
    const request = readFileSync(resolve(options.request), "utf8");
    await writeFile(generatedRule, generateSer(extractor, request), "utf8");
  }

  let extractReport = null;
  if (options.mode === "extract" || options.mode === "generate-and-extract") {
    const rule = options.rule ? resolve(options.rule) : generatedRule;
    extractReport = runExtract(extractor, project, rule, factsFile, options);
  }

  const result = {
    extractor,
    mode: options.mode,
    serFile: generatedRule,
    factsFile: extractReport ? factsFile : null,
    reportFile,
    extractReport
  };
  await writeFile(reportFile, JSON.stringify(result, null, 2) + "\n", "utf8");
  process.stdout.write(JSON.stringify(result, null, 2) + "\n");
}

async function detectExtractor(project) {
  const files = await scanFiles(project, [".tsx", ".jsx", ".ts", ".js", ".java"]);
  if (files.some((file) => [".tsx", ".jsx"].includes(extname(file)))) {
    return "react";
  }
  if (files.some((file) => extname(file) === ".java")) {
    return "java-jdt";
  }
  throw new Error("Unable to detect extractor from project files.");
}

function runExtract(extractor, project, rule, factsFile, options) {
  if (extractor === "react") {
    const cli = options.cliTs
      ? resolve(options.cliTs)
      : resolveJsCli();
    const initReport = runJson("node", [
      cli,
      "init",
      "--project", project
    ]);
    const tryReport = runJson("node", [
      cli,
      "try",
      "--project", project,
      "--source", project,
      "--rule", rule
    ]);
    const diagnoseReport = tryReport.resultCount > 0 ? null : runJson("node", [
      cli,
      "diagnose",
      "--project", project,
      "--source", project,
      "--rule", rule
    ]);
    const runReport = runJson("node", [
      cli,
      "run",
      "--project", project,
      "--source", project,
      "--rule", rule,
      "--out", factsFile
    ]);
    return {
      initReport,
      tryReport,
      diagnoseReport,
      runReport,
      resultCount: runReport.resultCount
    };
  }
  if (extractor === "java-jdt") {
    const command = javaCommand(options);
    const javaSources = scanFilesSync(project, [".java"]);
    if (javaSources.length === 0) {
      throw new Error(`No Java source files found under project: ${project}`);
    }
    const tryArgs = [
      "try",
      "--project", project,
      ...javaSources.flatMap((source) => ["--source", source]),
      "--rule", rule
    ];
    const diagnoseArgs = [
      "diagnose",
      "--project", project,
      ...javaSources.flatMap((source) => ["--source", source]),
      "--rule", rule
    ];
    const initReport = runJavaJson(command, [
      "init",
      "--project", project
    ]);
    const tryReport = runJavaJson(command, tryArgs);
    const diagnoseReport = tryReport.resultCount > 0 ? null : runJavaJson(command, diagnoseArgs);
    const runReport = runJavaJson(command, [
      "run",
      "--project", project,
      "--source", project,
      "--rule", rule,
      "--out", factsFile
    ]);
    return {
      initReport,
      tryReport,
      diagnoseReport,
      runReport,
      resultCount: runReport.resultCount
    };
  }
  throw new Error(`Unsupported extractor: ${extractor}`);
}

function runJson(command, args) {
  const stdout = execFileSync(command, args, { encoding: "utf8" });
  return JSON.parse(stdout);
}

function runJavaJson(command, args) {
  if (command.kind === "direct") {
    return runJson(command.command, [...command.prefixArgs, ...args]);
  }
  return runJson(command.command, [
    "-cp",
    command.classpath,
    "com.poseidon.javastatic.extract.cli.JavaStaticExtractCli",
    ...args
  ]);
}

function javaCommand(options) {
  if (options.cliJava) {
    return {
      kind: "direct",
      command: resolve(options.cliJava),
      prefixArgs: []
    };
  }
  if (process.env.STATIC_EXTRACT_JAVA_CLI) {
    return {
      kind: "direct",
      command: process.env.STATIC_EXTRACT_JAVA_CLI,
      prefixArgs: []
    };
  }
  if (process.env.STATIC_EXTRACT_JAVA_CLASSPATH) {
    return {
      kind: "classpath",
      command: process.env.JAVA ?? "java",
      classpath: process.env.STATIC_EXTRACT_JAVA_CLASSPATH
    };
  }
  const packagedCandidates = [
    resolve(workspaceRoot, "static-extract-java/cli/target/appassembler/bin/static-extract-java"),
    resolve(workspaceRoot, "static-extract/java/cli/target/appassembler/bin/static-extract-java"),
    resolve(workspaceRoot, "static-extract-java/cli/target/appassembler/bin/static-extract-java")
  ];
  for (const packaged of packagedCandidates) {
    if (existsSync(packaged)) {
      return {
        kind: "direct",
        command: packaged,
        prefixArgs: []
      };
    }
  }
  if (commandExists("static-extract-java")) {
    return {
      kind: "direct",
      command: "static-extract-java",
      prefixArgs: []
    };
  }
  throw new Error("Unable to find static-extract-java. Install the release package, run Maven package, pass --cli-java, or set STATIC_EXTRACT_JAVA_CLASSPATH.");
}

function resolveJsCli() {
  if (process.env.STATIC_EXTRACT_JS_CLI) {
    return resolve(process.env.STATIC_EXTRACT_JS_CLI);
  }
  const candidates = [
    resolve(workspaceRoot, "static-extract-js/cli/static-extract-ts.mjs"),
    resolve(workspaceRoot, "static-extract/ts/cli/static-extract-ts.mjs"),
    resolve(workspaceRoot, "static-extract-js/cli/static-extract-ts.mjs")
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  throw new Error("Unable to find static-extract-js CLI. Set STATIC_EXTRACT_JS_CLI or place static-extract-js as a sibling of static-extract-spec.");
}

function commandExists(command) {
  const paths = (process.env.PATH ?? "").split(delimiter).filter(Boolean);
  return paths.some((dir) => existsSync(join(dir, command)));
}

async function scanFiles(root, extensions) {
  if (!existsSync(root)) {
    return [];
  }
  if (!statSync(root).isDirectory()) {
    return extensions.includes(extname(root)) ? [root] : [];
  }
  const files = [];
  for (const entry of await readdir(root, { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name === "target" || entry.name === ".git") {
      continue;
    }
    const path = join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...await scanFiles(path, extensions));
    } else if (entry.isFile() && extensions.includes(extname(entry.name))) {
      files.push(path);
    }
  }
  return files;
}

function scanFilesSync(root, extensions) {
  if (!existsSync(root)) {
    return [];
  }
  if (!statSync(root).isDirectory()) {
    return extensions.includes(extname(root)) ? [root] : [];
  }
  const files = [];
  for (const entry of readdirSyncSafe(root)) {
    if (entry.name === "node_modules" || entry.name === "target" || entry.name === ".git" || entry.name === ".ser") {
      continue;
    }
    const path = join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...scanFilesSync(path, extensions));
    } else if (entry.isFile() && extensions.includes(extname(entry.name))) {
      files.push(path);
    }
  }
  return files.sort();
}

function readdirSyncSafe(root) {
  return statSync(root).isDirectory() ? readdirSync(root, { withFileTypes: true }) : [];
}

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case "--project":
        options.project = requireValue(argv, ++i, arg);
        break;
      case "--mode":
        options.mode = requireValue(argv, ++i, arg);
        break;
      case "--request":
        options.request = requireValue(argv, ++i, arg);
        break;
      case "--rule":
        options.rule = requireValue(argv, ++i, arg);
        break;
      case "--extractor":
        options.extractor = requireValue(argv, ++i, arg);
        break;
      case "--cli-java":
        options.cliJava = requireValue(argv, ++i, arg);
        break;
      case "--cli-ts":
        options.cliTs = requireValue(argv, ++i, arg);
        break;
      case "--out":
        options.out = requireValue(argv, ++i, arg);
        break;
      case "--out-dir":
        options.outDir = requireValue(argv, ++i, arg);
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

main(process.argv.slice(2)).catch((error) => {
  process.stderr.write(JSON.stringify({
    status: "ERROR",
    message: error.message
  }) + "\n");
  process.exitCode = 1;
});
