# FTC_SYNTAX.md — Authoritative syntax guide for this repository

> **Audience:** AI agents (and humans) writing new code in this repo.
> **Rule:** every snippet in this file was cross-checked against the *actual* library
> binaries/sources in the Gradle cache and against the existing TeamCode sources.
> If a snippet here disagrees with a library, this file wins only after re-verification.
>
> **Library versions this guide targets** (from `build.dependencies.gradle`):

| Component | Artifact | Version |
|---|---|---|
| FTC SDK | `org.firstinspires.ftc:RobotCore/FtcCommon/Hardware/Vision/Blocks/Inspection/OnBotJava/RobotServer` | **12.0.0** |
| Pedro Pathing core | `com.pedropathing:core` (transitive via revhub) | **3.0.0** |
| Pedro Pathing RevHub layer | `com.pedropathing:revhub` | **3.0.0** |
| Pedro autotune | `com.pedropathing:tuning` | **1.0.0** |
| Ivy command framework | `com.pedropathing.ivy:core` | **1.1.0** |
| Ivy↔Pedro bridge | `com.pedropathing.ivy:pedro` | **1.1.0** |
| Elipse telemetry/config UI | `com.zenith.elipse:elipse-ftc` | **0.1.0** |
| byLazar panels | `com.bylazar:fullpanels` | 1.0.12 |
| FTC-AutoTune (JitPack) | `com.github.aaravdhawan25.FtcAutoTune:pidautotuner-core/ftc` | v0.3.7 (**declared, no TeamCode imports — do not assume it**) |
| FTC Dashboard | `com.acmerobotics.dashboard:dashboard` | 0.4.16 (**declared, never imported — do not use**) |
| Java language level | `build.common.gradle` | **Java 8** — no `var`, no records, no switch-expressions, no text blocks |

> ⚠️ **This is Pedro Pathing v3.** There is **no** `PathBuilder`, `PathSegment`,
> `LineTo`/`SplineTo`, `ParameterList`, `MecanumDriveConstants`, `setMaxVel()`,
> `followPath()`, `waitUntilComplete()`, or Road-Runner-style `Action`/`SequentialAction`.
> Code written against v1/v2 tutorials **will not compile here.**

---

## 1. Project layout & naming

```
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/
├── Kickoff/                  (capital K — season folder)
│   ├── ExampleAuto.java      → .Kickoff            (Pedro auto template)
│   ├── ExampleTeleOp.java    → .Kickoff            (Pedro teleop drive template)
│   ├── auton/                → .Kickoff.auton      (LAST_RESORT)
│   ├── teleop/               → .Kickoff.teleop     (TELESLOP, Test*, sloptest)
│   └── subsystems/           → .Kickoff.subsystems (DriveTrain, Shooter, Intake, LinearSlides, LimelightVision)
├── pedro/                    → .pedro              (Constants, Tuning)
│   └── procedures/           → .pedro.procedures   (autotune Procedure implementations)
├── utils/                    → .utils              (PIDController, PDF, VoltageMonitor, Formulas, …)
├── Tests/                    → .Tests              (⚠ capitalized; PID tuning OpModes)
└── AutoTunner/               → .AutoTunner         (⚠ misspelled "Tunner", capitalized)
    ├── Code/                 → .AutoTunner.Code
    └── Instructions/*.md
```

* **Folder ⇄ package mapping is 1:1, including case.** New files must match their
  folder's existing case exactly (`Kickoff`, `Tests`, `AutoTunner` are capitalized;
  `utils`, `pedro`, `auton`, `teleop`, `subsystems` are lowercase).
* Class naming in this repo is deliberately mixed: `SCREAMING_CASE` (`TELESLOP`,
  `LAST_RESORT`), lowercase (`sloptest`), CamelCase (`ExampleTeleOp`, `ShooterTest`).
  Match the folder you are adding to; when in doubt use CamelCase for new classes.
* Names suffixed `Test` are **OpModes**, not JUnit. There is no unit-test framework here.

---

## 2. OpMode structure

**House rule: every team OpMode is an iterative `OpMode`.** `LinearOpMode` appears only
inside Pedro library classes (`TuningOpMode`). Never write a team `LinearOpMode`,
`waitForStart()`, `opModeIsActive()`, `sleep()`, `idle()`, or `Thread.sleep()`.

### 2.1 Annotations

```java
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

@TeleOp(name = "My TeleOp", group = "test")   // name/group optional; group used with @TeleOp only
public class MyTeleOp extends OpMode { ... }

@Autonomous(name = "My Auto")                 // bare @Autonomous also used
public class MyAuto extends OpMode { ... }
```

Never used in this repo: `@Disabled`, `@Ignore`, `preselectAutoScanTemplate`,
`@Config` (FTC Dashboard), `@DeviceProperties`, `@HardwareType`.

### 2.2 Lifecycle — the only three overrides

```java
@Override public void init()   { /* construct everything; runs while DS sits on INIT */ }
@Override public void start()  { /* runs when PLAY is pressed */ }
@Override public void loop()   { /* runs continuously after start() */ }
```

No `init_loop()` and no `stop()` override exist anywhere in TeamCode.

### 2.3 Canonical TeleOp skeleton

```java
package org.firstinspires.ftc.teamcode.Kickoff.teleop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.teamcode.Kickoff.subsystems.Shooter;
import org.firstinspires.ftc.teamcode.Kickoff.subsystems.DriveTrain;

@TeleOp(name = "My TeleOp")
public class MyTeleOp extends OpMode {
    Shooter shooter = new Shooter();          // field initializers: no-arg constructors
    DriveTrain driveTrain = new DriveTrain();

    @Override
    public void init() {
        shooter.init(hardwareMap);            // hardware acquired in init()
        driveTrain.init(hardwareMap);
    }

    @Override
    public void start() {
        shooter.setVelocity(6000);
    }

    @Override
    public void loop() {
        driveTrain.update(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
        shooter.update();                     // subsystem control loops at the END of loop()
    }
}
```

### 2.4 Canonical Autonomous skeleton (Pedro + Ivy)

```java
package org.firstinspires.ftc.teamcode.Kickoff.auton;

import static com.pedropathing.api.Paths.line;
import static com.pedropathing.ivy.Scheduler.schedule;
import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.commands.Commands.waitMs;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static com.pedropathing.ivy.pedro.PedroCommands.follow;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.follower.Follower;
import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.Scheduler;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import org.firstinspires.ftc.teamcode.Kickoff.subsystems.Shooter;
import org.firstinspires.ftc.teamcode.pedro.Constants;

@Autonomous(name = "My Auto")
public class MyAuto extends OpMode {
    private Follower follower;
    Shooter shooter = new Shooter();

    private final PoseFactory poses = PoseFactory.degrees();   // declare BEFORE any pose field that uses it
    private final Pose start = poses.of(0, 0, 0);              // x INCHES, y INCHES, heading DEGREES

    private Path toScore() {
        return line(start, poses.of(48, 0, 90)).linear(start, poses.of(48, 0, 90));
    }

    @Override
    public void init() {
        Scheduler.reset();                         // ALWAYS first
        follower = Constants.create(hardwareMap);
        follower.setPose(start);
        follower.update();
        shooter.init(hardwareMap);
    }

    @Override
    public void start() {
        // Build the command tree HERE (after init), never in a field initializer — see §7.5
        schedule(sequential(
            instant(() -> shooter.setVelocity(2500)),
            follow(follower, toScore()),
            waitMs(1000),
            instant(() -> shooter.setVelocity(0))
        ));
    }

    @Override
    public void loop() {
        follower.update();                         // ALWAYS first
        Scheduler.execute();                       // ALWAYS second
        shooter.update();
    }
}
```

**Ordering rules for `loop()`:** `follower.update()` → `Scheduler.execute()` →
subsystem `update()` calls. This order is fixed by `ExampleAuto.java:42-45`.

---

## 3. Subsystems & hardware

### 3.1 Subsystem contract

```java
package org.firstinspires.ftc.teamcode.Kickoff.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

public class MySubsystem {
    private DcMotorEx motor;
    private Servo servo;
    public static double kP = 0.01;        // public static => live-tunable via @Configurable

    public void init(HardwareMap hw) {     // called once from OpMode.init()
        motor = hw.get(DcMotorEx.class, "myMotor");
        servo = hw.get(Servo.class, "myServo");
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }

    public void update() { /* called every loop(); closed-loop control here */ }
}
```

* No-arg constructor; instantiated as a field (`MySubsystem x = new MySubsystem();`).
* Subsystems **never** touch `gamepad*` or `telemetry` — the OpMode does that.
* Multiple subsystems calling `VoltageMonitor.getInstance().init(hw)` is fine (idempotent).

### 3.2 Device lookup

```java
DcMotor     m = hardwareMap.get(DcMotor.class,  "frontLeft");   // drivetrain
DcMotorEx   e = hardwareMap.get(DcMotorEx.class, "flywheel");    // needs getVelocity()/getCurrent()
Servo       s = hardwareMap.get(Servo.class,     "stopper");
Limelight3A ll = hardwareMap.get(Limelight3A.class, "limelight");   // §11
```

`hardwareMap.dcMotor.get(name)` and `hardwareMap.getAll(LynxModule.class)` also appear
(only inside tuning procedures). Not used anywhere in TeamCode: `CRServo`, `TouchSensor`,
`ColorSensor`, `DistanceSensor`, direct `hardwareMap.get(IMU.class, …)`.

### 3.3 Enums & motor configuration

```java
motor.setDirection(DcMotorSimple.Direction.REVERSE);   // also DcMotor.Direction (same enum values)
motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);   // or .FLOAT for idle mechanisms
motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
servo.setDirection(Servo.Direction.REVERSE);
servo.setPosition(0.25);                               // 0.0 .. 1.0
```

Encoder reset idiom (from `LinearSlides.init`, `Shooter.init`):

```java
motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
motor.setTargetPosition(0);                 // optional marker
motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
```

**Never used in this repo — do not introduce:** `RUN_TO_POSITION`, `RUN_USING_ENCODER`.
All closed loops are hand-rolled: `setPower()` driven by `getVelocity()` /
`getCurrentPosition()` errors (see §6).

### 3.4 Reading sensors

```java
double ticks   = motor.getCurrentPosition();      // encoder
double ticksS  = motor.getVelocity();             // ticks/second (DcMotorEx only)
double amps    = e.getCurrent(CurrentUnit.AMPS);  // org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit
double voltage = VoltageMonitor.getInstance().getVoltage();   // §6.1 — never call sensors directly
```

---

## 4. Gamepad

**`gamepad1` only.** `gamepad2` and rumble APIs appear zero times in TeamCode.

### 4.1 Edge detection — SDK `*WasPressed()` methods

```java
if (gamepad1.aWasPressed()) { running = !running; }          // toggle

intakeAngle += (gamepad1.dpadUpWasPressed() ? 0.05 : 0)
             - (gamepad1.dpadDownWasPressed() ? 0.05 : 0);   // nudge

if (gamepad1.leftBumperWasPressed())  pidController.kV -= step;
if (gamepad1.rightBumperWasPressed()) pidController.kV += step;
```

Available (verified in use): `aWasPressed, bWasPressed, xWasPressed, yWasPressed,
dpadUpWasPressed, dpadDownWasPressed, dpadLeftWasPressed, dpadRightWasPressed,
leftBumperWasPressed, rightBumperWasPressed, leftTriggerWasPressed,
rightTriggerWasPressed`.

### 4.2 Held buttons, triggers, sticks

```java
if (gamepad1.left_bumper)                        shooter.setStopperNectar();
else if (gamepad1.left_trigger > 0.1)            shooter.setStopperPolen();   // deadband 0.1
else                                             shooter.setStopperClosed();

driveTrain.update(-gamepad1.left_stick_y,             // Y negated (stick up = +y)
                   gamepad1.left_stick_x,
                   gamepad1.right_stick_x);
st.setPower(gamepad1.left_trigger - gamepad1.right_trigger);  // dual-trigger scalar
```

Do **not** hand-roll `wasPressed` state machines — the SDK 12 built-ins are the convention.

---

## 5. Telemetry

### 5.1 Driver Station telemetry

```java
telemetry.addData("pos", motor.getCurrentPosition());
telemetry.addLine("x: " + follower.pose().x());
telemetry.update();      // must be called each loop to flush
telemetry.clear();       // on phase transitions
```

Never used here: `setDisplayFormat`, `MSAG_caption`, `setAutoClear`,
`setCaptioningEnabled`, `setExternalTransmission`.

### 5.2 Elipse panels (the house telemetry UI)

```java
import com.zenith.elipse.ftc.ElipseTelemetry;
import com.zenith.elipse.ftc.TelemetryManager;

TelemetryManager panelsTelemetry;                       // field

// in init():
panelsTelemetry = ElipseTelemetry.INSTANCE.getTelemetry();

// in loop():
panelsTelemetry.debug("speed:" + shooter.flywheel.getVelocity());   // varargs, one line each
panelsTelemetry.addData("key", value);
panelsTelemetry.addLine("text");
panelsTelemetry.update(telemetry);   // mirrors buffer -> DS, then flushes to panels
// panelsTelemetry.update();         // flush only, no DS mirror
```

`TelemetryManager` full API: `addData(String, Object)`, `addLine(String)`,
`debug(Object...)`, `setUpdateInterval(long ms)`, `update()`, `update(Telemetry)`.

### 5.3 `@Configurable` — live tunables (elipse, NOT FTC Dashboard)

```java
import com.zenith.elipse.config.Configurable;      // ⚠ NOT com.acmerobotics.dashboard…

@Configurable                                        // class-level
public class Shooter {
    public static double kP = 0.0005;                // public static non-final fields become sliders
}
```

* Only classes under `org.firstinspires.ftc.teamcode` / `com.zenith` are scanned.
* FTC Dashboard's `@Config`/`FtcDashboard` are **never** imported in this repo — do not add them.

---

## 6. Utilities (verified public APIs)

### 6.1 `utils.VoltageMonitor` (singleton, voltage compensation)

```java
VoltageMonitor.getInstance().init(hardwareMap);   // from subsystem init()
double v    = VoltageMonitor.getInstance().getVoltage();          // cached, refreshes every 400 ms
double comp = VoltageMonitor.getInstance().getCompensation();     // 12 / max(V, 9.0)
```
Knobs: `NOMINAL_VOLTAGE = 12`, `MIN_VOLTAGE_FLOOR = 9.0`, `CACHE_MS = 400`.

### 6.2 `utils.PIDController` (PID + kV/kS feedforward, with auto-tuners)

```java
public static PIDController pid = new PIDController(kP, kI, kD, kV, kS)   // ctor
        .setMaxPower(0.99)                        // fluent: setMinPower, setMaxPower,
        .setKSTuneWaitSeconds(1.2);               // setIntegralZone, setMaxIntegralSum

double out  = pid.calculate(target, error, VoltageMonitor.getInstance().getCompensation());
pid.update(motor, target, error, false, comp);    // writes motor.setPower() (reversed flag applies sign)
pid.reset();
```
Gain fields are public and mutable: `kP, kI, kD, kV, kS, minPower, maxPower`.

Auto-tune trio (used by `Tests/ShooterTest.java`): `autoTuneKS(motor, velocity)`,
`autoTuneKV(motor, velocity)`, `autoTuneKP(motor, targetVelocity, currentVelocity, comp)`,
each paired with `resetXAutoTune()`, `isXAutoTuneComplete()`, `getXTune*()` accessors
and `setXTune*()` config methods. Consult `utils/PIDController.java` before using.

### 6.3 `utils.PDF` (PD + feedforward, no integral)

```java
PDF pdf = new PDF(kP, kD, kS, kV);
pdf.setMinPower(-1).setMaxPower(1);               // fluent
pdf.update(turretMotor, target, error, false, comp);
pdf.calculate(target, error, comp);               // returns power without writing
pdf.reset();
```

### 6.4 Misc SDK helpers

```java
import com.qualcomm.robotcore.util.Range;
import com.qualcomm.robotcore.util.ElapsedTime;

double p = Range.clip(raw, -max, max);            // universal clamp
ElapsedTime timer = new ElapsedTime();            // timer.reset(); timer.seconds(); timer.milliseconds();
```

### 6.5 `utils.Formulas` (ballistics / turret aim — `@Configurable`)

```java
Pose turret = Formulas.getTurretPose(robotPose);
double[] ans = Formulas.solveShootOnTheMove(robotPose, robotVelocity, goalPose);  // {azimuthDeg, flywheelTicks, servoPos}
double servo = Formulas.turretAngleToServoPos(deg);
```
`Pose`/`Vector` here are **Pedro** math types (`com.pedropathing.math`).

---

## 7. Pedro Pathing v3 — follower, poses, paths

### 7.1 Creating the follower — `pedro.Constants`

There is exactly one factory. Never construct `Follower` ad hoc:

```java
follower = Constants.create(hardwareMap);
```

`Constants.java` (do not restructure) builds three lambda-configured objects:

```java
public static MecanumConfig drivetrainConfig = new MecanumConfig(c -> {
    c.frontLeftName.set("frontLeft");         // ConfigVar.set(...) — NOT builder setters
    c.frontRightName.set("frontRight");
    c.backLeftName.set("backLeft");
    c.backRightName.set("backRight");
    c.frontLeftDirection.set(DcMotorSimple.Direction.REVERSE);
    c.frontRightDirection.set(DcMotorSimple.Direction.FORWARD);
    c.backLeftDirection.set(DcMotorSimple.Direction.REVERSE);
    c.backRightDirection.set(DcMotorSimple.Direction.FORWARD);
});                                            // also: c.manualBrakeMode (bool, def true),
                                               //       c.powerThreshold (def 0.01)

public static PinpointConfig localizerConfig = new PinpointConfig(c -> {
    c.name.set("calc");
    c.podType.set(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
    c.xPodOffset.set(25.0);
    c.yPodOffset.set(60.0);
    c.xPodDirection.set(GoBildaPinpointDriver.EncoderDirection.REVERSED);
    c.yPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
    c.globalDistanceUnit.set(DistanceUnit.MM);
    c.offsetUnits.set(DistanceUnit.MM);
});                                            // also: c.encoderResolutionUnit, c.ticksPerUnit,
                                               //       c.resetMode (PinpointLocalizer.ResetMode)

public static ForesightConfig foresightConfig = new ForesightConfig(c -> { …gains… });

public static Follower create(HardwareMap h) {
    return new Follower(
        new PinpointLocalizer(h, localizerConfig),   // (Localizer)
        new Mecanum(h, drivetrainConfig),            // (Drivetrain)
        new Foresight(foresightConfig)               // (Algorithm)
    );
}
```

The `Follower` constructor signature is
`new Follower(Localizer localizer, Drivetrain drivetrain, Algorithm algorithm)`.

`ForesightConfig` gains are produced by the **ForesightTuner** procedure (§10) and
pasted verbatim as `c.<field>.set(...)`. Key fields set in `Constants.java`:

```java
c.forwardTranslational.set(Controller.piecewise(secondary).put(2.5, primary));
c.strafeTranslational.set(Controller.piecewise(secondaryL).put(2.5, primaryL));
c.coast.set(Controller.proportionalFeedforward(0.010978350889324107));
c.brake.set(Controller.proportionalFeedforward(0.008731598255925491));
c.headingFeedback.set(Controller.proportional(5.258721785960744));
c.headingBrakeCoefficients.set(Vector2D.cartesian(a, b));
c.linearBrakeCoefficients.set(Matrix.diag(a, b));
c.quadraticBrakeCoefficients.set(Matrix.diag(a, b));
c.maxAchievableForwardVelocity.set(72.72923108818539);   // in/s
c.maxAchievableStrafeVelocity.set(52.34323936525474);
c.naturalForwardDeceleration.set(85.01144677379789);
c.naturalStrafeDeceleration.set(104.49787535782846);
```

`Controller` factories: `proportional(kP)`, `proportionalFeedforward(kV)`,
`staticFeedforward(kS)`, `integral(kI)`, `sum(...)`, `piecewise(baseline).put(distance, ctrl)`.
All take either a `double` or a `Supplier<Double>` (so live-tunable gains work).

### 7.2 Poses — `PoseFactory`

```java
import com.pedropathing.api.PoseFactory;
import com.pedropathing.math.Pose;

private final PoseFactory poses = PoseFactory.degrees();   // or PoseFactory.radians()
Pose a = poses.of(60, 9, 0);      // x, y, heading  — DEGREES when PoseFactory.degrees()
Pose b = new Pose(10, 0);         // raw ctor — heading defaults to 0, always RADIANS
Pose c = new Pose(10, 0, Math.PI / 2);                       // radians
Pose z = Pose.zero();
```

`Pose` accessors (note: methods, not fields): `x()`, `y()`, `heading()` (radians),
`withX(v)`, `withY(v)`, `withHeading(v)`, `distance(other)`, `plus`, `minus`,
`times(scalar)`, `toVector2D()`.

`PoseFactory` mirroring for red/blue alliance variants:

```java
PoseFactory mirrored = PoseFactory.degrees().mirrorX(72);   // also mirrorY, mapX, mapY, mapHeading, map
Pose p = mirrored.of(12, 24, 90);
```

⚠ **Field-unit gotcha:** this robot's `PinpointConfig` sets
`globalDistanceUnit = DistanceUnit.MM`, but `PoseFactory.degrees().of(x, y, h)` values
are *unitless* numbers passed to Pedro's path math — autos in this repo treat them as
**inches** (matching field coordinates). Keep new poses consistent with existing autos.

### 7.3 Building paths — `com.pedropathing.api.Paths` + `Path` modifiers

```java
import static com.pedropathing.api.Paths.line;    // static imports are the house style
import static com.pedropathing.api.Paths.curve;
import static com.pedropathing.api.Paths.through;
import com.pedropathing.paths.Path;
```

Geometry factories:

| Call | Meaning |
|---|---|
| `line(Pose start, Pose end)` | straight segment between two poses |
| `line(Vector2D start, Vector2D end)` | straight segment, no heading info |
| `curve(Pose... poses)` | bezier through control poses (≥2) — `curve(start, ctrl, end)` = quadratic |
| `curve(Vector2D... points)` | bezier, control points only |
| `through(Pose... poses)` | smooth path through all poses |
| `path(Path... paths)` / `path(Curve)` | composition / raw curve |

Heading modifiers (fluent, every one returns `Path` — chain onto the geometry):

| Modifier | Meaning |
|---|---|
| `.linear(start, end)` | interpolate heading start→end over the path (pose overload reads `.heading()`) |
| `.linear(a, b, endT)` / `.linear(a, b)` numeric overloads | numeric heading interpolation |
| `.constant(headingRad)` / `.constant(pose)` | constant heading |
| `.tangent()` | face path tangent |
| `.reverseTangent()` | face opposite of tangent |
| `.facingPoint(point)` / `.facingPoint(pose)` | always face a fixed field point |
| `.heading(interpolator)` | any custom `Interpolator` |
| `.with(Modifier...)` | attach raw modifiers |

Examples (mirroring `ExampleAuto` and `Tests`):

```java
import com.pedropathing.paths.interpolator.Interpolator;

Path straight  = line(start, end).linear(start, end);          // heading blends start→end
Path flat      = line(start, end).constant(0);                 // heading locked to 0 rad
Path arc       = curve(shoot, ctrl, park).linear(shoot, park); // bezier + heading blend
Path tangent   = curve(a, b, c).tangent();
Path piecewise = curve(a, b, c).heading(
        Interpolator.piecewise()
            .until(0.5, Interpolator.tangent)
            .until(1.0, Interpolator.constant(0)));
Path lambda    = curve(a, b, c).heading((curve, t) -> Math.PI); // Interpolator is @FunctionalInterface
```

`Interpolator` statics: `constant(double)`, `constant(Pose)`, `linear(double, double)`,
`linear(Pose, Pose)`, `longLinear(...)` (same overloads), `facingPoint(Vector2D)`,
`facingPoint(Pose)`, `piecewise()` → `.until(t, interp)`, and the field
`Interpolator.tangent`. Functional signature: `double interpolate(Curve curve, double t)`.

> **Clarification:** `.linear(a, b)` only sets *heading* interpolation. Position comes
> from `line(...)`/`curve(...)`. That is why every path in this repo is
> `line(a, b).linear(a, b)` — it looks redundant, it is not.

### 7.4 Following — `Follower` API

Raw follower methods (verified signatures):

```java
follower.setPose(pose);                  // reset odometry before a routine
follower.update();                       // call EVERY loop() iteration, first statement
follower.update(deltaSeconds);           // optional explicit dt
follower.follow(path);                   // start following (non-blocking)
follower.hold(pose);                     // hold a pose (also hold(pose, useScaling))
follower.manual(forward, lateral, heading);   // teleop field-centric drive (joysticks)
follower.manual(drivePowers);            // DrivePowers overload
follower.stop();
Pose cur = follower.pose();              // .x() .y() .heading()
boolean end = follower.atParametricEnd(); // path fully consumed — the "done" check
boolean busy = follower.isBusy();        // delegates to algorithm.isBusy() (Foresight), NOT mode checks
follower.holdEnd.set(true);              // ConfigVar: brake at path end
```

State queries: `following()`, `holding()`, `manual()`, `idle()`, `mode()`,
`completion()`, `parametricCompletion()`, `distanceToEndpoint()`, `remainingDistance()`,
`currentPath()`, `velocity()`, `twist()`, `closestPose()`, `closestTangent()`.

**Manual driving (teleop) pattern** — `Kickoff/ExampleTeleOp.java`:

```java
@Override public void init()   { follower = Constants.create(hardwareMap); }
@Override public void loop()   {
    follower.manual(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
    follower.update();
}
```

**Raw re-trigger pattern** (only seen inside tuning procedures):

```java
follower.follow(path1);
while (opModeIsActive()) {                 // TuningOpMode context only — never in a team OpMode
    follower.update();
    if (follower.atParametricEnd()) follower.follow(path2);
}
```

### 7.5 ⚠ CRITICAL PITFALL — building commands before `follower` exists

`PedroCommands.follow(follower, path)` captures its `follower` argument **by value**
when called. It is implemented as:

```java
new CommandBuilder()
    .setStart(() -> follower.follow(path))
    .setDone(follower::atParametricEnd);   // bound method ref — NPE if follower == null
```

A bound method reference on a `null` receiver throws **at construction time**. Therefore
writing `follow(follower, …)` in a field initializer **crashes the OpMode on
instantiation** — verified by executing the real `PedroCommands.follow(null, path)`
against the real Ivy 1.1.0 jar:

```
java.lang.NullPointerException
    at com.pedropathing.ivy.pedro.PedroCommands.follow(PedroCommands.java:23)
```

**Never** write `follow(follower, …)` in a field initializer (the field is still `null`
there — `init()` has not run yet). Use one of these two correct forms:

```java
// ✅ OPTION A (recommended): build the tree in start() — init() has run by then
Command autonRoutine;
@Override public void start() {
    autonRoutine = sequential(
        instant(() -> shooter.setVelocity(2500)),
        follow(follower, shoot()),
        follow(follower, park())
    );
    schedule(autonRoutine);
}

// ✅ OPTION B: keep a field, but defer creation with lazy(...) — supplier runs at start
Command autonRoutine = lazy(() -> sequential(
        instant(() -> shooter.setVelocity(2500)),
        follow(follower, shoot())
));
@Override public void start() { schedule(autonRoutine); }
```

Both options were compiled and instantiated against Pedro 3.0.0 + Ivy 1.1.0 to confirm
no NPE.

**The precise capture rule (all four cases verified by executing the real library):**

| Expression written while `follower` is still `null` | Result |
|---|---|
| `follow(follower, path)` | **NPE at construction** (bound ref `follower::atParametricEnd`) |
| `hold(follower)` | **NPE at construction** (calls `follower.pose()` immediately) |
| `hold(follower, pose)` | constructs OK, then **NPE when the command starts** (parameter value captured) |
| `instant(() -> follower.update())` — a lambda that reads the *field* | **safe**: the lambda reads `this.follower` at execution time, after `init()` |

Rule of thumb: **passing** `follower` into a method binds its current value (null) —
unsafe in a field initializer. **Referencing** `follower` inside your own lambda body
reads the field later — safe. When unsure, build the tree in `start()` (Option A).

---

## 8. Ivy command framework (the "Action" system)

Import set used by existing autos:

```java
import com.pedropathing.ivy.Command;                        // interface (has .schedule()/.cancel())
import com.pedropathing.ivy.Scheduler;
import static com.pedropathing.ivy.Scheduler.schedule;
import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.commands.Commands.waitMs;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static com.pedropathing.ivy.pedro.PedroCommands.follow;
```

### 8.1 Lifecycle contract

```java
init()  -> Scheduler.reset();  follower = …;  follower.setPose(…);  follower.update();
start() -> schedule(commandTree);                  // build tree here (see §7.5)
loop()  -> follower.update();  Scheduler.execute();  <subsystem updates>
```

### 8.2 `Scheduler` (all static)

`schedule(Command)`, `schedule(Command...)`, `execute()`, `reset()`,
`isRunning(Command)`, `isScheduled(Command)`, `cancel(Command)`.
Command also has instance defaults: `cmd.schedule()`, `cmd.cancel()`.

### 8.3 `Commands` (leaf commands → all return `CommandBuilder`)

| Call | Semantics |
|---|---|
| `instant(Runnable)` | one-shot action |
| `waitMs(double millis)` | wall-clock wait (`System.currentTimeMillis`) |
| `waitUntil(BooleanSupplier)` | wait for condition |
| `infinite(Runnable)` | never-done command |
| `conditional(BooleanSupplier, Command, Command)` | if/else |
| `branch(LinkedHashMap<BooleanSupplier, Command>)` | first-match branch |
| `lazy(Supplier<Command>)` | **defer tree creation until start** (§7.5) |
| `match(Supplier<T>, EnumMap<T, Command>)` | enum state select |
| `onInterrupt(Runnable)` | callback when interrupted |

### 8.4 `Groups` (composition → return `CommandBuilder`)

| Call | Semantics |
|---|---|
| `sequential(Command...)` | run in order (the only one used by current autos) |
| `parallel(Command...)` | run simultaneously until all done |
| `race(Command...)` | first to finish wins, others cancelled |
| `deadline(Command, Command...)` | cancel others when the deadline command finishes |
| `repeat(Command, int)` / `repeat(Command, IntSupplier)` | fixed/deferred iterations |
| `loop(Command)` | repeat forever |

### 8.5 `PedroCommands` (bridge to the follower)

| Call | Semantics |
|---|---|
| `follow(Follower, Path)` | `start → follower.follow(path)`, `done → follower.atParametricEnd()` — **NPEs if `follower` is null when called** (§7.5) |
| `hold(Follower)` | evaluates `follower.pose()` **immediately** — NPEs if `follower` is null when called |
| `hold(Follower, Pose)` | wraps `instant(() -> follower.hold(pose))` — builds fine even with null, but **NPEs when the command runs** if the passed value was null |

### 8.6 Fluent chaining on any `Command` / `CommandBuilder`

`then(...)`, `with(...)` (parallel), `raceWith(...)`, `until(BooleanSupplier)`,
`unless(BooleanSupplier)`, `proxy()`; plus builder setters `setStart(Runnable)`,
`setExecute(Runnable)`, `setDone(BooleanSupplier)`, `setEnd(Consumer<EndCondition>)`,
`requiring(Object...)`, `setPriority(int)`, `setInterruptedBehavior(...)`,
`setBlockedBehavior(...)`, `setConflictBehavior(...)`.

### 8.7 Full example (the house pattern, corrected)

```java
Command shootRoutine = sequential(
    waitMs(1000),
    instant(() -> {
        intake.runForward();
        shooter.setStopperPolen();
        glisiere.setTargetTicks(500);
    }),
    waitMs(2000),
    instant(() -> {
        intake.stop();
        shooter.setStopperClosed();
        shooter.setVelocity(0);
    })
);

@Override public void start() {
    schedule(sequential(                 // follow() only appears here, after init()
        instant(() -> shooter.setVelocity(2500)),
        follow(follower, shootPath()),
        shootRoutine,
        follow(follower, parkPath())
    ));
}
```

Timing uses `waitMs(...)` or `ElapsedTime` — never `sleep()`.

---

## 9. Localization

Production robot: **GoBilda Pinpoint** via `Constants.localizerConfig` (§7.1).
All other localizers exist as optional tuning procedures under `pedro/procedures/`.

| Localizer | Config | Constructor |
|---|---|---|
| `PinpointLocalizer` | `PinpointConfig` | `new PinpointLocalizer(hardwareMap, config)` |
| `ThreeWheelLocalizer` | `ThreeWheelConfig` | `new ThreeWheelLocalizer(hardwareMap, config)` |
| `ThreeWheelIMULocalizer` | `ThreeWheelIMUConfig` | same shape; `ThreeWheelIMULocalizer.useIMU` static flag |
| `TwoWheelLocalizer` | `TwoWheelConfig` | same shape (`c.imu.set(new RevHubIMU(new RevHubOrientationOnRobot(logo, usb)))`) |
| `OTOSLocalizer` | `OTOSConfig` | `c.offset.set(new Pose(x, y))`, `c.linearUnit.set(DistanceUnit.INCH)` |
| `OctoQuadLocalizer` | `OctoQuadConfig` | uses `com.qualcomm.hardware.digitalchickenlabs.OctoQuad` |
| `RevHubIMU` | — | embedded inside TwoWheel/ThreeWheelIMU configs |

Localizer interface: `setPose(Pose)`, `update()`, `pose()`, `twist()`, `velocity()`,
`reset()` (+ `setX/setY/setHeading` defaults).

Encoder direction constants in configs: `Encoder.FORWARD` / `Encoder.REVERSE`.

Bare `GoBildaPinpointDriver` is also used directly by `utils/DcMotorAutoAlignSystem`
and `utils/ServoAutoAlignSystem`:

```java
localizer.setOffsets(x, y, DistanceUnit.MM);
localizer.setEncoderResolution(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
localizer.recalibrateIMU();
localizer.setPosition(startingPose);
localizer.getHeading(AngleUnit.DEGREES);
localizer.update();
```

---

## 10. Tuning procedures (Pedro autotune)

Registration lives in `pedro/Tuning.java`. Contract: `@Tuner` methods must be
**`public static`, zero-argument, return `Procedure`** (verified against the scanner):

```java
public class Tuning {
    @Tuner
    public static Procedure mecanumTuner() { return new MecanumTuner(); }

    @Tuner
    public static Procedure foresightTuner() {
        return new ForesightTuner(
            (hardwareMap) -> new PinpointLocalizer(hardwareMap, Constants.localizerConfig),
            (hardwareMap) -> new Mecanum(hardwareMap, Constants.drivetrainConfig));
    }
}
```

Procedure authoring API (`com.pedropathing.tuning.autotune.Procedure`):

```java
package org.firstinspires.ftc.teamcode.pedro.procedures;   // lives under .pedro.procedures

import com.pedropathing.tuning.autotune.Display;
import com.pedropathing.tuning.autotune.Display.FourWheelBot.Wheel;
import com.pedropathing.tuning.autotune.Inputs;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.TuningOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;

public class MyTuner extends Procedure {
    public MyTuner() { super("Name", "Description"); }

    @Override public void run() throws InterruptedException {
        Inputs inputs = inputs("Title", "Prompt");
        // ⚠ do NOT name a local "name" — TuningOpMode declares `public final String name`,
        //   which shadows it inside any TuningOpMode subclass you pass to runOpMode().
        Inputs.Field<String> motorName = inputs.s("Motor Name").withDefault("frontLeft");
        Inputs.Field<Integer> n   = inputs.i("Count").withDefault(3).min(1).max(10);
        Inputs.Field<Boolean> b   = inputs.b("Continue?").withDefault(true);
        awaitInputs(inputs);

        confirmation("Title", "Message");                 // yes/no gate
        withDisplay(Display.fourWheelBot(Wheel.FRONT_LEFT, false), () -> {
            runOpMode(new TuningOpMode<Void>("Spin", "Spins one motor", true) {
                @Override protected Void runTuningOpMode() throws InterruptedException {
                    DcMotor m = hardwareMap.dcMotor.get(motorName.get());
                    waitForStart();
                    while (opModeIsActive()) { /* … */ }
                    return null;
                }
            });
        });

        result("frontLeftName", "frontLeft");             // emit result
        code(Language.JAVA, "public static MecanumConfig …"); // emit paste-able config snippet
        // abort("message"); on failure
    }
}
```

Registered tuners today: `mecanumTuner`, `pinpointTuner`, `foresightTuner`, `tests`.
Unregistered (implemented but not in `Tuning.java`): `ThreeWheelTuner`, `TwoWheelTuner`,
`ThreeWheelIMUTuner`, `OTOSTuner`, `OctoQuadTuner`.

`TuningOpMode<Result>` is a library `LinearOpMode` subclass: implement
`runTuningOpMode()`; `waitForStart()` / `opModeIsActive()` / `isStopRequested()` are
allowed **only** inside these classes.

Enum pickers with `@DisplayName` on constants + `inputs.e("Label", MyEnum.class).withDefault(...)`
are the standard UI (see `MecanumTuner`, `Tests`).

---

## 11. Vision — Limelight 3A

SDK driver only. **No `VisionPortal`, no AprilTag processor, no EasyOpenCV/OpenCV**
anywhere in TeamCode.

```java
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;

limelight = hwMap.get(Limelight3A.class, "limelight");
limelight.pipelineSwitch(0);
limelight.start();

LLResult result = limelight.getLatestResult();
if (result == null || !result.isValid()) return;
List<LLResultTypes.FiducialResult> tags = result.getFiducialResults();
if (tags == null || tags.isEmpty()) return;
int id = tags.get(0).getFiducialId();

limelight.stop();
```

---

## 12. Quick reference — DO / DON'T

**DO (matches existing style):**

1. Extend iterative `OpMode`; annotate `@TeleOp(name=, group=)` / `@Autonomous(name=)`.
2. Subsystems: `new X()` as a field → `x.init(hardwareMap)` in `init()` → `x.update()` in `loop()`.
3. Look up hardware with `hw.get(DcMotorEx.class, "name")` / `hw.get(Servo.class, "name")`.
4. Reset encoders with `STOP_AND_RESET_ENCODER` then `RUN_WITHOUT_ENCODER`.
5. Clamp with `Range.clip`, time with `ElapsedTime`, compensate with
   `VoltageMonitor.getInstance().getCompensation()`.
6. Hand-roll closed loops in `subsystem.update()` using `getVelocity()`/`getCurrentPosition()`.
7. Edge-detect with `gamepad1.xWasPressed()`; drive sticks as `-gamepad1.left_stick_y`.
8. Telemetry: `telemetry.addData/update` for DS, `ElipseTelemetry.INSTANCE.getTelemetry()`
   → `.debug(...)` → `.update(telemetry)` for panels.
9. Live tunables: `public static` fields + class-level `@Configurable`
   (`com.zenith.elipse.config.Configurable`).
10. Autos: `Scheduler.reset()` first in `init()`; `Constants.create(hardwareMap)`;
    `setPose` → `update`; build the command tree in `start()`; loop order
    `follower.update(); Scheduler.execute(); subsystem.update();`.
11. Poses: `PoseFactory.degrees().of(x, y, headingDeg)`; paths:
    `line(a, b).linear(a, b)` / `curve(a, ctrl, b).linear(a, b)`.
12. Java 8 only, FTC SDK 12.0.0 APIs, Pedro 3.x packages (`com.pedropathing.api`,
    `com.pedropathing.ivy.*`).

**DON'T (verified absent from this codebase — introducing these breaks convention):**

* ❌ Pedro v1/v2 APIs: `PathBuilder`, `LineTo`/`SplineTo`, `followPath`, `PathConstraints`,
  `ParameterList`, `MecanumDriveConstants`, `DriveConstants`, `Trajectory`, `waitUntilComplete`,
  `turnSync`, Road-Runner `Action`/`SequentialAction`/`ParallelAction`.
* ❌ `LinearOpMode` + `waitForStart()`/`opModeIsActive()`/`sleep()`/`idle()`/`Thread.sleep`
  in team OpModes (allowed only inside `TuningOpMode`).
* ❌ `RUN_TO_POSITION`, `RUN_USING_ENCODER`.
* ❌ `gamepad2`, gamepad rumble.
* ❌ `telemetry.setDisplayFormat(...)`.
* ❌ Any `com.acmerobotics.*` / `FtcDashboard` / `@Config` imports (dependency exists, unused).
* ❌ `VisionPortal`, AprilTag/OpenCV/EasyVision — Limelight is `Limelight3A` only.
* ❌ `TouchSensor`, `ColorSensor`, `DistanceSensor`, `CRServo`, raw `IMU.class` lookups.
* ❌ Threads, `ExecutorService`, `Runnable` workers, callbacks on background threads.
* ❌ `@Disabled`, `@Ignore`, `preselectAutoScanTemplate`.
* ❌ **Passing `follower` into `follow(...)`/`hold(...)` from a field initializer** —
  NPE at construction or at command start (§7.5 capture rule).
* ❌ Calling `Scheduler.execute()` before `follower.update()`, or scheduling in `init()`
  before `Scheduler.reset()`.

---

## 13. Copy-paste templates

### 13.1 Minimal TeleOp

```java
package org.firstinspires.ftc.teamcode.Kickoff.teleop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.teamcode.Kickoff.subsystems.DriveTrain;

@TeleOp(name = "Minimal TeleOp")
public class MinimalTeleOp extends OpMode {
    DriveTrain driveTrain = new DriveTrain();

    @Override
    public void init() {
        driveTrain.init(hardwareMap);
    }

    @Override
    public void loop() {
        driveTrain.update(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
    }
}
```

### 13.2 Minimal Pedro autonomous (correct command-timing)

```java
package org.firstinspires.ftc.teamcode.Kickoff.auton;

import static com.pedropathing.api.Paths.curve;
import static com.pedropathing.api.Paths.line;
import static com.pedropathing.ivy.Scheduler.schedule;
import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.commands.Commands.waitMs;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static com.pedropathing.ivy.pedro.PedroCommands.follow;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.follower.Follower;
import com.pedropathing.ivy.Scheduler;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import org.firstinspires.ftc.teamcode.pedro.Constants;

@Autonomous(name = "Minimal Auto")
public class MinimalAuto extends OpMode {
    private Follower follower;

    private final PoseFactory poses = PoseFactory.degrees();
    private final Pose start = poses.of(0, 0, 0);
    private final Pose mid   = poses.of(48, 24, 0);
    private final Pose end   = poses.of(48, 48, 90);

    private Path toMid()  { return line(start, mid).linear(start, mid); }
    private Path toEnd()  { return curve(mid, poses.of(60, 36, 0), end).linear(mid, end); }

    @Override
    public void init() {
        Scheduler.reset();
        follower = Constants.create(hardwareMap);
        follower.setPose(start);
        follower.update();
    }

    @Override
    public void start() {
        schedule(sequential(
            follow(follower, toMid()),
            waitMs(500),
            instant(() -> telemetry.addLine("mid reached")),
            follow(follower, toEnd())
        ));
    }

    @Override
    public void loop() {
        follower.update();
        Scheduler.execute();
    }
}
```

### 13.3 Minimal subsystem with voltage-compensated closed loop

```java
package org.firstinspires.ftc.teamcode.Kickoff.subsystems;

import com.zenith.elipse.config.Configurable;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.Range;
import org.firstinspires.ftc.teamcode.utils.VoltageMonitor;

@Configurable
public class Flywheel {
    public DcMotorEx motor;
    public static double kP = 0.0005;
    public static double kS = 0.03;
    public static double kV = 0.0004;
    public static double maxPower = 0.9;
    private double targetVelocity = 0;

    public void init(HardwareMap hw) {
        VoltageMonitor.getInstance().init(hw);
        motor = hw.get(DcMotorEx.class, "flywheel");
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }

    public void setVelocity(double ticksPerSecond) { targetVelocity = ticksPerSecond; }

    public void update() {
        if (targetVelocity == 0) { motor.setPower(0); return; }
        double comp = VoltageMonitor.getInstance().getCompensation();
        double error = targetVelocity - motor.getVelocity();
        if (Math.abs(error) > 40) {
            motor.setPower(Range.clip(
                (kS + kV * targetVelocity + kP * error) * comp,
                -maxPower, maxPower));
        }
    }
}
```

### 13.4 Subsystem + Pedro auto combined skeleton

```java
package org.firstinspires.ftc.teamcode.Kickoff.auton;

import static com.pedropathing.api.Paths.curve;
import static com.pedropathing.api.Paths.line;
import static com.pedropathing.ivy.Scheduler.schedule;
import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.commands.Commands.waitMs;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static com.pedropathing.ivy.pedro.PedroCommands.follow;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.follower.Follower;
import com.pedropathing.ivy.Scheduler;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import org.firstinspires.ftc.teamcode.Kickoff.subsystems.Shooter;
import org.firstinspires.ftc.teamcode.pedro.Constants;

@Autonomous(name = "Shoot And Park")
public class ShootAndPark extends OpMode {
    private Follower follower;
    Shooter shooter = new Shooter();

    private final PoseFactory poses = PoseFactory.degrees();
    private final Pose shootStart = poses.of(60, 9, 0);
    private final Pose shoot      = poses.of(60, 24, 0);
    private final Pose ctrl       = poses.of(17, 31, 0);
    private final Pose park       = poses.of(12, 96, 0);

    private Path shootPath() { return line(shootStart, shoot).linear(shootStart, shoot); }
    private Path parkPath()  { return curve(shoot, ctrl, park).linear(shoot, park); }

    @Override
    public void init() {
        Scheduler.reset();
        follower = Constants.create(hardwareMap);
        follower.setPose(shootStart);
        follower.update();
        shooter.init(hardwareMap);
    }

    @Override
    public void start() {
        schedule(sequential(
            instant(() -> shooter.setVelocity(2500)),
            follow(follower, shootPath()),
            waitMs(1500),
            instant(() -> shooter.setVelocity(0)),
            follow(follower, parkPath())
        ));
    }

    @Override
    public void loop() {
        follower.update();
        Scheduler.execute();
        shooter.update();
    }
}
```

---

## 14. Verification notes (how this file was validated)

* Pedro signatures were read from the resolved dependency sources in
  `~/.gradle/caches/modules-2/files-2.1/com.pedropathing*/**` (core 3.0.0, revhub 3.0.0,
  ivy core 1.1.0, ivy pedro 1.1.0, tuning 1.0.0) — the same artifacts Gradle builds against.
* FTC SDK signatures come from `RobotCore/FtcCommon/Hardware` 12.0.0 AARs and the existing
  TeamCode sources.
* The §7.5 NPE was reproduced against the real library jars by calling
  `PedroCommands.follow(null, path)` directly and by compiling/instantiating a minimal
  auto that used the bad field-initializer pattern; the fix patterns in §7.5 were
  compiled and instantiated against the same jars successfully. The four-case capture
  table in §7.5 was verified by executing `PedroCommands.follow/hold` and lambda
  variants with a null follower.
* Every **complete-class template** in this file (§2.3, §2.4, §3.1, §10, §13.1–13.4) was
  extracted and compiled against RobotCore/FtcCommon/Hardware 12.0.0 + Pedro 3.0.0 +
  revhub 3.0.0 + Ivy 1.1.0 + tuning 1.0.0 + elipse 0.1.0 — all 8 compile cleanly.
* An API smoke test calling **every** signature in the §7.2–§7.4, §8, §9, §11 reference
  tables was compiled against the same jars (it caught and corrected the `mirrorX`
  return type and the `TuningOpMode.name` shadowing issues).
* Elipse `TelemetryManager` API read from `elipse-ftc-0.1.0` sources.
