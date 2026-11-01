---
name: hudhud-workspace-rules
description: "Apply Hudhud workspace rules for local-first dependencies, D/E-only file storage, minimal non-destructive edits, and isolated operation code. Use before downloading, installing, editing files, or structuring a feature in this workspace."
---

# Hudhud Workspace Rules

Apply these rules to every task in the Hudhud workspace. The workspace-level `AGENTS.md` contains the always-on requirements; this skill provides the execution checklist.

## 1. Check locally before using the internet

Before obtaining a tool, dependency, package, sample, or reference:

1. Inspect the workspace, installed tools, local package caches, and already available dependencies.
2. Reuse an appropriate local resource.
3. Use the internet only when the required resource is unavailable locally and the task cannot proceed without it.
4. Never run a remote installer or fetch-and-execute command as a shortcut.

## 2. Keep work off drive C

- Save downloads, packages, generated files, temporary files, and other task artifacts only on drive D or E.
- Before using a tool that writes caches or output by default, configure its cache/output/temp path to D or E.
- Do not intentionally create or save task files on C.
- If a required tool cannot be configured to avoid writing to C, stop and ask the user before proceeding.

## 3. Make minimal edits to existing files

1. Read the existing file and enough surrounding context before editing it.
2. Apply only the targeted additions or changes needed for the request.
3. Preserve unrelated content and existing user changes.
4. Do not delete and recreate an existing file, replace its complete contents, or use a generated rewrite when a focused patch can make the change.
5. After editing, inspect the affected section and confirm unrelated sections remain intact.

New files may be created when needed; this rule does not authorize replacing existing files with new ones.

## 4. Isolate code by operation

- Give each distinct user operation or feature its own implementation file/class/module, following the project's existing structure.
- Keep each operation's UI/state handling and business logic separate from other operations.
- Put genuinely shared behavior in a clearly named shared component and call it from the operations; do not couple operations through a monolithic handler or God class.
- Keep boundaries simple: do not create extra modules or abstractions that add complexity without improving separation.
- When changing an operation, avoid unrelated operation files unless a shared contract must change.

## Before acting

- [ ] Confirm the needed resource is not already available locally.
- [ ] Confirm any required output, cache, and temporary paths are on D or E.
- [ ] Read existing files before making a focused patch.
- [ ] Identify the operation boundary and keep its implementation isolated.
- [ ] Inspect the final diff or changed sections for unintended replacement or unrelated edits.
