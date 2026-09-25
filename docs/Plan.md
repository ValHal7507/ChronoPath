# ChronoPath Replay System — Task Breakdown for MiMo v2.6

This document breaks the FTC robot replay system named ChronoPath into small, independently
verifiable tasks. Each task should be completed and checked before moving to
the next. Pure/serialization code gets unit tests with hardcoded byte values
_before_ anything touches hardware, UI, or OpModes.

**Rules for the agent:**

- Before writing any serialization code, open and re-read
  `docs/replay-format-spec.md`. Never guess a byte offset.
- Complete tasks in order. Do not start a task until the previous task's
  test/checkpoint passes.
- Each task should touch as few files as possible. If a task seems to
  require editing more than 2-3 files, stop and ask to split it further.
- Pure functions (serialize/parse/format-math) get unit tests with
  hardcoded byte values before anything touches hardware, UI, or OpModes.

---

## Phase 0 — Spec document (before any code)

1. Create `docs/replay-format-spec.md` containing: magic bytes, header
   field table with exact byte offsets and sizes, sample record field
   table with exact byte offsets/sizes/scaling factors, channel bitmask
   table, file naming convention (`Run<N>.autonomous` / `Run<N>.teleop`),
   and a worked example (hand-computed hex bytes for one fake sample).
   This file is the single source of truth — every later task references
   it, never re-derives it.
2. Create `docs/directory-structure.md` listing the two subprojects
   (`robot-module/`, `replay-app/`) and how they'll stay in sync (shared
   constants file, mirrored manually or via codegen — decide now,
   document it).

## Phase 1 — Robot-side (Java, FTC SDK), one task each

3. Create `ReplayFormatConstants.java` — magic bytes, header size, record
   size, scale factors, channel bitmask constants, transcribed exactly
   from the spec doc. No logic.
4. Create `ReplaySample.java` — a plain data class/struct holding one
   sample's fields in engineering units (float X, float Y, float
   headingDeg, gamepad states). No serialization yet.
5. Write `ReplaySample.toBytes()` — pure function, sample → fixed-size
   `byte[]`, using the constants from task 3. Unit-testable in isolation
   (no OpMode needed).
6. Write a standalone Java test (plain JUnit, no robot hardware) that
   creates a known `ReplaySample`, calls `toBytes()`, and asserts the
   exact byte values against the worked example from the spec doc.
7. Create `ReplayRecorder.java` — a buffer class with
   `addSample(pose, gamepad1, gamepad2, dtMs)` (calls task 5's serializer,
   appends to a preallocated byte buffer) and
   `writeHeader(sampleCount, mode)`.
8. Write `ReplayRecorder.saveToFile(String path)` — flush header + buffer
   to a file on Control Hub storage. Test this with a hardcoded fake
   buffer, not a live robot.
9. Integrate into one existing OpMode (pick the simplest current auto):
   call `recorder.addSample(...)` once per loop iteration, call
   `saveToFile()` in `stop()`. This is the ONLY task that touches an
   actual OpMode — keep it isolated so a mistake here doesn't cascade
   into the format code.
10. On-robot smoke test: run the OpMode, pull the file off the Control
    Hub, verify file size matches
    `header size + sampleCount × recordSize` exactly.

## Phase 2 — Shared constants (Kotlin mirror)

11. Create `ReplayFormatConstants.kt` in the KMP project — manually
    mirror task 3's Java constants. These two files must stay
    numerically identical; diff them against each other.

## Phase 3 — KMP replay app, parsing layer only (no UI yet)

12. Create `ReplaySample.kt` — matching data class, engineering units.
13. Write `ReplayFileReader.parseHeader(bytes): ReplayHeader` — pure
    function, no I/O yet, takes a `ByteArray`.
14. Write a unit test using the same worked-example hex bytes from the
    spec doc, assert `parseHeader` produces the expected values.
15. Write `ReplayFileReader.parseSample(bytes, offset): ReplaySample` —
    pure function.
16. Write a unit test using the worked-example sample bytes, assert
    exact field values come back out (round-trip test against task 6's
    expected output — same numbers, so the test fixture can be copied).
17. Write `ReplayFileReader.parseFile(fullBytes): ReplayRun` (header +
    `List<ReplaySample>`) composing tasks 13+15 in a loop.
18. Wire up platform-specific file reading (`readBytes(path): ByteArray`)
    separately per target: Linux/Windows via `okio`/`java.io`, Android
    via `ContentResolver`/file picker. Three small tasks, not one.
19. Load one real `.teleop` file produced in Phase 1 and print
    `sampleCount`, first sample, last sample to console. This is the
    first true end-to-end checkpoint — confirms robot output and app
    parser agree.

## Phase 4 — Compose UI, smallest pieces first

20. Static field canvas: draw the FTC field background image at correct
    aspect ratio, no robot yet.
21. Draw a single static robot marker (triangle/arrow) at a hardcoded
    (X, Y, heading) — confirms the coordinate transform (inches → canvas
    pixels) is correct before animating anything.
22. Draw the full trajectory as a static polyline from all samples in a
    loaded run — no playback yet, just "does the shape look like the
    match."
23. Add a `currentSampleIndex` state variable and a plain `Slider` —
    moving the slider updates the robot marker position by re-reading
    that sample. This proves scrubbing works before adding automatic
    playback.
24. Add play/pause: a coroutine ticker that advances
    `currentSampleIndex` based on real elapsed time vs. accumulated `dt`
    values, respecting a playback speed multiplier.
25. Add rewind/fast-forward/jump-to-time as separate small functions
    operating on the same `currentSampleIndex` state.

## Phase 5 — Gamepad visualization (separate from field rendering)

26. Build a standalone `GamepadView` composable that takes a single
    `ReplaySample`'s gamepad fields and draws stick positions + pressed
    buttons — develop and test with a hardcoded fake sample, not wired
    to playback yet.
27. Wire `GamepadView` to `currentSampleIndex` alongside the field view.

## Phase 6 — Platform builds

28. Verify desktop (Linux) build runs and loads a real file.
29. Verify Windows build (if cross-compiling/testing) separately.
30. Verify Android build separately — this is where file-picker and
    permissions issues surface; keep it isolated from the desktop tasks
    so failures don't get conflated.

## Phase 7 — Only after everything above works

31. Add a new optional channel (e.g., turret angle) by: updating the
    spec doc bitmask table → updating Java constants/recorder →
    updating Kotlin constants/parser → updating tests → updating UI.
    Treat every future channel addition as its own mini version of
    Phases 0-3, never skip the spec-doc-first step.

    # Prompt should contain:

1. Context — what the overall project is and which phase we're in.
1. Goal — exactly one concrete thing to implement.
1. Existing architecture — what has already been implemented.
1. Files it may modify.
1. Files it must not modify.
1. Technical requirements.
1. Constraints.
1. Acceptance criteria.
1. Tests it must perform.
1. Required final report.
