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

## Releasing to Modrinth

This section is the runbook for the NEXT time someone cuts a version. It
assumes nothing about this repo beyond what is in it right now; anything
that could drift (a rinth tag, a secret's exact state, a workflow's exact
flags) is a live check below, not a restated value that will go stale.

### The two workflows

- **`.github/workflows/modrinth-draft-create.yml`** — one-shot. Creates the
  Modrinth project as a DRAFT. Already run for `dynamic-atmosphere` unless
  this ticket's own writeup says otherwise; you should not need to run it
  again. workflow_dispatch only.
- **`.github/workflows/release.yml`** — the actual release path. Triggers on
  `release: types: [published]` (a real GitHub Release) and on
  `workflow_dispatch` (a dry run only — see below). Both build the mod jar
  with the version wired in from the trigger; only the `release` trigger
  ever publishes to Modrinth for real.

### Listing decisions (categories, client/server side)

`modrinth-draft-create.yml` hardcodes these; changing them means changing
the workflow, not a config file. Reasoning, so the next person doesn't have
to reconstruct it or dig up the SICKOS-40 ticket:

- **Categories: `game-mechanics`, `mobs`.** Modrinth has no weather or
  atmosphere category (checked live against
  `https://api.modrinth.com/v2/tag/category` for `project_type=mod`, 19
  categories total, re-check before trusting this is unchanged).
  `game-mechanics` because the mod's core purpose is a generic, data-driven
  mechanic, not decoration or world generation. `mobs` because the design
  spec's own headline Purpose examples include Zombie Fog changing zombie
  spawn behavior — a first-class example, not a buried side effect, even
  though Zombie Fog isn't in the Phase 1 MVP.
- **`--client-side required --server-side required`.** The simulation is
  server-authoritative (material state, movement, and thresholds are all
  world state), and the MVP's whole deliverable is a perceivable, rendered
  atmosphere — a client without the mod isn't getting the point of it. The
  current repo has no vanilla-fallback path, so this wasn't relaxed to
  `optional` on a guess.

### What must exist first

Both workflows read `secrets.MODRINTH_TOKEN` and `vars.MODRINTH_PROJECT_ID`
on THIS repo (`brooswit-minecraft/dynamic-atmosphere`), never a value copied
from another repo. Check what is currently set with:

```
gh secret list -R brooswit-minecraft/dynamic-atmosphere
gh variable list -R brooswit-minecraft/dynamic-atmosphere
```

- If `MODRINTH_TOKEN` is missing: every Modrinth-writing step in both
  workflows degrades on purpose — it prints which of `MODRINTH_TOKEN` /
  `MODRINTH_PROJECT_ID` is missing in the job summary and skips the write,
  rather than failing with a raw API error. The jar still builds and the CI
  build/test job is unaffected. Setting it needs a repo secret write
  (`gh secret set MODRINTH_TOKEN -R brooswit-minecraft/dynamic-atmosphere`),
  which needs repo admin.
- If `MODRINTH_PROJECT_ID` is missing: same degrade. Set it as a repo
  variable (`gh variable set MODRINTH_PROJECT_ID -R
  brooswit-minecraft/dynamic-atmosphere`) once the draft project exists —
  `modrinth-draft-create.yml`'s live-create step prints the created id
  prominently (job summary + a `rinth-project-create-json` artifact) for a
  human to record; it does not set the variable itself.

### Dry run first, always

`workflow_dispatch` on `release.yml` never publishes for real, no matter
what you type — the live publish step is gated on `github.event_name ==
'release'`, not on any input. Dispatch it (Actions tab, or `gh workflow run
release.yml -R brooswit-minecraft/dynamic-atmosphere -f version=0.0.2-dryrun`)
with a literal test version such as:

```
version: 0.0.2-dryrun
```

Read the job summary: it shows the resolved version, the jar file built,
the version read back out of that jar's `neoforge.mods.toml` (these two
must agree — the job fails loudly if they don't), and — if
`MODRINTH_TOKEN`/`MODRINTH_PROJECT_ID` are both set — the exact JSON payload
`rinth publish --dry-run` would have sent, with no network write made.

### Cutting a real release

1. Confirm `MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID` are both set (above).
2. Create a GitHub Release with a tag shaped `vN` (for example `v0.1.0`) —
   the workflow strips the leading `v`. Publishing the release (not saving a
   draft) is what fires `release.yml` on `release: types: [published]`.
3. That run builds the jar with the release's version baked in throughout
   (Gradle `version`, the jar file name, and the in-jar
   `neoforge.mods.toml` `version=` — all one source of truth, see
   `forge/build.gradle`), then runs `rinth publish` for real: loader
   `neoforge`, game version `1.21.1`, channel derived as `beta` if the
   version contains a hyphen else `release`, changelog taken from the GitHub
   Release body.
4. `rinth` itself refuses to publish a duplicate `version_number` before any
   upload — republishing the same tag by accident fails loudly rather than
   silently overwriting.

### Verifying what actually landed

Human-readable `rinth project get`/`rinth versions list` output omits
`description` and `body` entirely (`formatProject` in rinth's
`src/commands/project.ts` — read it at whichever tag you have pinned rather
than trusting this line). Always add the global `--json` flag:

```
bunx --bun github:brooswit-minecraft/rinth#<pinned tag> --json project get dynamic-atmosphere
bunx --bun github:brooswit-minecraft/rinth#<pinned tag> --json versions list dynamic-atmosphere
```

check the pin actually in use in `release.yml`'s `RINTH_REF` before trusting
the tag above — this line will drift. From `project get`, at minimum
confirm: `status` (must stay `draft` unless someone has deliberately
submitted it, see below), `description`, `body`, `license`, `source_url`,
`issues_url`, `categories`. From `versions list`, confirm the new version's
`version_number`, `game_versions`, `loaders`, and `version_type` match what
was just published.

### Submitting for review is a separate, deliberate, human-gated act

Nothing in this repo's workflows calls `rinth project submit` or sets
`requested_status`, anywhere, ever — verify this yourself before trusting
it: `grep -rn "submit\|requested_status" .github/`. That is deliberate, not
an oversight: submitting moves the project out of `draft` and into
Modrinth's moderation queue, which is a one-way, queue-position-costing
action a release workflow must never take on anyone's behalf. When the
project is actually ready for review, a human runs `rinth project submit
<slug>` by hand, having first confirmed (by reading `project get --json`'s
`status`, `body`, and the published versions) that what is about to go in
front of a moderator is what they intend to ship.
