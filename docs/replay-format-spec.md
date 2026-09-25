# ChronoPath — Binary File Format Specification (v1)

**Status:** single source of truth. Every serializer (Java, robot side) and
parser (Kotlin, replay app) must implement exactly what is written here.
Never guess a byte offset, size, or scale factor — if this document does not
answer a question, stop and ask.

Covers the two file extensions produced by the recorder:

- `Run<N>.autonomous` — an autonomous-period recording
- `Run<N>.teleop` — a teleop-period recording

Both extensions use the **identical** binary layout below; they differ only
in the `mode` header byte (and the file extension, which must agree with it).

---

## 1. General conventions

### 1.1 Types

| Type      | Size    | Encoding                                             |
| --------- | ------- | ---------------------------------------------------- |
| `uint8`   | 1 byte  | unsigned integer                                     |
| `uint16`  | 2 bytes | unsigned integer                                     |
| `uint32`  | 4 bytes | unsigned integer                                     |
| `float32` | 4 bytes | IEEE-754 binary32 (same as Java `float` / C `float`) |

There are no other field types in v1. There are no variable-length fields,
no strings, no padding inside records (§5), and no checksum/CRC.

### 1.2 Endianness — LITTLE-ENDIAN, without exception

**Every multi-byte field in the file — `uint16`, `uint32`, and `float32`
alike — is stored little-endian** (least-significant byte first). This
applies to the header and to every record. There is no big-endian data
anywhere in the format.

All fields sit at offsets that are a multiple of their own size (the layout
below is 4-byte aligned throughout), so parsers may use aligned reads; the
endianness rule is still the only rule that matters for byte order.

### 1.3 Units

| Quantity     | Unit                     | Notes                                                                                                                                                                        |
| ------------ | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `x`, `y`     | **inches**               | FTC field coordinates, matching PedroPathing `PoseFactory.degrees()` poses used by the robot code                                                                            |
| `headingDeg` | **degrees**              | counter-clockwise positive, `0°` = +X axis; stored as-is (recorded value should be normalized to `(-180, 180]` by the recorder, but the parser must accept any finite value) |
| `dtMs`       | **milliseconds**         | integer, exact (no rounding needed)                                                                                                                                          |
| stick axes   | unitless `[-1.00, 1.00]` | raw FTC `Gamepad` stick values                                                                                                                                               |
| triggers     | unitless `[0.00, 1.00]`  | raw FTC `Gamepad` trigger values                                                                                                                                             |

### 1.4 Two-decimal rounding rule (applies to every `float32` record field)

Requirement: pose values are stored as **raw `float32`, pre-rounded to
exactly 2 decimal places** (e.g. `42.24`, `67.12`, `-12.35`). To make
"byte-identical output from two independent implementations" possible, the
rounding is defined as an exact algorithm on IEEE-754 **binary64**
(Java `double`), executed in this order:

```text
1. r = abs(v) * 100.0                 // binary64 multiply
2. n = floor(r + 0.5)                 // binary64 add + floor → integral value
3. rounded = copysign(n / 100.0, v)   // binary64 divide, restore sign of v
4. store (float32) rounded            // binary64 → binary32 conversion
```

Notes:

- The rule is **half away from zero** (`-12.345 → -12.35`, `+12.345 →
+12.35`) — as computed on the binary64 results of step 1, exactly as
  written. Implementations must not use banker's rounding or
  round-toward-+∞ (`Math.round`) on negative values.
- Steps 1–3 use only IEEE-754 binary64 operations, which are
  deterministic and identical on the robot (JVM/Android) and the desktop
  (JVM), so both sides produce the same `rounded` double.
- The rule is **idempotent**: feeding the parsed `float32` back through
  steps 1–4 reproduces the same 4 bytes (e.g. `42.24 → 42.240001678… →
42.24 → 0x4228F5C3`). Re-serializing a parsed sample is therefore safe.
- The rule applies to **all** `float32` fields in a record: pose _and_
  gamepad stick/trigger values. Header fields contain no `float32`.

### 1.5 File size arithmetic (exact)

```text
fileSizeBytes == HEADER_SIZE + sampleCount * recordSize
fileSizeBytes == 16 + sampleCount * recordSize
```

- `HEADER_SIZE` is **always 16 bytes** (§4), regardless of channels.
- `recordSize` is read from the header (§4, offset 12) and must equal
  `BASE_RECORD_SIZE + 4 × popcount(channels)` = `72 + 4 × popcount(channels)`
  (§6). For v1 files the only legal values are **72** (channels = 0) and
  **76** (channels = 0x01).

A parser must reject a file whose length does not satisfy this equation
exactly (this is the Phase 1 on-robot smoke test).

---

## 2. Overall file structure

```text
+-----------------------+  offset 0
|  Header (16 bytes)    |
+-----------------------+  offset 16
|  Record 0             |  recordSize bytes
+-----------------------+  offset 16 + recordSize
|  Record 1             |
+-----------------------+
|  ...                  |
+-----------------------+  offset 16 + (sampleCount-1) * recordSize
|  Record sampleCount-1 |
+-----------------------+  EOF = 16 + sampleCount * recordSize
```

Records are packed back-to-back with no gap, in recording order (record 0
is the sample taken first in time).

---

## 3. Magic bytes & version

The first 4 bytes of every file are the fixed magic sequence:

| Byte | Hex    | Meaning |
| ---- | ------ | ------- |
| 0    | `0x5A` | `'Z'`   |
| 1    | `0x4E` | `'N'`   |
| 2    | `0x54` | `'T'`   |
| 3    | `0x48` | `'H'`   |

ASCII string **`ZNTH`** (abbreviated "Zenith"), stored as plain bytes
(ASCII is a subset of both Latin-1 and UTF-8, so byte-comparing is enough —
no decoding involved).

A parser MUST reject any file whose first 4 bytes are not `5A 4E 54 48`.

Immediately after the magic comes a `uint16` **format version = 1**
(header offset 4). A parser MUST reject any version other than `1`
(future versions will redefine fields; bumping the version is the escape
hatch for incompatible changes, see §6.4).

---

## 4. Header field table

Fixed size: **16 bytes** (`HEADER_SIZE = 16`). All offsets are absolute
from the start of the file. All multi-byte fields little-endian (§1.2).

| #   | Offset      | Size | Type     | Name          | Meaning                                                                                                                              |
| --- | ----------- | ---- | -------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 0   | `0x00` (0)  | 4    | bytes    | `magic`       | Must be `5A 4E 54 48` (`"ZNTH"`) — §3                                                                                                |
| 1   | `0x04` (4)  | 2    | `uint16` | `version`     | Format version; must be `1`                                                                                                          |
| 2   | `0x06` (6)  | 1    | `uint8`  | `mode`        | `0x00` = autonomous, `0x01` = teleop. Any other value → reject. Must agree with the file extension (§7)                              |
| 3   | `0x07` (7)  | 1    | `uint8`  | `channels`    | Optional-channel bitmask — §6. v1 recorders write `0x00`                                                                             |
| 4   | `0x08` (8)  | 4    | `uint32` | `sampleCount` | Number of records that follow the header. Must be ≥ 1                                                                                |
| 5   | `0x0C` (12) | 2    | `uint16` | `recordSize`  | Bytes per record. Must equal `72 + 4 × popcount(channels)` — §6. Authoritative value for parsing (parsers read it, then validate it) |
| 6   | `0x0E` (14) | 2    | `uint16` | `reserved`    | Must be `0x0000`. Present only to pad the header to 16 bytes and keep every later record 4-byte aligned                              |

**Sample interval:** there is **no** interval/`dt` field in the header,
because the sampling interval is _not_ fixed. Recording runs at a nominal
~50–60 Hz (documented expectation, not stored in the file); the actual
elapsed time between consecutive samples is carried **per record** in
`dtMs` (§5). `sampleCount` plus the per-record `dtMs` values fully
determine the timeline — nothing external is needed to parse a file.

---

## 5. Sample record field table

Base record size (`channels = 0`): **72 bytes** (`BASE_RECORD_SIZE = 72`).
Offsets are **within the record** (record _i_ starts at file offset
`16 + i × recordSize`). All fields are 4 bytes, all little-endian (§1.2),
and every field is 4-byte aligned within the record.

| #   | Offset      | Size | Type      | Name                     | Scale factor                   | Meaning                                                                                         |
| --- | ----------- | ---- | --------- | ------------------------ | ------------------------------ | ----------------------------------------------------------------------------------------------- |
| 0   | `0x00` (0)  | 4    | `float32` | `x`                      | raw float (2-dp rounded, §1.4) | robot X position, inches                                                                        |
| 1   | `0x04` (4)  | 4    | `float32` | `y`                      | raw float (2-dp rounded, §1.4) | robot Y position, inches                                                                        |
| 2   | `0x08` (8)  | 4    | `float32` | `headingDeg`             | raw float (2-dp rounded, §1.4) | robot heading, degrees CCW                                                                      |
| 3   | `0x0C` (12) | 4    | `uint32`  | `dtMs`                   | 1 (integer milliseconds)       | elapsed ms since the previous sample; for record 0, ms from recording start to the first sample |
| 4   | `0x10` (16) | 4    | `uint32`  | `gamepad1.buttons`       | bitmask (§5.1)                 | pressed-button states of driver-1 gamepad                                                       |
| 5   | `0x14` (20) | 4    | `float32` | `gamepad1.left_stick_x`  | raw float (2-dp rounded, §1.4) | left stick X, `[-1.00, 1.00]`                                                                   |
| 6   | `0x18` (24) | 4    | `float32` | `gamepad1.left_stick_y`  | raw float (2-dp rounded, §1.4) | left stick Y, `[-1.00, 1.00]`                                                                   |
| 7   | `0x1C` (28) | 4    | `float32` | `gamepad1.right_stick_x` | raw float (2-dp rounded, §1.4) | right stick X, `[-1.00, 1.00]`                                                                  |
| 8   | `0x20` (32) | 4    | `float32` | `gamepad1.right_stick_y` | raw float (2-dp rounded, §1.4) | right stick Y, `[-1.00, 1.00]`                                                                  |
| 9   | `0x24` (36) | 4    | `float32` | `gamepad1.left_trigger`  | raw float (2-dp rounded, §1.4) | left trigger, `[0.00, 1.00]`                                                                    |
| 10  | `0x28` (40) | 4    | `float32` | `gamepad1.right_trigger` | raw float (2-dp rounded, §1.4) | right trigger, `[0.00, 1.00]`                                                                   |
| 11  | `0x2C` (44) | 4    | `uint32`  | `gamepad2.buttons`       | bitmask (§5.1)                 | pressed-button states of driver-2 gamepad                                                       |
| 12  | `0x30` (48) | 4    | `float32` | `gamepad2.left_stick_x`  | raw float (2-dp rounded, §1.4) | left stick X, `[-1.00, 1.00]`                                                                   |
| 13  | `0x34` (52) | 4    | `float32` | `gamepad2.left_stick_y`  | raw float (2-dp rounded, §1.4) | left stick Y, `[-1.00, 1.00]`                                                                   |
| 14  | `0x38` (56) | 4    | `float32` | `gamepad2.right_stick_x` | raw float (2-dp rounded, §1.4) | right stick X, `[-1.00, 1.00]`                                                                  |
| 15  | `0x3C` (60) | 4    | `float32` | `gamepad2.right_stick_y` | raw float (2-dp rounded, §1.4) | right stick Y, `[-1.00, 1.00]`                                                                  |
| 16  | `0x40` (64) | 4    | `float32` | `gamepad2.left_trigger`  | raw float (2-dp rounded, §1.4) | left trigger, `[0.00, 1.00]`                                                                    |
| 17  | `0x44` (68) | 4    | `float32` | `gamepad2.right_trigger` | raw float (2-dp rounded, §1.4) | right trigger, `[0.00, 1.00]`                                                                   |
| —   | `0x48` (72) | 4    | `float32` | `turretAngleDeg`         | raw float (2-dp rounded, §1.4) | **only present when `channels` bit 0 is set** — §6                                              |

Every gamepad axis/button name above matches the corresponding FTC SDK
`Gamepad` field (`left_stick_x`, `left_trigger`, …) exactly, so the
recorder copies values straight from `gamepad1`/`gamepad2` without
renaming.

**No range validation, ever.** The ranges in the table (`[-1.00, 1.00]`
for sticks, `[0.00, 1.00]` for triggers) describe what the SDK normally
produces — they are **not** a parsing rule. Parsers MUST NOT clamp,
rescale, or otherwise alter an analog value they read: a corrupt or
quirky `left_stick_x = 1.34` is returned as `1.34`, unchanged, so
parse→serialize round-trips stay byte-identical. Recorders write whatever
the SDK reports, rounded to 2 dp only (§1.4), with no clamping either.

### 5.1 Gamepad button bitmask (`uint32`, fields at record offsets 16 and 44)

Bit _b_ set to `1` = button currently held (level state, not edge). All
unlisted bits are reserved and MUST be written as `0` (parsers ignore them).

| Bit   | Mask         | Button               | FTC SDK `Gamepad` field |
| ----- | ------------ | -------------------- | ----------------------- |
| 0     | `0x00000001` | A / cross            | `a`                     |
| 1     | `0x00000002` | B / circle           | `b`                     |
| 2     | `0x00000004` | X / square           | `x`                     |
| 3     | `0x00000008` | Y / triangle         | `y`                     |
| 4     | `0x00000010` | D-pad up             | `dpad_up`               |
| 5     | `0x00000020` | D-pad down           | `dpad_down`             |
| 6     | `0x00000040` | D-pad left           | `dpad_left`             |
| 7     | `0x00000080` | D-pad right          | `dpad_right`            |
| 8     | `0x00000100` | Back / share         | `back`                  |
| 9     | `0x00000200` | Guide / PS           | `guide`                 |
| 10    | `0x00000400` | Start / options      | `start`                 |
| 11    | `0x00000800` | Left bumper          | `left_bumper`           |
| 12    | `0x00001000` | Right bumper         | `right_bumper`          |
| 13    | `0x00002000` | Left stick click     | `left_stick_button`     |
| 14    | `0x00004000` | Right stick click    | `right_stick_button`    |
| 15    | `0x00008000` | reserved — write `0` | —                       |
| 16–31 | `0xFFFF0000` | reserved — write `0` | —                       |

(The SDK's PS-layout aliases such as `circle`/`share` map onto the same
physical buttons the table above names via `b`/`back`; only the primary
name is recorded.)

Trigger _pressed_ thresholds are not stored — the raw analog
`left_trigger`/`right_trigger` float values are enough for visualization,
and the app derives any threshold itself.

---

## 6. Channel bitmask

The header's `channels` byte (offset 7, `uint8`) marks which optional
channels are present in every record of this file.

| Bit | Mask   | Channel      | Field                                                      | Field size | Status in v1                                                                                                                                                      |
| --- | ------ | ------------ | ---------------------------------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0   | `0x01` | turret angle | `turretAngleDeg` — `float32`, degrees, 2-dp rounded (§1.4) | 4 bytes    | **reserved for Phase 7.** Layout is fixed now (it lands at record offset 72); v1 recorders MUST write the bit as `0`. Parsers MUST accept the bit set (see below) |
| 1–7 | `0xFE` | reserved     | —                                                          | —          | MUST be written as `0`. Enabling any of these requires a **version bump** (§6.4) because their record size is undefined to a v1 parser                            |

### 6.1 How the layout changes when a channel is enabled

- The **header never changes size**: always 16 bytes, same field offsets.
- The **base record layout never changes**: fields 0–17 keep their exact
  offsets (§5).
- Enabled channel fields are **appended after the base fields**, in
  ascending bit order. With only bit 0 defined, `turretAngleDeg` sits at
  record offset 72 (immediately after `gamepad2.right_trigger`).
- Therefore `recordSize = 72 + 4 × popcount(channels)`, which for v1 is
  `72` (bit clear) or `76` (bit set).

### 6.2 Parsing rule

Read `channels` and `recordSize` from the header, then **validate**:

```text
recordSize == 72 + 4 * popcount(channels)      // and recordSize ∈ {72, 76} in v1
```

If the check fails, reject the file. `recordSize` from the header is what
the parser actually uses to stride through records; the check exists so a
corrupted/inconsistent header is caught before any offset math runs.

### 6.3 Writing rule (v1 recorders)

Write `channels = 0x00` and emit 72-byte records. Do not emit the turret
field until Phase 7 turns the bit on (spec-doc-first, per Plan.md).

### 6.4 When the version changes

Bump `version` (header offset 4) only for changes that a v1 parser could
misread: altering/removing an existing field, changing a size, endianness,
or scale — or assigning record size to reserved channel bits 1–7. Adding a
new channel bit with a pre-declared size (like bit 0) does not by itself
require a version bump, because `recordSize` + `channels` already describe
the layout unambiguously.

---

## 7. File naming convention

```text
Run<N>.autonomous      N = 1, 2, 3, …
Run<N>.teleop          N = 1, 2, 3, …
```

- `<N>` is a **smallest-unused positive integer**, chosen by the recorder
  at save time: scan the target directory for files matching
  `Run*.<same extension>` and pick the lowest `N ≥ 1` for which
  `Run<N>.<extension>` does not yet exist.
- `<N>` is counted **per extension** — `Run3.autonomous` and `Run3.teleop`
  may coexist; they are different files.
- The number is **not** stored inside the file (nothing in the header
  depends on the filename). Only the `mode` byte ↔ extension agreement
  (§4) is part of the format contract.

**Why a counter instead of a timestamp:** the Control Hub's wall clock is
unreliable (may be unset or wrong at match time), timestamps
(`Run20260925-141503.teleop`) are long and annoying to read/compare in a
file browser, and a smallest-unused counter needs no persistent state file
— just one directory listing at save time. It also never overwrites an
existing run, which is the property that actually matters for match data.

---

## 8. Worked example — exact bytes

This is the fixture Phase 1 (Java serializer tests) and Phase 3 (Kotlin
parser tests) must copy **verbatim**. Every byte below is derived from the
tables above; nothing is left for the reader to compute.

### 8.1 Fake file header — `Run1.teleop`

Fake file parameters: `mode = teleop (1)`, `channels = 0`, `sampleCount =
250`, `recordSize = 72`.

| Offset | Size | Field         | Engineering value    | Encoded (LE) | Bytes         |
| ------ | ---- | ------------- | -------------------- | ------------ | ------------- |
| `0x00` | 4    | `magic`       | `"ZNTH"`             | —            | `5A 4E 54 48` |
| `0x04` | 2    | `version`     | 1                    | `0x0001`     | `01 00`       |
| `0x06` | 1    | `mode`        | teleop               | `0x01`       | `01`          |
| `0x07` | 1    | `channels`    | no optional channels | `0x00`       | `00`          |
| `0x08` | 4    | `sampleCount` | 250                  | `0x000000FA` | `FA 00 00 00` |
| `0x0C` | 2    | `recordSize`  | 72                   | `0x0048`     | `48 00`       |
| `0x0E` | 2    | `reserved`    | 0                    | `0x0000`     | `00 00`       |

**Full 16-byte header hex:**

```text
5A 4E 54 48 01 00 01 00 FA 00 00 00 48 00 00 00
```

(For an autonomous run of the same shape: `mode = 00`, filename
`Run1.autonomous`, header = `5A 4E 54 48 01 00 00 00 FA 00 00 00 48 00 00 00`.)

Expected file size for this header (§1.5):
`16 + 250 × 72 = 16 + 18000 = **18016 bytes**`.

### 8.2 Fake sample record

Engineering-unit inputs as the recorder sees them (pre-rounding), then the
result of each table entry:

| #   | Offset | Field                    | Raw input                                  | After 2-dp rule (§1.4) | Encoded word | Bytes (LE)    |
| --- | ------ | ------------------------ | ------------------------------------------ | ---------------------- | ------------ | ------------- |
| 0   | `0x00` | `x`                      | `42.2417` in                               | `42.24`                | `0x4228F5C3` | `C3 F5 28 42` |
| 1   | `0x04` | `y`                      | `67.119` in                                | `67.12`                | `0x42863D71` | `71 3D 86 42` |
| 2   | `0x08` | `headingDeg`             | `-12.346`°                                 | `-12.35`               | `0xC145999A` | `9A 99 45 C1` |
| 3   | `0x0C` | `dtMs`                   | `17` ms (integer, no rounding)             | —                      | `0x00000011` | `11 00 00 00` |
| 4   | `0x10` | `gamepad1.buttons`       | A held (bit 0) + left bumper held (bit 11) | `0x00000801`           | `0x00000801` | `01 08 00 00` |
| 5   | `0x14` | `gamepad1.left_stick_x`  | `-0.50`                                    | `-0.50`                | `0xBF000000` | `00 00 00 BF` |
| 6   | `0x18` | `gamepad1.left_stick_y`  | `0.75`                                     | `0.75`                 | `0x3F400000` | `00 00 40 3F` |
| 7   | `0x1C` | `gamepad1.right_stick_x` | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 8   | `0x20` | `gamepad1.right_stick_y` | `-0.25`                                    | `-0.25`                | `0xBE800000` | `00 00 80 BE` |
| 9   | `0x24` | `gamepad1.left_trigger`  | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 10  | `0x28` | `gamepad1.right_trigger` | `1.00`                                     | `1.00`                 | `0x3F800000` | `00 00 80 3F` |
| 11  | `0x2C` | `gamepad2.buttons`       | nothing pressed                            | `0x00000000`           | `0x00000000` | `00 00 00 00` |
| 12  | `0x30` | `gamepad2.left_stick_x`  | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 13  | `0x34` | `gamepad2.left_stick_y`  | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 14  | `0x38` | `gamepad2.right_stick_x` | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 15  | `0x3C` | `gamepad2.right_stick_y` | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 16  | `0x40` | `gamepad2.left_trigger`  | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |
| 17  | `0x44` | `gamepad2.right_trigger` | `0.00`                                     | `0.00`                 | `0x00000000` | `00 00 00 00` |

**Full 72-byte record hex:**

```text
C3 F5 28 42 71 3D 86 42 9A 99 45 C1 11 00 00 00
01 08 00 00 00 00 00 BF 00 00 40 3F 00 00 00 00
00 00 80 BE 00 00 00 00 00 00 80 3F 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00
```

(line-wrapped for readability; the record is one contiguous 72-byte
sequence — 18 fields × 4 bytes).

Spot-check of the non-obvious words (all reproducible with any IEEE-754
tool, e.g. pack `42.24` as binary32 little-endian):

- `42.24 → 0x4228F5C3` → stored `C3 F5 28 42` (byte 0 = `C3` because
  little-endian)
- `67.12 → 0x42863D71` → stored `71 3D 86 42`
- `-12.35 → 0xC145999A` → stored `9A 99 45 C1` (sign bit set → top byte
  of the word is `C1`, which lands last in little-endian)
- `buttons 0x00000801` → bytes `01 08 00 00` (low byte first)
- `17 → 0x00000011` → bytes `11 00 00 00`

### 8.3 Optional-channel variant (Phase 7 preview)

Same record as §8.2 but with `channels = 0x01` in the header:

- `recordSize` becomes `76` (`48 00` → `4C 00`)
- 4 bytes are appended at record offset 72: `turretAngleDeg`
  e.g. `45.00°` → `0x42340000` → `00 00 34 42`
- header size, header offsets, and base-record offsets are unchanged
- file size = `16 + sampleCount × 76`

---

## 9. Constant summary (for `ReplayFormatConstants.java` / `.kt`)

Transcribe these verbatim; the three copies (this file, Java, Kotlin) must
stay numerically identical.

| Constant           | Value                                                  |
| ------------------ | ------------------------------------------------------ |
| magic bytes        | `0x5A 0x4E 0x54 0x48` (`"ZNTH"`)                       |
| `VERSION`          | `1`                                                    |
| `HEADER_SIZE`      | `16`                                                   |
| `BASE_RECORD_SIZE` | `72`                                                   |
| `MODE_AUTONOMOUS`  | `0`                                                    |
| `MODE_TELEOP`      | `1`                                                    |
| `CHANNEL_TURRET`   | `0x01` (bit 0)                                         |
| rounding           | 2 decimals, half away from zero (§1.4) — `100.0` scale |
| `dtMs` scale       | `1` (integer milliseconds)                             |
| endianness         | little-endian (all multi-byte fields)                  |

### 9.1 Gamepad button bitmask constants (from §5.1)

Phase 1 Task 3 needs these alongside the table above — transcribe into
both constants files:

| Constant                | Value        | Meaning                                        |
| ----------------------- | ------------ | ---------------------------------------------- |
| `BUTTON_A`              | `0x00000001` | bit 0 — `a`                                    |
| `BUTTON_B`              | `0x00000002` | bit 1 — `b`                                    |
| `BUTTON_X`              | `0x00000004` | bit 2 — `x`                                    |
| `BUTTON_Y`              | `0x00000008` | bit 3 — `y`                                    |
| `BUTTON_DPAD_UP`        | `0x00000010` | bit 4 — `dpad_up`                              |
| `BUTTON_DPAD_DOWN`      | `0x00000020` | bit 5 — `dpad_down`                            |
| `BUTTON_DPAD_LEFT`      | `0x00000040` | bit 6 — `dpad_left`                            |
| `BUTTON_DPAD_RIGHT`     | `0x00000080` | bit 7 — `dpad_right`                           |
| `BUTTON_BACK`           | `0x00000100` | bit 8 — `back`                                 |
| `BUTTON_GUIDE`          | `0x00000200` | bit 9 — `guide`                                |
| `BUTTON_START`          | `0x00000400` | bit 10 — `start`                               |
| `BUTTON_LEFT_BUMPER`    | `0x00000800` | bit 11 — `left_bumper`                         |
| `BUTTON_RIGHT_BUMPER`   | `0x00001000` | bit 12 — `right_bumper`                        |
| `BUTTON_LEFT_STICK`     | `0x00002000` | bit 13 — `left_stick_button`                   |
| `BUTTON_RIGHT_STICK`    | `0x00004000` | bit 14 — `right_stick_button`                  |
| `BUTTONS_RESERVED_MASK` | `0xFFFF8000` | bits 15–31 — writers set to `0`, parsers ignore |

---

## 10. Parser rejection checklist

Reject (throw/return error) if any of:

1. file shorter than 16 bytes;
2. bytes 0–3 ≠ `5A 4E 54 48`;
3. `version` ≠ 1;
4. `mode` not in `{0, 1}`, or disagrees with the file extension;
5. `sampleCount` = 0;
6. `recordSize ≠ 72 + 4 × popcount(channels)` (nor `∈ {72, 76}` in v1);
7. `reserved` (offset 14) ≠ 0;
8. any reserved `channels` bit 1–7 set;
9. `fileSize ≠ 16 + sampleCount × recordSize`.

This list is exhaustive. In particular, out-of-range analog values are
**not** grounds for rejection and must not be clamped — see the
no-range-validation rule in §5.
