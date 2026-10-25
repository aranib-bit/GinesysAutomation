# Comment-Tag Convention (Better Comments)

Uses [aaron-bond.better-comments](https://github.com/aaron-bond/better-comments) in VS Code to
color-highlight comments by intent. This applies to **both** `SeleniumFramework3.141_NEW` (Java)
and `D:\PerformanceAutomationTesting` (k6/TypeScript) — the tag meanings below are shared across
both, and each repo's `.vscode/settings.json` carries the same tag/color config so a comment reads
the same way regardless of which project you're in.

## The rule

Tag a comment only when it changes how the reader should treat it — not every comment needs one.
Plain descriptive comments (what this branch does, what a UI dialog is called) stay untagged.

| Tag | Meaning | When to use it |
|-----|---------|-----------------|
| `// !` | **Alert** — a deliberate, non-obvious decision or hard constraint. Reversing it reintroduces a bug or breaks something silently. | "must NOT use X here", "deliberately NOT a default method", "oldest-first scanning is required here" |
| `// ?` | **Question** — open uncertainty that still needs verification or a decision. | A locator/behavior that hasn't been confirmed against the live app yet |
| `// TODO:` | **Todo** — genuine unimplemented work or an explicit coverage gap. | Stub page objects, unimplemented branches, known-missing test scenarios |
| `// *` | **Highlight** — useful background/context that isn't a gotcha, just worth noticing. | Perf notes, "why we did it this way" explanations, forensic run notes |
| `// //` | **Strikethrough** — commented-out dead code. | Reserved for code temporarily disabled, not prose |

## What NOT to tag

- **String literals that happen to start with `//`** (e.g. placeholder XPath locators like
  `By.xpath("//TODO-search-input")`) are code, not comments — never retag these; doing so breaks
  the locator.
- **Javadoc/XML prose that merely references another location's tag** (e.g. `{@code //TODO-}` in
  a class-level Javadoc describing a placeholder locator elsewhere) — tagging these would imply
  the todo lives at that line, which it doesn't.
- **Comments describing product/UI terminology** (e.g. "the Warning popup", "a WARNING is logged")
  — the capitalized word is naming an app dialog or log level, not flagging something for the
  reader.

## IDE setup

- **VS Code**: install `aaron-bond.better-comments` from the Marketplace. Each repo's
  `.vscode/settings.json` already pins the tag/color config above — no per-user setup needed once
  the extension is installed.
- **IntelliJ IDEA**: Better Comments itself doesn't exist for IntelliJ. Two options, neither of
  which touches project files (per this repo's rule against modifying `.idea/`):
  - Install the **Better Highlights** plugin (JetBrains Marketplace) and add `!`, `?`, `*` as
    custom highlight patterns.
  - Or just rely on IntelliJ's built-in TODO pattern matching (**Settings → Editor → TODO**,
    an IDE-level setting, not a project file) — add patterns matching `!.*`, `\*.*` alongside the
    default `TODO` pattern so they show up in the TODO tool window with distinct colors.
