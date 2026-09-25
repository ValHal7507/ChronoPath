# AGENTS.md — ChronoPath, Zenith Replay System

This file is project-wide instructions for any AI agent (MiMo, or otherwise)
working in this repository. Read this in full before starting any task.
These rules override general habits/defaults — follow them exactly.

## What this project is

A two-part system for FTC Team Zenith 19084:

1. **Robot-side recorder** (Java, FTC SDK + PedroPathing) — records robot
   pose (X, Y, heading), timestamps, and both gamepads' inputs during a
   match at ~50-60Hz, and saves them to a custom binary file
   (`Run<N>.autonomous` / `Run<N>.teleop`).
2. **Replay app** (Kotlin Multiplatform + Compose Multiplatform, targeting
   Linux, Windows, Android) — loads those files and replays the match:
   field view with robot trajectory, scrubbable timeline, and gamepad
   input visualization.

The binary file format is defined in `docs/replay-format-spec.md`. That
file is the single source of truth for byte layout — never guess a byte
offset, field size, or scale factor. Always read it before writing or
editing any serialization/parsing code.

## The most important rule: FTC_SYNTAX.md

`FTC_SYNTAX.md` contains the exact, verified method signatures, field
names, and usage patterns for the FTC SDK and PedroPathing as they are
actually used in this codebase — extracted from real call sites, not
from general training knowledge.

**You MUST follow this workflow on every task that touches FTC SDK or
PedroPathing code (pose access, follower construction, path/trajectory
building, gamepad fields, OpMode lifecycle methods):**

1. **Before writing any code**, open and read `FTC_SYNTAX.md`. Use only
   the method signatures, field names, and patterns listed there. Do not
   call any FTC SDK or PedroPathing method that is not documented in that
   file — if you need one that isn't there, stop and ask instead of
   guessing.
2. **After finishing the task**, re-open `FTC_SYNTAX.md` and check every
   line of code you just wrote or edited against it, one call at a time:
   - Does every PedroPathing/FTC SDK method call match a signature listed
     in the file exactly (name, parameter types, return type)?
   - Does every field access (gamepad buttons/sticks, pose fields, etc.)
     match the names listed?
   - If you used anything not in the file, either fix it to match a
     documented pattern, or explicitly flag it in your task summary as
     "used an undocumented API — needs verification" so a human can check
     it. Never silently leave an unverified call in the code.
3. If `FTC_SYNTAX.md` is missing a pattern you need and you can find it
   correctly elsewhere in this repo's existing working code, add the
   verified pattern to `FTC_SYNTAX.md` yourself before using it, citing
   the exact file/line you copied it from. Do not add a pattern to
   `FTC_SYNTAX.md` from memory or assumption — only from a real, working
   call site in this repo or in the PedroPathing source itself.

This two-pass check (read before, verify after) applies even if the task
feels small or the method feels "obviously right." Most hallucinated code
in this kind of project looks plausible — that's exactly why the after-task
check exists.

## How to work through tasks

Work is broken into **phases**, each containing small, independently
verifiable **steps** (see `docs/Plan.md` for the full list, or
the prompt given to you for the current step).

Follow these rules for every step:

1. **Do exactly one step at a time.** Do not start the next step until
   the current one's checkpoint (test passing, build succeeding, output
   verified) is confirmed.
2. **Touch as few files as possible.** If a step seems to require editing
   more than 2-3 files, stop and say so instead of expanding scope
   silently.
3. **Pure logic first, integration last.** Serialization, parsing, and
   math functions must be written and unit-tested with hardcoded
   byte/value fixtures _before_ they are wired into an OpMode, UI, or any
   hardware/platform-specific code. Do not combine "write the logic" and
   "wire it into the app" in the same step unless the task explicitly
   says to.
4. **Never invent values.** Byte offsets, sizes, and scale factors come
   only from `docs/replay-format-spec.md`. API calls come only from
   `FTC_SYNTAX.md`. If either file doesn't answer a question you have,
   stop and ask rather than filling the gap with a guess.
5. **Report a syntax check at the end of every task.** After completing a
   step, explicitly state in your summary: "Checked against FTC_SYNTAX.md:
   [pass / issues found + what they were]." This must appear every time
   FTC SDK or PedroPathing code was touched, with no exceptions.
6. **Keep the two Kotlin/Java constants files in sync.**
   `ReplayFormatConstants.java` (robot side) and
   `ReplayFormatConstants.kt` (replay app side) must stay numerically
   identical to each other and to `docs/replay-format-spec.md`. Any time
   you edit one, check the other two and update them together in the
   same task.

## What to do when unsure

If a task requires information not present in `FTC_SYNTAX.md`,
`docs/replay-format-spec.md`, or existing working code in this repo:

- **Stop and ask a clarifying question** rather than proceeding on a
  best guess.
- Do not fabricate a plausible-sounding API, file path, class name, or
  config value to keep moving. A wrong guess that compiles is more
  dangerous than an honest "I don't know" because it fails silently
  later.

## Testing expectations

- Pure functions (byte packing/unpacking, math, format conversions) get
  unit tests with hardcoded fixture values, not just "looks right"
  visual checks.
- Where the task breakdown provides a "worked example" (e.g. hand-
  computed hex bytes in `docs/replay-format-spec.md`), tests must assert
  against those exact values, not values you compute yourself.
- Hardware/OpMode integration and UI steps are verified by an explicit,
  stated checkpoint (e.g. "file size matches header + sampleCount ×
  recordSize exactly") rather than "it seems to work."

## Summary of source-of-truth files

| File                          | Governs                                                                         |
| ----------------------------- | ------------------------------------------------------------------------------- |
| `docs/replay-format-spec.md`  | Binary file format: header, sample layout, byte offsets, scale factors, bitmask |
| `FTC_SYNTAX.md`               | Exact FTC SDK / PedroPathing method signatures and usage patterns               |
| `docs/directory-structure.md` | Where robot-side and replay-app code live and how they stay in sync             |
| `docs/Plan.md`                | The ordered list of phases and steps to follow                                  |

The java compiler is in: `/home/val3nt_n/.jdks/temurin-17.0.19`

Always check code against the relevant source-of-truth file above before
and after writing it. When in doubt, re-read before you write.

NEVER push/commit or pull anything from git unless the user specifically tells so. ALSO, NEVER USE GIT FOR EDITING OR REVERTING CHANGES. USE IT STRICTLY FOR CHECKING MODIFIED FILES, NOT TO MODIFY THEM.
