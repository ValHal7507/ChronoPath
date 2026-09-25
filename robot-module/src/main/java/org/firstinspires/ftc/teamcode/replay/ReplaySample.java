package org.firstinspires.ftc.teamcode.replay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One recorded sample in engineering units — the in-memory form of a
 * single record from docs/replay-format-spec.md §5 (fields 0–17).
 *
 * <p>Plain data holder with one serializer:
 * <ul>
 *   <li>{@code toBytes()} is the only serialization path — packs this
 *       sample into the spec §5 record (Phase 1 Task 5). Parsing is
 *       Phase 3's job, not this class's.</li>
 *   <li>No validation or clamping — spec §5 forbids range checks on
 *       analog values (round-trip fidelity).</li>
 *   <li>No FTC SDK types — values are primitives copied out of a
 *       {@code Gamepad}, so this class is testable without any robot
 *       runtime.</li>
 * </ul>
 *
 * <p>Field names match spec §5 / §5.1 (which match the FTC SDK
 * {@code Gamepad} field names) exactly, one-to-one, so serialization and
 * tests can reference the spec table directly.
 */
public final class ReplaySample {

    /** spec §5 field 0 — robot X position, inches. */
    public float x;
    /** spec §5 field 1 — robot Y position, inches. */
    public float y;
    /** spec §5 field 2 — robot heading, degrees CCW. */
    public float headingDeg;
    /** spec §5 field 3 — elapsed ms since previous sample (record 0: since recording start). */
    public int dtMs;

    /** spec §5 fields 4–10 — driver-1 gamepad state. */
    public final GamepadState gamepad1 = new GamepadState();
    /** spec §5 fields 11–17 — driver-2 gamepad state. */
    public final GamepadState gamepad2 = new GamepadState();

    /** No-arg constructor; all fields start at their zero/default value. */
    public ReplaySample() {
    }

    /**
     * Packs this sample into one fixed-size record exactly per
     * docs/replay-format-spec.md §5, channels = 0:
     * <ul>
     *   <li>every multi-byte field little-endian (§1.2, explicit
     *       {@link ByteOrder#LITTLE_ENDIAN} — never the platform
     *       default);</li>
     *   <li>every {@code float32} field (pose and gamepad axes) pre-rounded
     *       with the exact §1.4 algorithm via {@link #round2dp(float)};</li>
     *   <li>{@code dtMs} written as-is, integer milliseconds (§1.3);</li>
     *   <li>button booleans packed per §5.1 using the
     *       {@code BUTTON_*} constants (reserved bits stay 0).</li>
     * </ul>
     * No channels/turret handling — always exactly
     * {@link ReplayFormatConstants#BASE_RECORD_SIZE} bytes (Phase 7).
     *
     * @return a new byte array of BASE_RECORD_SIZE bytes (never null)
     */
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(ReplayFormatConstants.BASE_RECORD_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        // spec §5 fields 0–3 — pose and dt
        buf.putFloat(0x00, round2dp(x));
        buf.putFloat(0x04, round2dp(y));
        buf.putFloat(0x08, round2dp(headingDeg));
        buf.putInt(0x0C, dtMs);

        // spec §5 fields 4–10 — gamepad1
        buf.putInt(0x10, packButtons(gamepad1));
        buf.putFloat(0x14, round2dp(gamepad1.left_stick_x));
        buf.putFloat(0x18, round2dp(gamepad1.left_stick_y));
        buf.putFloat(0x1C, round2dp(gamepad1.right_stick_x));
        buf.putFloat(0x20, round2dp(gamepad1.right_stick_y));
        buf.putFloat(0x24, round2dp(gamepad1.left_trigger));
        buf.putFloat(0x28, round2dp(gamepad1.right_trigger));

        // spec §5 fields 11–17 — gamepad2
        buf.putInt(0x2C, packButtons(gamepad2));
        buf.putFloat(0x30, round2dp(gamepad2.left_stick_x));
        buf.putFloat(0x34, round2dp(gamepad2.left_stick_y));
        buf.putFloat(0x38, round2dp(gamepad2.right_stick_x));
        buf.putFloat(0x3C, round2dp(gamepad2.right_stick_y));
        buf.putFloat(0x40, round2dp(gamepad2.left_trigger));
        buf.putFloat(0x44, round2dp(gamepad2.right_trigger));

        return buf.array();
    }

    /**
     * spec §1.4 two-decimal rounding, implemented exactly as specified —
     * IEEE-754 binary64 throughout, fixed order of operations:
     * <pre>
     * 1. r = abs(v) * 100.0
     * 2. n = floor(r + 0.5)
     * 3. rounded = copysign(n / 100.0, v)
     * 4. return (float) rounded
     * </pre>
     * Half away from zero. Deliberately NOT {@code Math.round} (that is
     * half-up toward +∞ and diverges on negative values) — spec §1.4
     * requires bit-for-bit agreement with the Kotlin parser's inverse.
     */
    private static float round2dp(float v) {
        double vd = v;                                  // exact widening; binary64 from here
        double r = Math.abs(vd) * 100.0;                // step 1: binary64 multiply
        double n = Math.floor(r + 0.5);                 // step 2: binary64 add + floor
        double rounded = Math.copySign(n / 100.0, vd);  // step 3: divide, restore sign
        return (float) rounded;                         // step 4: binary64 → binary32
    }

    /**
     * spec §5.1 — OR together the {@code BUTTON_*} constants for every
     * held button; reserved bits (15–31 and any unlisted) stay 0.
     */
    private static int packButtons(GamepadState g) {
        int mask = 0;
        if (g.a)                   mask |= ReplayFormatConstants.BUTTON_A;
        if (g.b)                   mask |= ReplayFormatConstants.BUTTON_B;
        if (g.x)                   mask |= ReplayFormatConstants.BUTTON_X;
        if (g.y)                   mask |= ReplayFormatConstants.BUTTON_Y;
        if (g.dpad_up)             mask |= ReplayFormatConstants.BUTTON_DPAD_UP;
        if (g.dpad_down)           mask |= ReplayFormatConstants.BUTTON_DPAD_DOWN;
        if (g.dpad_left)           mask |= ReplayFormatConstants.BUTTON_DPAD_LEFT;
        if (g.dpad_right)          mask |= ReplayFormatConstants.BUTTON_DPAD_RIGHT;
        if (g.back)                mask |= ReplayFormatConstants.BUTTON_BACK;
        if (g.guide)               mask |= ReplayFormatConstants.BUTTON_GUIDE;
        if (g.start)               mask |= ReplayFormatConstants.BUTTON_START;
        if (g.left_bumper)         mask |= ReplayFormatConstants.BUTTON_LEFT_BUMPER;
        if (g.right_bumper)        mask |= ReplayFormatConstants.BUTTON_RIGHT_BUMPER;
        if (g.left_stick_button)   mask |= ReplayFormatConstants.BUTTON_LEFT_STICK;
        if (g.right_stick_button)  mask |= ReplayFormatConstants.BUTTON_RIGHT_STICK;
        return mask;
    }

    /**
     * Engineering-unit state of one gamepad: the 15 named buttons of
     * spec §5.1 as individual booleans, plus the 6 stick/trigger axes of
     * spec §5 as floats. Shared by {@code gamepad1} and {@code gamepad2}
     * (identical layout in the record, differing only by base offset).
     *
     * <p>Booleans, not a packed int: bit packing against the §5.1 masks
     * happens only in {@code toBytes()} (Task 5) / the parser (Phase 3),
     * never here.
     */
    public static final class GamepadState {

        /* spec §5.1 buttons — bit 0..14 (names = SDK Gamepad fields) */
        public boolean a;
        public boolean b;
        public boolean x;
        public boolean y;
        public boolean dpad_up;
        public boolean dpad_down;
        public boolean dpad_left;
        public boolean dpad_right;
        public boolean back;
        public boolean guide;
        public boolean start;
        public boolean left_bumper;
        public boolean right_bumper;
        public boolean left_stick_button;
        public boolean right_stick_button;

        /* spec §5 axes — raw SDK values, stored as-is (no clamping) */
        public float left_stick_x;
        public float left_stick_y;
        public float right_stick_x;
        public float right_stick_y;
        public float left_trigger;
        public float right_trigger;

        /** No-arg constructor; all buttons false, all axes 0.0f. */
        public GamepadState() {
        }
    }
}
