> [this modrinth page](https://modrinth.com/mod/elytrapitchhelper) (modrinth[dot]com/mod/elytrapitchhelper) and [github releases](https://github.com/asphyxiamywife/ElytraPitchHelper/releases) are the only officially maintained download sources. since v2.1.0, releases uploaded to modrinth are the same jars u will find on github with immutability enabled and attestation provided so u can verify them easily

while gliding, the mod shows subtle pitch indicators at +40° and –40°, so u dont have to rely on the cluttered f3 debug screen. this makes it easier to stay focused on smooth and efficient flight. the indicators appear only when ure wearing elytra and close to these pitch angles, keeping ur screen clean the rest of the time.

![output](https://github.com/user-attachments/assets/5586de9c-9d75-4815-b7e1-e0d6782f2cb6)

as of v3.0.0, theres also an optional amplitude cue that helps u time climb and dive cycles based on height or velocity. its useful for maintaining a consistent rhythm without watching numbers. each cue also leaves a tiny motion glyph: a brisk pull-up after the dive and a slower release after the climb, so the two turns dont look falsely identical. theres a void warning too, for when the ground u were counting on isnt there.

did i mention it also includes pride themes that increase ur flight abilities by 20%? ...theyd call it elytra gay helper

---


## configuration

open the config screen in-game via mod menu (fabric) or the mods list (neoforge), or bind a key for it. keybinds live under **Elytra Pitch Helper** in the controls screen:

- **command palette** — `ctrl+k`, or `cmd+k` on macos. search every setting and action, run it from the list. what u use most floats to the top.
- **toggle guides** — unbound by default.
- **open config** — unbound by default.
- **open profiles** — unbound by default.

config lives under `config/elytra-pitch-helper/`, with one json per profile in `profiles/` and the mod's own internals in `.internal/`.

## compilation

run `./gradlew build` to build every loader, or build just one loader with:

```sh
./gradlew buildFabric
./gradlew buildNeoForge
```

jars are written under each loader module's `build/libs/` directory.

## fuzzing

the regular `./gradlew test` run replays the fuzz regression corpus. to run coverage-guided fuzzing locally, use:

```sh
./gradlew fuzzProfileJson
./gradlew fuzzProfileFileNames
./gradlew fuzzProfileBackups
./gradlew fuzzConfigModel
./gradlew fuzzClientLogic
./gradlew fuzzCoreUtilities
./gradlew fuzzHudLogic
./gradlew fuzzScreenLogic
```

a target is registered for every `*FuzzTest.java` under `common/src/test/java`, so a new one needs no build change. crashing inputs belong in `src/test/resources` next to their target, where the plain `test` run replays them.

each target runs for 30 seconds by default. `./gradlew fuzz` runs all targets.
override the duration when needed with, for example,
`./gradlew fuzzProfileJson -PfuzzDuration=5m`.
