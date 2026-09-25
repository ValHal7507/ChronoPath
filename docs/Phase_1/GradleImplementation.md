CONTEXT
ChronoPath — FTC Team Zenith 19084's robot replay/visualization system.
Phase 1 Tasks 3–7 (ReplayFormatConstants.java, ReplaySample.java,
ReplayRecorder.java, plus Task 6's test) are complete and byte-verified,
but a read-only investigation just confirmed robot-module/ is not wired
into this repo's Gradle build: it's missing from settings.gradle, has no
build.gradle, and TeamCode has no dependency on it. This is Task 7.5 —
an inserted infrastructure task, not in Plan.md's original numbering,
needed before Task 8 (saveToFile) and Task 9 (OpMode integration) can
work for real. Nothing has been modified yet; this task applies the fix
that was previously only described.

GOAL
Wire robot-module into the Gradle build so its classes are on TeamCode's
compile classpath, and confirm the module actually builds — including
running Task 6's existing test under Gradle, not just standalone javac.

EXISTING ARCHITECTURE (from the prior read-only investigation)

- settings.gradle currently has exactly: include ':FtcRobotController'
  and include ':TeamCode'.
- TeamCode/build.gradle applies ../build.common.gradle and
  ../build.dependencies.gradle, with implementation
  project(':FtcRobotController') as its only project dependency.
- FtcRobotController/build.gradle is the repo's existing library-module
  template: apply plugin: 'com.android.library', its own android {}
  block (compileSdk 36, Java 8), apply from: '../build.dependencies.gradle'.
- build.dependencies.gradle declares RobotCore:12.0.0 and Pedro
  (revhub:3.0.0 → resolves to core:3.0.0), plus an AAR
  (androidx.appcompat), which is why the new module must be
  com.android.library, not plain java-library.
- Root build.gradle is AGP 8.7.0 — every module's android block needs an
  explicit namespace.
- robot-module/src/main/java/.../replay/ already contains
  ReplayFormatConstants.java, ReplaySample.java, ReplayRecorder.java;
  robot-module/src/test/java/.../replay/ContainsReplaySampleToBytesTest.java.
  Neither has a build.gradle or AndroidManifest.xml yet.

FILES YOU MAY MODIFY

- settings.gradle (add one include line)
- Create robot-module/build.gradle
- Create robot-module/src/main/AndroidManifest.xml (if AGP 8.7 requires
  one for a library module with no manifest — verify this rather than
  assuming; if AGP 8.7 builds fine without one, state that and skip
  creating it)
- TeamCode/build.gradle (add one project dependency line)

FILES YOU MUST NOT MODIFY

- docs/Plan.md
- docs/AGENTS.md
- docs/FTC_SYNTAX
- docs/replay-format-spec.md
- docs/directory-structure.md
- ReplayFormatConstants.java, ReplaySample.java, ReplayRecorder.java,
  ReplaySampleToBytesTest.java (no source changes — this task is build
  wiring only)
- FtcRobotController/build.gradle, build.common.gradle,
  build.dependencies.gradle (reference them, don't alter them)
- any other pre-existing file

TECHNICAL REQUIREMENTS

1. Add `include ':robot-module'` to settings.gradle.
2. Create robot-module/build.gradle following the investigation's
   proposed pattern: com.android.library plugin, namespace matching the
   package (org.firstinspires.ftc.teamcode.replay), compileSdk matching
   TeamCode's actual value (re-verify this number yourself from
   build.common.gradle rather than trusting the prior report's "34" —
   confirm or correct it), minSdkVersion matching TeamCode's actual
   value (re-verify from build.common.gradle or TeamCode/build.gradle —
   the prior report flagged this as unverified; confirm the real number
   before using it, don't guess or copy blindly), Java 8
   source/targetCompatibility, `apply from: '../build.dependencies.gradle'`,
   and a testImplementation 'junit:junit:4.13.2' dependency so
   :robot-module:testDebugUnitTest can run.
3. Add `implementation project(':robot-module')` to TeamCode/build.gradle's
   dependencies block, alongside the existing FtcRobotController one.
4. Determine whether a minimal AndroidManifest.xml is required under AGP
   8.7 for a manifest-less library module — check FtcRobotController's
   manifest for reference, and either create the minimal equivalent or
   report that none was needed, based on the actual build result (see
   Tests below), not assumption.

CONSTRAINTS

- No changes to any recorder/format source file — this task only touches
  build configuration.
- Don't change compileSdk/minSdk/Java version numbers to anything other
  than what TeamCode already actually uses — the goal is consistency
  with the existing app, not a new standard.
- Don't add any dependency beyond what build.dependencies.gradle already
  provides plus the one JUnit test dependency — no extra libraries.

ACCEPTANCE CRITERIA

- ./gradlew :robot-module:assembleDebug succeeds.
- ./gradlew :robot-module:testDebugUnitTest succeeds and reports
  ReplaySampleToBytesTest's 2 tests passing (same results Task 6 got
  standalone, now under the real build).
- ./gradlew :TeamCode:assembleDebug (or compileDebugJavaWithJavac)
  succeeds, confirming TeamCode can now see robot-module's classes —
  this can be checked by confirming compilation succeeds; you do not
  need to write any OpMode code yet (that's still Task 9).

TESTS YOU MUST PERFORM

- Actually run the three Gradle commands above (not just describe them)
  and report their real output/exit status.
- If any build error occurs (missing manifest, namespace conflict,
  version mismatch), fix it within the constraints above and re-run
  until green, documenting what was wrong and what fixed it.

REQUIRED FINAL REPORT

- Full diff/content of settings.gradle's change, the new
  robot-module/build.gradle, any AndroidManifest.xml created (or a
  statement that none was needed, with why), and TeamCode/build.gradle's
  change.
- The actual compileSdk/minSdk values used, confirmed against
  TeamCode's real configuration (not copied from the prior report
  without re-checking).
- Real output confirming all three Gradle commands succeeded, including
  the test task's result line for ReplaySampleToBytesTest.
- Checked against FTC_SYNTAX.md: N/A (build config only, no SDK code
  written) — state this explicitly since AGENTS.md wants the line
  present or explicitly marked N/A every task.
