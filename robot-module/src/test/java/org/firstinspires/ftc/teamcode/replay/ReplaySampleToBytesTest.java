package org.firstinspires.ftc.teamcode.replay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/**
 * Phase 1 Task 6 — serializer test asserting the spec §8.2 worked example
 * byte for byte (docs/replay-format-spec.md §8.2; Plan.md step 6).
 *
 * <p>Plain JUnit, no FTC SDK / hardware / Robolectric: runs on a desktop
 * JVM. The expected record is copied verbatim from the spec's "Full
 * 72-byte record hex" block — never computed from the input values, per
 * AGENTS.md testing expectations.
 */
public class ReplaySampleToBytesTest {

    /**
     * Expected 72-byte record — copied verbatim from
     * docs/replay-format-spec.md §8.2 "Full 72-byte record hex"
     * (18 fields × 4 bytes, little-endian, one line per field in §5
     * order). Deliberately a dumb literal: transcribing the spec is the
     * point, recomputing it would defeat the test.
     */
    private static final byte[] EXPECTED_SPEC_82_RECORD = {
        (byte) 0xC3, (byte) 0xF5, (byte) 0x28, (byte) 0x42,
        (byte) 0x71, (byte) 0x3D, (byte) 0x86, (byte) 0x42,
        (byte) 0x9A, (byte) 0x99, (byte) 0x45, (byte) 0xC1,
        (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x01, (byte) 0x08, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xBF,
        (byte) 0x00, (byte) 0x00, (byte) 0x40, (byte) 0x3F,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0xBE,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x3F,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
    };

    /** The spec §8.2 fixture sample, constructed from the spec's raw-input column. */
    private ReplaySample sample;

    /**
     * Builds the §8.2 worked-example sample: raw (pre-rounding) input
     * values exactly as listed in the spec's §8.2 table. gamepad2 stays
     * at constructor defaults ("nothing pressed", all axes 0.00).
     */
    @Before
    public void buildSpec82Sample() {
        sample = new ReplaySample();
        sample.x = 42.2417f;
        sample.y = 67.119f;
        sample.headingDeg = -12.346f;
        sample.dtMs = 17;

        sample.gamepad1.a = true;                    // §8.2 field 4: A held (bit 0)
        sample.gamepad1.left_bumper = true;          // §8.2 field 4: left bumper held (bit 11)
        sample.gamepad1.left_stick_x = -0.50f;
        sample.gamepad1.left_stick_y = 0.75f;
        sample.gamepad1.right_stick_x = 0.00f;
        sample.gamepad1.right_stick_y = -0.25f;
        sample.gamepad1.left_trigger = 0.00f;
        sample.gamepad1.right_trigger = 1.00f;

        // gamepad2: all booleans false, all axes 0.0f — §8.2 rows 11–17.
        // (Remaining gamepad1 booleans: false by default — §8.2 row 4 lists
        // only A and left bumper held.)
    }

    /**
     * Core assertion: toBytes() output equals the spec §8.2 record
     * byte for byte — any single-byte regression fails the test.
     */
    @Test
    public void toBytes_matchesSpec82RecordByteForByte() {
        byte[] actual = sample.toBytes();
        assertArrayEquals(
            "toBytes() output must equal the spec §8.2 72-byte record exactly",
            EXPECTED_SPEC_82_RECORD,
            actual);
    }

    /**
     * Length regression guard: record size is always
     * ReplayFormatConstants.BASE_RECORD_SIZE (72), independent of content.
     */
    @Test
    public void toBytes_lengthIsAlwaysBaseRecordSize() {
        assertEquals(
            "ReplayFormatConstants.BASE_RECORD_SIZE must remain 72 (spec §9)",
            72,
            ReplayFormatConstants.BASE_RECORD_SIZE);
        byte[] actual = sample.toBytes();
        assertEquals(
            "toBytes() must always return BASE_RECORD_SIZE bytes",
            ReplayFormatConstants.BASE_RECORD_SIZE,
            actual.length);
    }
}
