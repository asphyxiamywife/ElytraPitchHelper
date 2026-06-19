> [this modrinth page](https://modrinth.com/mod/elytrapitchhelper) (modrinth[dot]com/mod/elytrapitchhelper) and [github releases](https://github.com/asphyxiamywife/ElytraPitchHelper/releases) are the only officially maintained download sources. since v2.1.0, releases uploaded to modrinth are the same jars u will find on github with immutability enabled and attestation provided so u can verify them easily

while gliding, the mod shows subtle pitch indicators at +40° and –40°, so u dont have to rely on the cluttered f3 debug screen. this makes it easier to stay focused on smooth and efficient flight. the indicators appear only when ure wearing elytra and close to these pitch angles, keeping ur screen clean the rest of the time.

![output](https://github.com/user-attachments/assets/5586de9c-9d75-4815-b7e1-e0d6782f2cb6)

as of v2.1.0, theres also an optional amplitude cue that helps u time climb and dive cycles based on height or velocity. its useful for maintaining a consistent rhythm without watching numbers.

did i mention it also includes pride themes that increase ur flight abilities by 20%? ...theyd call it elytra gay helper

---

## configuration

open the config screen in-game via mod menu or the keybind (unbound by default). all options are documented there.

config lives under `config/elytra-pitch-helper/` with one json per profile. profiles let u save different setups and switch between them from the config screen.

## migrating from v1.x.x / v2.0.0

the mod will try to migrate ur old config automatically. if anything looks off, the main changes are:

- all tuning options moved into grouped profile sections (e.g. `targetUpMinecraft` → `pitch.targetUpMinecraft`, `lineColorRgb` → `line.colorRgb`, etc.)
- `centerTickColorRgb` was removed.
- config moved from a single `elytra-pitch-helper.json` to the `config/elytra-pitch-helper/` folder with separate profile files.

## compilation
run `./gradlew build` and find the jar in `build/libs/`

## fuzzing

the regular `./gradlew test` run replays the fuzz regression corpus. to run coverage-guided fuzzing locally, use:

```sh
./gradlew fuzzProfileJson
./gradlew fuzzProfileFileNames
./gradlew fuzzConfigModel
./gradlew fuzzHudLogic
./gradlew fuzzScreenLogic
```

each target runs for 30 seconds by default. `./gradlew fuzz` runs all targets.
override the duration when needed with, for example,
`./gradlew fuzzProfileJson -PfuzzDuration=5m`.
