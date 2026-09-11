# Dynamic Atmosphere

A NeoForge mod for Minecraft 1.21.1. Right now it is a shell: it loads,
proves that a Minecraft-free module's code is packaged and reachable inside
the mod jar, and logs one line at startup. No weather/atmosphere simulation
ships yet — that is future work (tracked under SICKOS-26 and later epics),
and this repository exists specifically to give that work a build and a
test harness that keeps Minecraft and NeoForge off its classpath.

- Mod id: `dynamicatmosphere`
- Licence: MIT (see `LICENSE`)
- Target: Minecraft 1.21.1, NeoForge 21.1.248, Java 21

## Layout

Two Gradle subprojects, split on purpose:

- **`engine/`** — plain `java-library`, Java 21. No NeoForge or
  ModDevGradle plugin, and no dependency here may ever resolve to a
  `net.minecraft` or `net.neoforged` artifact (see "Minecraft-free
  enforcement" below). This is where all future simulation code
  (SICKOS-26 and later) goes. Its root package is
  `io.github.brooswitminecraft.dynamicatmosphere.engine`; sub-packages
  under that are left for the code that lands there to choose, but keep
  them under `engine`'s own root so the split stays visible from the
  package name alone.
- **`forge/`** — applies NeoForge's ModDevGradle plugin
  (`net.neoforged.moddev`) and holds everything Minecraft-facing: the
  `@Mod` class, `META-INF/neoforge.mods.toml`, `pack.mcmeta`. Root package
  `io.github.brooswitminecraft.dynamicatmosphere` (note: no `.engine`
  suffix — that suffix marks the Minecraft-free side specifically).

Maven group for both: `io.github.brooswitminecraft`.

`forge` depends on `engine` (`implementation project(":engine")`) **and**
its `jar` task is overridden to also `from(project(":engine").sourceSets
.main.output)`, copying `engine`'s compiled classes directly into the mod
jar's root. A plain project dependency alone would put `engine` on
`forge`'s own classpath but would NOT put its classes inside the jar
NeoForge loads — that gap is exactly how this kind of split usually ends
in a `NoClassDefFoundError` at game start. (NeoForge's `jarJar` mechanism
is the other supported way to do this; it was skipped here because it
exists to embed *third-party* dependencies with their own version
tracking, which `engine` — our own first-party code with no independent
release of its own — isn't.)

## Gradle tooling: ModDevGradle, not NeoGradle

`forge/build.gradle` applies `net.neoforged.moddev` (ModDevGradle), NeoForge's
current officially-maintained Gradle plugin, rather than NeoGradle
(`net.neoforged.gradle.userdev`). NeoGradle is the older toolchain; NeoForge's
own MDK template repository has moved its actively-updated branches to
ModDevGradle for Minecraft 1.21+ (its 1.21.1 example still on NeoGradle is
kept only as an archived branch). There is no reason to pick the
legacy tool for a project starting today.

## Building

Requires JDK 21. If you don't have one, an
[Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21)
tarball unpacked anywhere (no root needed) works — point `JAVA_HOME` at it.

```
./gradlew build
```

This compiles both subprojects, runs `engine`'s tests (including the
Minecraft-free classpath check below), and produces the mod jar at
`forge/build/libs/dynamicatmosphere-<version>.jar`.

## Testing

```
./gradlew test        # engine's JUnit 5 tests
./gradlew check        # tests + the verifyNoMinecraft task, both subprojects
```

### Minecraft-free enforcement

`engine` must never depend on Minecraft or NeoForge, and this is enforced
by the build in two independent ways (`engine/build.gradle`):

1. **`verifyNoMinecraft`** (a Gradle task, wired into `check`, so CI runs it
   without anyone remembering to) resolves `engine`'s `compileClasspath`,
   `runtimeClasspath`, `testCompileClasspath` and `testRuntimeClasspath`
   and fails if any resolved component's group is (or is a subgroup of)
   `net.minecraft` / `net.neoforged`, or its artifact name contains
   "minecraft"/"neoforge". This catches an accidental dependency add before
   it ever compiles.
2. **`ClasspathIsolationTest`** (`engine/src/test/.../ClasspathIsolationTest
   .java`) independently asserts, at the JVM level, that a real Minecraft
   class (`net.minecraft.world.level.block.Blocks`) and a real NeoForge/FML
   class (`net.neoforged.fml.common.Mod`) are NOT loadable
   (`Class.forName` throws `ClassNotFoundException`). Both class names were
   verified against NeoForge's own MDK example mod for 1.21.1 before being
   used here — see the test's own comment for exactly how and where.

Both were proven to actually fire, not just written and trusted: a real
NeoForge dependency (`net.neoforged.fancymodloader:loader:4.0.43`) was
temporarily added to `engine/build.gradle`, and both `verifyNoMinecraft`
and `ClasspathIsolationTest.neoForgeIsNotOnTheClasspath()` failed as a
result, quoted verbatim in this repo's PR history for this change. (The
Minecraft-side assertion in that same test could not be tripped the same
way in that demonstration, because Minecraft itself has no ordinary,
redistributable Maven coordinate to depend on by accident in the first
place — which is itself part of why this split is enforceable at all.)
The dependency was reverted immediately after.

## Repeating the load proof

Gradle producing a jar, or a dev-environment `runServer`/`runClient` task,
is not proof the mod loads — only running the actual built jar in a real
NeoForge instance is. To repeat it:

1. `./gradlew build`, then take `forge/build/libs/dynamicatmosphere-<version>.jar`.
2. Outside this repo (the scratch instance is never committed), download the
   NeoForge 21.1.248 installer from `maven.neoforged.net` and run:
   `java -jar neoforge-21.1.248-installer.jar --installServer`
   in an empty scratch directory.
3. Accept the EULA (`eula.txt` → `eula=true`) and drop the built jar into
   that instance's `mods/` folder.
4. Start it (the installer writes a `run.sh`/`user_jvm_args.txt` you can
   use, or invoke the server jar directly with `-Dfml.pickGameSourceSet=server`
   as needed) and read the log. A clean load shows, in order: the NeoForge
   version line, `dynamicatmosphere` being discovered, this mod's own
   `[dynamicatmosphere] engine module reachable: ...` line (which only
   prints because `engine`'s class made it into the jar and onto the
   runtime classpath — see "Layout" above), and a clean
   `Done (N.Ns)! For help, type "help"` with no exception trace.

See the PR/ticket for this change for the actual quoted log lines from the
last time this was run.
