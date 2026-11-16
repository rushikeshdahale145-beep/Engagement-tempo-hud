# Engagement Tempo HUD (Minecraft 26.2, Fabric, client-only)

Invisible until a combat window opens, then a tiny reflex-sight next to your crosshair:
- Shield disabled -> 5.00s bar
- Ender pearl thrown / chorus fruit eaten -> 1.00s bar
- Totem pop -> stopwatch to re-equip, shown as `0.40s` (green >0.5s, amber 0.25-0.5s, red <0.25s); `NO TOTEM` after 2.5s

100% passive: no packets sent, no server NBT/inventory reads.

## Commands (client-side)
- `/tempo toggle <shield|pearl|totem>`
- `/tempo offset <x> <y>`  (default 18 14)
- `/tempo scale <0.5-3.0>`
- `/tempo preview`  (looping dummy timers, auto-off after 60s)

Config: `config/engagement_tempo.json`

## Build
1. Set `fabric_version` in `gradle.properties` (see https://fabricmc.net/develop for 26.2).
2. Add the Gradle wrapper (`gradle wrapper --gradle-version 9.5.1`, or copy `gradlew`, `gradlew.bat`,
   `gradle/wrapper/gradle-wrapper.jar` from any working Fabric project).
3. `./gradlew build` with JDK 25 -> `build/libs/engagement-tempo-hud-1.0.0.jar`.

The included GitHub Actions workflow (`.github/workflows/build.yml`) builds with Gradle 9.5.1 and uploads the jar, no wrapper needed.
