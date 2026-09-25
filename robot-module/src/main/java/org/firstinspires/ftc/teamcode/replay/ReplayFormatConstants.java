/*
 * SYNCED COPY — values come from docs/replay-format-spec.md §9 and §9.1
 * (single source of truth). If you edit this file, edit the other subproject's
 * ReplayFormatConstants file AND the spec doc in the same task, then re-run
 * the constants self-check diff (§3.2).
 */
package org.firstinspires.ftc.teamcode.replay;

/**
 * Format constants for ChronoPath replay files, transcribed verbatim from
 * docs/replay-format-spec.md §9 and §9.1. Constants holder only — no logic
 * (Plan Task 3).
 */
public final class ReplayFormatConstants {

    /* ------------------------------------------------------------------ */
    /* spec §9 — constant summary                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Magic bytes "ZNTH" (spec §9 row "magic bytes"; byte sequence from §3).
     * Fixed 4-byte file signature — do not mutate.
     */
    public static final byte[] MAGIC_BYTES = {0x5A, 0x4E, 0x54, 0x48};

    /** Format version (spec §9). */
    public static final int VERSION = 1;

    /** Header size in bytes (spec §9). */
    public static final int HEADER_SIZE = 16;

    /** Record size in bytes when no optional channels are enabled (spec §9). */
    public static final int BASE_RECORD_SIZE = 72;

    /** mode byte: autonomous (spec §9). */
    public static final int MODE_AUTONOMOUS = 0;

    /** mode byte: teleop (spec §9). */
    public static final int MODE_TELEOP = 1;

    /** channels bitmask: turret channel, bit 0 (spec §9). */
    public static final int CHANNEL_TURRET = 0x01;

    /**
     * spec §9 row "rounding": 2 decimals, half away from zero (§1.4) —
     * 100.0 scale. (The decimals/algorithm are prose in §9; the typed value
     * is the scale.)
     */
    public static final double ROUNDING_SCALE = 100.0;

    /** spec §9 row "dtMs scale": 1 (integer milliseconds). */
    public static final int DTMS_SCALE = 1;

    // spec §9 row "endianness": little-endian (all multi-byte fields) —
    // no typed value; documented here and in the spec only.

    /* ------------------------------------------------------------------ */
    /* spec §9.1 — gamepad button bitmask constants (from §5.1)            */
    /* ------------------------------------------------------------------ */

    /** bit 0 — a */
    public static final int BUTTON_A = 0x00000001;
    /** bit 1 — b */
    public static final int BUTTON_B = 0x00000002;
    /** bit 2 — x */
    public static final int BUTTON_X = 0x00000004;
    /** bit 3 — y */
    public static final int BUTTON_Y = 0x00000008;
    /** bit 4 — dpad_up */
    public static final int BUTTON_DPAD_UP = 0x00000010;
    /** bit 5 — dpad_down */
    public static final int BUTTON_DPAD_DOWN = 0x00000020;
    /** bit 6 — dpad_left */
    public static final int BUTTON_DPAD_LEFT = 0x00000040;
    /** bit 7 — dpad_right */
    public static final int BUTTON_DPAD_RIGHT = 0x00000080;
    /** bit 8 — back */
    public static final int BUTTON_BACK = 0x00000100;
    /** bit 9 — guide */
    public static final int BUTTON_GUIDE = 0x00000200;
    /** bit 10 — start */
    public static final int BUTTON_START = 0x00000400;
    /** bit 11 — left_bumper */
    public static final int BUTTON_LEFT_BUMPER = 0x00000800;
    /** bit 12 — right_bumper */
    public static final int BUTTON_RIGHT_BUMPER = 0x00001000;
    /** bit 13 — left_stick_button */
    public static final int BUTTON_LEFT_STICK = 0x00002000;
    /** bit 14 — right_stick_button */
    public static final int BUTTON_RIGHT_STICK = 0x00004000;
    /** bits 15–31 — writers set to 0, parsers ignore (spec §9.1) */
    public static final int BUTTONS_RESERVED_MASK = 0xFFFF8000;

    /** Non-instantiable constants holder. */
    private ReplayFormatConstants() {
    }
}
