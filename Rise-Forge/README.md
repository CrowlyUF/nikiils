# Rise → Forge 1.8.9

The Rise 6.2.4 client (from the [OpenRise](https://github.com/fmcpe/OpenRise) source) ported from a standalone MCP client to a
**Forge 1.8.9 mod jar**. You drop the jar into `mods/` and get the Rise ClickGUI, HUD, modules, commands,
configs and themes.

- Minecraft **1.8.9**, Forge **11.15.1.2318** (any 1.8.9 Forge build should work)
- Java **8** at runtime, which is what Forge 1.8.9 requires
- Mod id `rise`. A prebuilt jar is in [`release/Rise-6.2.4.jar`](release/Rise-6.2.4.jar)

## Build

```bash
./gradlew build          # -> build/libs/Rise-6.2.4.jar
```

Gradle downloads a JDK 8 toolchain on its own. Compiling just needs any JDK 17+ to start Gradle.

Install: put the jar in `.minecraft/mods/` with a Forge 1.8.9 profile. Press **Right Shift** to open the ClickGUI,
and use `.help` in chat for commands.

### macOS (Apple Silicon)

Forge 1.8.9 runs on LWJGL 2, which has no native arm64 build. Use a launcher that ships patched natives (Prism
Launcher handles this automatically for 1.8.9), or run the x86_64 Java 8 through Rosetta.

## How the port works

Rise patched about 80 vanilla classes in place. A mod can't do that, so each patch became one of these:

| Rise did | Port does |
|---|---|
| Fired its events from inside vanilla methods | **Mixins** in `com.alan.clients.mixin.*` fire the same events at the same points |
| Added fields to `Entity`/`EntityPlayerSP` (`offGroundTicks`, `movementYaw`, `ticksSinceVelocity`, …) | `EntityData`, attached to every entity by `MixinEntity`; call sites read `EntityData.of(entity).field` |
| Added methods to vanilla classes | Static equivalents in `util/mc/MCHooks` |
| Made private vanilla members public | Access transformer (`src/main/resources/accesstransformer.cfg`) |
| Replaced the vanilla font renderer | `MinecraftFont` adapts the vanilla renderer to Rise's `Font` API |
| Patched its own HUD into `GuiIngame` | Forge's HUD stays; the parts Rise replaces (hotbar, chat, XP, boss bar) are hidden through `RenderGameOverlayEvent` |

Movement events (`PreMotionEvent`, `StrafeEvent`, `JumpEvent`, `MoveEvent`), silent rotations (`movementYaw`,
`LookEvent`, server-side head pitch), silent slot switching, packet events and the render events all behave as
they did in Rise. Without that the combat and movement modules would compile but not work.

## Rendering compatibility (black world fix)

Rise's blur/bloom effects read and redraw the main framebuffer. The original client assumed a desktop driver and
OptiFine with Fast Render off, so on other setups the world could render black while the HUD and chat stayed visible.
This build:

- forces OptiFine **Fast Render** off, as Rise did, and keeps it off;
- checks every Rise shader for compile/link errors. If one fails, or the renderer is GL4ES (PojavLauncher), the
  blur/bloom effects switch off and everything else keeps working (`-Drise.effects=on|off` overrides this);
- binds the extra effect textures on unit 3 instead of 16/20, which most mobile GPUs don't have, and through
  `GlStateManager` so its texture cache stays in sync;
- allocates the stencil buffer through Forge (`DEPTH24_STENCIL8`, correct size) instead of Rise's unsized
  `GL_DEPTH_STENCIL` swap, which some drivers rejected, leaving the world without a depth buffer;
- restores the framebuffer, shader program and texture state after Rise's render events;
- no longer forces GUI scale 2, which made everything tiny on phones and high-res screens.

## What changed compared to OpenRise

**Removed. Everything here depended on Rise's servers, which are gone:**
- Vantage backend: login/HWID gate, IRC, the online community and cloud-config screens, IRC capes and nicknames
- The protection/obfuscation layer, the "security" packet filter and the packet-log detector

**Replaced:**
- ViaMCP is gone. Install [ViaForge](https://github.com/ViaVersion/ViaForge) if you need other server versions.
  The 1.9+ fixes (hitbox, block placement, transactions, …) detect ViaForge's target version at runtime.
- Rise's custom main menu: the Forge menu stays so the Mods button keeps working. Rise's **Alt Manager** is added
  as a button there.
- OptiFine is optional. Code that depended on it now checks for it by reflection.

**Optimizations:**
- Event bus: reflected `@EventLink` fields are cached per class. Each event type keeps a copy-on-write array, so
  toggling a module rebuilds only the types it touches. Dispatch is thread-safe, which matters for packet events
  that arrive on the netty thread, and one throwing listener no longer stops the listeners after it.
- `ScaledResolution` is cached per resolution and GUI scale instead of being rebuilt on every call.
- ViaForge protocol lookups are cached, since they run inside hitbox and ray-trace code.
- Packet direction checks use a set lookup instead of scanning an array for every packet.
- The jar ships only the fonts the client actually loads: assets went from about 130 MB to about 12 MB.
- Bug fixes found along the way:
  - The `ServerUtil` cache never cleared between servers.
  - `Ambience` skipped rain particles even while disabled (an operator-precedence bug).
  - The Server Finder rewrote the server list once per server it found.

**Scripts:** the Nashorn script engine still works on Java 8. On a JVM without Nashorn, scripts are disabled and the
rest of the client keeps working.

## Development

```bash
./gradlew build
```

Loom's `runClient` task fails Gradle 8's task validation, so dev launches go through Loom's dev-launch config.
Add `-Drise.selftest=true` to run the smoke test (`forge/SelfTest.java`). It:

1. Loads a flat singleplayer world.
2. Opens the ClickGUI.
3. Turns every module on and off.
4. Saves screenshots to `run/screenshots/`.
5. Logs the result with the `[selftest]` prefix.

Set `-Drise.dev=true` to get listener exceptions reported in chat as well as in the log.

## Credits

Rise by Alan, Rotel and The_Bi11iona1re. OpenRise is maintained by the HomelessSolutions team.
