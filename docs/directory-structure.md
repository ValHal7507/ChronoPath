# ChronoPath — Directory Structure & Constants Sync

This document answers two questions only:

1. Where do the two subprojects live, and where do their
   `ReplayFormatConstants.*` files go?
2. How do those two constants files stay numerically identical?

It deliberately does **not** restate any byte-level format detail —
`docs/replay-format-spec.md` (§9, §9.1) remains the single source of
truth for every constant's value. This file only says where the copies
live and how they are kept in sync.

**Out of scope:** the pre-existing TeamCode modules in the host FTC
TeamCode repository (external to this repo — see the repo-boundary
section below; unrelated to the replay system). This document neither
describes nor restructures them. The one deliberate contact point is
Phase 1 Task 9, where the recorder is called from a single existing
OpMode — that OpMode lives in `TeamCode/` of the host repo, i.e. in a
*different repository*, not merely a different folder; the integration
is specified in `docs/Plan.md`, not here.

---

## 1. The two subprojects

| Root            | Stack                                        | Purpose (one line)                                                                     |
| --------------- | -------------------------------------------- | -------------------------------------------------------------------------------------- |
| `robot-module/` | Java, FTC SDK                                | Records pose + both gamepads during a match and writes `Run<N>.*` files on the hub.    |
| `replay-app/`   | Kotlin Multiplatform + Compose Multiplatform | Loads those files and replays the match (field view, timeline, gamepad visualization). |

They share **nothing at build time** — no common Gradle module, no code
generation, no copied source. The only thing they share is the _contract_
described in `docs/replay-format-spec.md`, transcribed by hand into one
constants file per subproject (§2 below).

---

## Repo boundary — ChronoPath is its own repository

ChronoPath is a **standalone git repository**, not a folder inside a
single FTC monorepo. Its own root contains exactly the layout §4
describes — `docs/`, `robot-module/`, and (eventually) `replay-app/` —
and every path in this document is relative to *that* root, never to
another repository's root.

ChronoPath is consumed by the separate **FTC TeamCode repository**
(Team-19084-Zenith-Tests), which is external to this repo, as a **git
submodule mounted at `chronopath/`** inside it.

### The wiring contract (host repo side)

For `:robot-module` to participate in the host build, the host repo's
`settings.gradle` must contain these exact lines:

```gradle
include ':robot-module'
project(':robot-module').projectDir = new File(rootDir, 'chronopath/robot-module')
```

and its `TeamCode/build.gradle` must depend on the module:

```gradle
implementation project(':robot-module')
```

The `projectDir` remap is what actually relocates `:robot-module` from
the host repo's root down into the submodule — written as
`new File(rootDir, 'chronopath/robot-module')`, resolved against the
host build's root, not as a bare relative path. This is the wiring as
built and verified end-to-end (`:robot-module:assembleDebug`,
`:robot-module:testDebugUnitTest`, `:TeamCode:assembleDebug`).

### `$rootDir` in robot-module/build.gradle means the host root

`robot-module/build.gradle` (a file inside this repo) contains:

```gradle
apply from: "$rootDir/build.dependencies.gradle"
```

**`$rootDir` here resolves to the HOST repo's root, not ChronoPath's
own root.** Gradle's `rootDir` is the root of the Gradle build whose
`settings.gradle` includes the module — and that build starts in the
host repo, because it is the host's `settings.gradle` that includes
`:robot-module`. So `build.dependencies.gradle` must be provided by the
host repo at its root; it is not (and need not be) present in
ChronoPath.

This line is therefore **not a bug and the path is not wrong**. What it
does mean is that **`robot-module` is not a fully standalone Gradle
module**: it cannot build by itself and is only buildable when consumed
by a host repo that supplies `build.dependencies.gradle` at its root.

### OpMode integration happens outside this repo (Plan Task 9)

The OpMode that will eventually call `ReplayRecorder` (Phase 1 Task 9)
is a file in `TeamCode/` of the host FTC repo — **outside this
repository entirely**, not merely outside `robot-module/`. That is why
this document neither owns nor describes that file's content (it stays
out of scope per the "Out of scope" note above): the file lives in a
different repo, not just a different folder.

---

## 2. Where the constants files live

| File                         | Path (from repo root)                                                                         | Created by      |
| ---------------------------- | --------------------------------------------------------------------------------------------- | --------------- |
| `ReplayFormatConstants.java` | `robot-module/src/main/java/org/firstinspires/ftc/teamcode/replay/ReplayFormatConstants.java` | Phase 1 Task 3  |
| `ReplayFormatConstants.kt`   | `replay-app/shared/src/commonMain/kotlin/chronopath/replay/ReplayFormatConstants.kt`          | Phase 2 Task 11 |

Notes:

- Both live in their subproject's **main shared source set** — the Java
  one where the recorder classes can see it, the Kotlin one in
  `commonMain` so every platform target (desktop, Android) compiles
  against the same values.
- Both are **constants only, no logic** (per Plan Task 3). Anything with
  behavior belongs in the recorder/parser classes.
- Package roots (`…teamcode.replay` on the robot side, `chronopath.replay`
  on the app side) follow each subproject's own convention; they are
  chosen here and may be confirmed/overridden at project bootstrap, but
  the _file names_ and _source sets_ above are fixed.

---

## 3. Sync mechanism — manual mirroring, no codegen

**There is no codegen step.** The constants are copied between the three
artifacts (`docs/replay-format-spec.md` §9/§9.1, the Java file, the
Kotlin file) by a human or agent editing them, in the order
spec → Java → Kotlin. Plan.md Task 11 explicitly chose manual mirroring.

**Why it is manual, not automatic:**

- The values change only when the format itself changes, which is rare
  and always starts as a spec-doc edit anyway (spec-first is already the
  project's rule).
- The two build systems (FTC/Gradle robot build, KMP build) share no
  toolchain today; introducing a generator would add a build-time
  dependency and a third artifact to keep working — for a handful of
  scalars.
- A hand-maintained copy that is _checked_ (below) fails loudly at review
  time; a broken generator fails the build for everyone.

### 3.1 Comment header (required in both files)

Both constants files MUST begin with a comment header pointing at the
source of truth, in this spirit (exact wording may vary, the pointer may
not):

```text
SYNCED COPY — values come from docs/replay-format-spec.md §9 and §9.1
(single source of truth). If you edit this file, edit the other subproject's
ReplayFormatConstants file AND the spec doc in the same task, then re-run
the constants self-check diff (§3.2).
```

### 3.2 The sync checklist (run whenever §9/§9.1 — or either file — changes)

1. **Edit the spec first.** `docs/replay-format-spec.md` §9/§9.1 is the
   source; nothing else may introduce or change a constant.
2. **Edit `ReplayFormatConstants.java`** to match the spec's constant
   summary exactly (same set of constants, same values).
3. **Edit `ReplayFormatConstants.kt`** to match — same names, same
   values (language syntax differs: `static final` vs `const val`; the
   _name→value pairs_ must not differ).
4. **Re-run the self-check diff:**
   - extract the name→value pairs from the Java file,
   - extract the name→value pairs from the Kotlin file,
   - diff the two pair-lists against each other **and** against the
     tables in spec §9/§9.1.
     All three must agree; any mismatch is a bug, fixed before the task is
     considered done.
5. This checklist spells out, in concrete steps, the same obligation
   AGENTS.md rule 6 states in general terms: that
   `ReplayFormatConstants.java`, `ReplayFormatConstants.kt`, and
   `docs/replay-format-spec.md` must all three stay in sync, and that
   editing any one of them means checking and updating the other two in
   the same task. AGENTS.md does not itself define a "self-check diff" —
   that mechanism (extract name→value pairs, diff them against each
   other and against the spec) is defined here, in steps 1–4 above.

A task that changes the constant summary in the spec without completing
steps 2–4 in the same task is incomplete.

---

## 4. Directory trees (as planned)

Both trees are **as currently planned**, not exhaustive — most files do
not exist yet and are not invented here; `docs/Plan.md` governs which
task creates what.

### 4.1 `robot-module/` — robot-side recorder (Java)

```text
robot-module/
└── src/
    ├── main/java/org/firstinspires/ftc/teamcode/replay/
    │   ├── ReplayFormatConstants.java   ← Phase 1 Task 3 (format constants, spec §9/§9.1)
    │   ├── ReplaySample.java            ← Phase 1 Tasks 4–5 (sample model + toBytes())
    │   └── ReplayRecorder.java          ← Phase 1 Tasks 7–8 (buffer, writeHeader, saveToFile)
    └── test/java/                       ← Phase 1 Task 6 (plain-JUnit serializer test,
                                            asserts the spec §8 worked-example bytes)
```

- **OpMode integration point (Phase 1 Task 9):** lives _outside_ this
  tree, in one pre-existing OpMode — the recorder classes above are
  called from it; the OpMode itself is not part of `robot-module/` and
  is out of scope for this document (it lives in another repository —
  `TeamCode/` in the host FTC repo; see the repo-boundary section).
- On-robot smoke check (Phase 1 Task 10) verifies the saved file against
  the size arithmetic stated in the spec — no new code paths here.

### 4.2 `replay-app/` — KMP replay app (Kotlin + Compose)

```text
replay-app/
├── shared/                                 ← pure logic, no I/O
│   └── src/
│       ├── commonMain/kotlin/chronopath/replay/
│       │   ├── ReplayFormatConstants.kt   ← Phase 2 Task 11 (mirror of the Java file)
│       │   ├── ReplaySample.kt            ← Phase 3 Task 12
│       │   └── ReplayFileReader.kt        ← Phase 3 Tasks 13/15/17 (parseHeader/parseSample/parseFile)
│       └── commonTest/kotlin/             ← Phase 3 Tasks 14/16 (fixtures from spec §8)
├── desktop/                               ← Linux/Windows target (Phase 3 Task 18a,
│                                              readBytes via okio/java.io)
├── android/                               ← Android target (Phase 3 Task 18b,
│                                              readBytes via ContentResolver)
└── ui/                                    ← Compose Multiplatform UI, shared by both
                                               targets (Phase 4: field canvas, timeline;
                                               Phase 5: GamepadView)
```

- `shared/src/commonMain` is where **all** parsing logic lives — pure
  functions over `ByteArray`, unit-testable without touching a file
  system (Phase 3 Tasks 13–17), which is why the constants file sits
  there too.
- Platform file-reading (Plan Task 18) is intentionally confined to
  `desktop/` and `android/` — one small function per target, nothing
  shared.
- UI (`ui/`) consumes the parsed model from `shared/`; it never reads
  bytes itself and never re-derives format values.

---

## 5. What this document will not tell you

Byte offsets, field sizes, scale factors, magic bytes, bitmasks — all of
it lives in `docs/replay-format-spec.md` and nowhere else. If a value is
needed and not in spec §9/§9.1, the answer is to read the spec, not to
guess or to look for a second copy in this file.
