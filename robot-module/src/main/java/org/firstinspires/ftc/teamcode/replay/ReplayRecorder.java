package org.firstinspires.ftc.teamcode.replay;

import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.hardware.Gamepad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory replay recording buffer (Phase 1 Task 7, Plan.md): accumulates
 * serialized samples and produces the 16-byte file header — both per
 * docs/replay-format-spec.md (§5 via {@link ReplaySample#toBytes()}, §4 for
 * the header, §1.2 little-endian).
 *
 * <p>Scope of this task:
 * <ul>
 *   <li>{@link #addSample(Pose, Gamepad, Gamepad, int)} — translates
 *       FTC/Pedro types into a fresh {@link ReplaySample} (ReplaySample
 *       itself stays free of SDK types, Task 4's design), serializes it
 *       with {@link ReplaySample#toBytes()}, appends the record.</li>
 *   <li>{@link #writeHeader(int, int)} — spec §4 header, channels = 0,
 *       recordSize = BASE_RECORD_SIZE (optional channels are Phase 7,
 *       out of scope).</li>
 *   <li><b>No file I/O</b> (no File/FileOutputStream/saveToFile), no
 *       OpMode, no hardware — that is Task 8 / Task 9.</li>
 * </ul>
 *
 * <p>Spec §5 note: every gamepad axis/button name above matches the FTC
 * SDK {@code Gamepad} field name exactly, so {@link #copyGamepad} copies
 * fields without renaming.
 */
public final class ReplayRecorder {

    /**
     * Serialized records in call order — one spec §5 record (72 bytes)
     * per element. A {@code List<byte[]>} rather than a single
     * {@code ByteArrayOutputStream}: sample count is exactly
     * {@code records.size()} (the input writeHeader needs), each element
     * stays one well-formed record that Task 8 can stream to disk without
     * first materializing a second full-length copy, and no
     * synchronized-append overhead or single-giant-array growth.
     */
    private final List<byte[]> records = new ArrayList<byte[]>();

    /** No-arg constructor; empty buffer (0 samples). */
    public ReplayRecorder() {
    }

    /** Number of samples currently buffered (input for {@link #writeHeader}). */
    public int getSampleCount() {
        return records.size();
    }

    /**
     * Read-only view of the buffered records in call order; each element
     * is exactly {@link ReplayFormatConstants#BASE_RECORD_SIZE} bytes.
     */
    public List<byte[]> getRecords() {
        return Collections.unmodifiableList(records);
    }

    /**
     * Translates one sampling instant into a spec §5 record and appends
     * it: pose x/y (inches) and heading (converted to degrees and
     * normalized to (-180, 180] per §1.3 — Pedro's {@code Pose.heading()}
     * is radians in [0, 2π), FTC_SYNTAX.md §7.2), dtMs as-is, and all
     * 15 buttons + 6 axes of each gamepad (§5.1/§5, no clamping, no
     * validation).
     *
     * @param pose    robot pose at this sample (inches, radians heading)
     * @param gamepad1 driver-1 gamepad state
     * @param gamepad2 driver-2 gamepad state
     * @param dtMs    elapsed ms since the previous sample (record 0: since
     *                recording start) — spec §5 field 3
     */
    public void addSample(Pose pose, Gamepad gamepad1, Gamepad gamepad2, int dtMs) {
        ReplaySample sample = new ReplaySample();
        sample.x = (float) pose.x();
        sample.y = (float) pose.y();
        sample.headingDeg = headingDegrees(pose);
        sample.dtMs = dtMs;
        copyGamepad(gamepad1, sample.gamepad1);
        copyGamepad(gamepad2, sample.gamepad2);
        records.add(sample.toBytes());
    }

    /**
     * spec §1.3: heading in degrees CCW (0° = +X), and the <b>recorder</b>
     * normalizes it to (-180, 180]. Needed because Pedro's {@code Pose}
     * stores heading via {@code Angle.normalize}, which maps to
     * [0, 2π) — a -12.346° pose reads back as 347.654° until normalized
     * here. ({@code Pose.heading()} itself is radians, FTC_SYNTAX.md §7.2.)
     */
    private static float headingDegrees(Pose pose) {
        double h = Math.toDegrees(pose.heading());
        h = h - 360.0 * Math.floor((h + 180.0) / 360.0);   // → [-180, 180)
        if (h == -180.0) {
            h = 180.0;                                     // spec range is (-180, 180]
        }
        return (float) h;
    }

    /**
     * Produces the 16-byte file header of spec §4, little-endian (§1.2):
     * magic "ZNTH" (§3), version, mode byte, channels = 0x00 (v1
     * recorder, §6), sampleCount, recordSize = BASE_RECORD_SIZE,
     * reserved = 0x0000. No channels/turret support in this task.
     *
     * @param sampleCount number of records that will follow the header (§4: ≥ 1)
     * @param mode        {@link ReplayFormatConstants#MODE_AUTONOMOUS} or
     *                    {@link ReplayFormatConstants#MODE_TELEOP}
     * @return a new 16-byte array; never null
     * @throws IllegalArgumentException for any mode other than MODE_AUTONOMOUS /
     *                                  MODE_TELEOP — writers must never emit an
     *                                  invalid mode (spec §4: any other value →
     *                                  reject; spec §10 checklist item 4)
     */
    public byte[] writeHeader(int sampleCount, int mode) {
        if (mode != ReplayFormatConstants.MODE_AUTONOMOUS
                && mode != ReplayFormatConstants.MODE_TELEOP) {
            throw new IllegalArgumentException(
                    "mode must be MODE_AUTONOMOUS ("
                            + ReplayFormatConstants.MODE_AUTONOMOUS
                            + ") or MODE_TELEOP ("
                            + ReplayFormatConstants.MODE_TELEOP
                            + "), got " + mode);
        }

        ByteBuffer header = ByteBuffer.allocate(ReplayFormatConstants.HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        header.put(ReplayFormatConstants.MAGIC_BYTES);                       // 0x00: magic "ZNTH" (§3)
        header.putShort((short) ReplayFormatConstants.VERSION);              // 0x04: version uint16
        header.put((byte) mode);                                             // 0x06: mode uint8
        header.put((byte) 0x00);                                             // 0x07: channels uint8 = 0 (§6)
        header.putInt(sampleCount);                                          // 0x08: sampleCount uint32
        header.putShort((short) ReplayFormatConstants.BASE_RECORD_SIZE);     // 0x0C: recordSize uint16
        header.putShort((short) 0x0000);                                     // 0x0E: reserved uint16

        return header.array();
    }

    /**
     * Copies all 15 buttons (§5.1) and all 6 axes (§5) of one SDK
     * {@link Gamepad} into a {@link ReplaySample.GamepadState} — field
     * names are identical on both sides (spec §5's SDK-name note), so the
     * copy is literal. No clamping or validation (spec §5).
     */
    private static void copyGamepad(Gamepad src, ReplaySample.GamepadState dst) {
        // spec §5.1 — buttons, bits 0..14
        dst.a = src.a;
        dst.b = src.b;
        dst.x = src.x;
        dst.y = src.y;
        dst.dpad_up = src.dpad_up;
        dst.dpad_down = src.dpad_down;
        dst.dpad_left = src.dpad_left;
        dst.dpad_right = src.dpad_right;
        dst.back = src.back;
        dst.guide = src.guide;
        dst.start = src.start;
        dst.left_bumper = src.left_bumper;
        dst.right_bumper = src.right_bumper;
        dst.left_stick_button = src.left_stick_button;
        dst.right_stick_button = src.right_stick_button;
        // spec §5 — analog axes
        dst.left_stick_x = src.left_stick_x;
        dst.left_stick_y = src.left_stick_y;
        dst.right_stick_x = src.right_stick_x;
        dst.right_stick_y = src.right_stick_y;
        dst.left_trigger = src.left_trigger;
        dst.right_trigger = src.right_trigger;
    }
}
