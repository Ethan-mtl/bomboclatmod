# MetalCraft — native Metal renderer for Minecraft 1.21.11 (Fabric)

Minecraft's Java Edition renders through **OpenGL**. On macOS, Apple deprecated OpenGL years ago, so every draw call
Minecraft makes is translated to Metal by Apple's OpenGL driver — that translation is the main reason the game runs so
much slower on a Mac than the hardware should allow.

This mod removes OpenGL from the picture entirely. It implements Minecraft's GPU abstraction (`GpuDevice`,
`CommandEncoder`, `RenderPass`, introduced in 1.21.x and used by vanilla for every draw in the game) directly on
**Apple Metal**. The game window is created with no OpenGL context (`GLFW_NO_API`), presentation happens through a
`CAMetalLayer`, and vanilla's own `RenderPipeline` descriptions are turned into `MTLRenderPipelineState` objects.

There is **no OpenGL → Metal translation layer** in the path. The only translation left is shader *compilation*
(GLSL → SPIR-V → Metal Shading Language), which happens once per shader at load time and is cached on disk in
`saves/../metalcraft/shader-cache`. It does not run per frame.

> ### Status: experimental
> This is a fork of [Im-Fran/MetalCraft](https://github.com/Im-Fran/MetalCraft) v0.1.0 (GPL-3.0), retargeted and
> hardened for Minecraft **1.21.11**. The renderer core is upstream's work, tested by its author on an M5 Mac running
> macOS 27. The changes in this fork are packaging/platform-safety work: early capability detection, a real OpenGL
> fallback for unsupported machines, no Fabric API requirement, and a build that is verified on Apple Silicon.
>
> **Verified in CI** (GitHub Actions, `macos-14` Apple Silicon runner, Xcode + Java 25):
> the Java layer compiles against Minecraft 1.21.11 and Sodium 0.8.14, `libmetalcraft.dylib` compiles with clang, and
> the test suite translates **every vanilla core/post shader** plus Sodium's chunk shader through GLSL → SPIR-V → MSL
> and has the Metal runtime compiler accept all of them (3 tests, 0 failures).
> **Not yet play-tested in a world by the maintainer of this fork** — no Mac was available while preparing it. Back up
> your worlds and treat the first session as a test.

## Requirements

| | |
|---|---|
| Mac | Apple Silicon (M1 or newer) — Intel Macs are not supported by the native layer |
| macOS | 14 Sonoma or newer |
| Minecraft | 1.21.11, **Fabric Loader 0.19.5+** |
| Java | 21+ (the launcher's bundled Java is fine) |
| Fabric API | **not required** |
| Sodium | optional, `mc1.21.11-0.8.14` — its terrain renderer is ported to Metal too |
| Iris / shader packs | not supported |

## Download

Grab `metalcraft-<version>.jar` from the [latest release](https://github.com/Ethan-mtl/bomboclatmod/releases/latest).
Every release is built by CI on an Apple Silicon runner; builds from the branch are also attached to the corresponding
Actions run (Actions → **build** → *metalcraft-jar*).

## Install

1. Download the jar (or build it yourself, below).
2. Drop `metalcraft-<version>.jar` into `.minecraft/mods` next to Fabric Loader.
3. Start the game. The F3 debug screen lists the renderer as `Metal on Apple M…` when the backend is active; the log
   also prints `Metal renderer selected (GPU: …)`.

If anything is wrong (Intel Mac, old macOS, missing native library, no Metal device), the mod logs why and lets
Minecraft start on its normal OpenGL renderer instead of crashing.

## Build

Building needs macOS with Xcode command line tools (`xcrun clang++`) because the Metal layer is native Objective-C++,
plus a **JDK 25** to run Gradle/Loom (the mod itself still compiles to Java 21 bytecode, which is what Minecraft
1.21.11 runs on):

```sh
./gradlew build          # jar in build/libs, also runs the shader-translation tests
./gradlew runClient      # development client, with the Metal debug layer enabled
```

Useful development flags:

| Flag | Effect |
|---|---|
| `-PopenGL` | runs the same build on vanilla OpenGL, for A/B comparisons |
| `-PgpuProfile` | logs GPU milliseconds per render pass |
| `-Pbench` | disables the Metal validation layer for honest benchmark numbers |
| `-Psodium` | adds Sodium 0.8.14 to the dev runtime |

You can also build without a local Mac: `.github/workflows/build.yml` builds the jar on a `macos-14` (Apple Silicon)
GitHub Actions runner and uploads it as an artifact.

### Runtime switches

| System property | Effect |
|---|---|
| `-Dmetalcraft.disable=true` | keeps the OpenGL renderer (useful to confirm a bug comes from this mod) |
| `-Dmetalcraft.gpuProfile=true` | per-pass GPU timings in the log |

## How it works

* **Device** — `MetalDevice` implements Blaze3D's `GpuDevice` over a thin JNI veneer (`src/main/native/metalcraft.mm`).
  Buffers are shared-storage `MTLBuffer`s, so mapping a buffer is just a pointer; textures are `MTLTexture`s.
* **Commands** — `MetalCommandEncoder` / `MetalRenderPass` record into one `MTLCommandBuffer` per frame and commit it
  on present. Consecutive passes that reuse the same attachments keep a single render encoder open, which matters a lot
  on Apple's tile-based GPUs (it avoids storing and reloading the whole target between passes).
* **Shaders** — vanilla GLSL is translated GLSL → SPIR-V (shaderc, OpenGL semantics) → MSL (SPIRV-Cross) at pipeline
  compile time, then cached. Clip-space Y/Z are fixed up in the vertex stage so render targets keep GL's layout.
* **Presentation** — `Mtl.present` blits the game's main target into a `CAMetalLayer` drawable. With vsync off,
  drawables are prefetched on a GCD queue so the render thread never blocks on the compositor.
* **Sodium** (optional) — mixins route Sodium's GL buffer/shader/uniform/draw calls to Metal and install no-op GL
  entry points so Sodium's GL function pointers are never null.

## Known limitations

* Mods that call OpenGL directly (outside Blaze3D) will not work: there is no GL context at all.
* Iris and shader packs need OpenGL/Iris internals and are not supported.
* Only Sodium 0.8.x for 1.21.11 is wired up; Sodium 0.9+ is blocked by the mod metadata.
* `RenderPipeline` colour logic ops are ignored (no vanilla pipeline uses them).
* Array/3D textures are rejected by the device layer.
* Early pre-release upstream: expect rough edges in the Nether/End, with resource packs, and with unusual mods.

## Troubleshooting

* **Game crashes at startup with a Metal/device error** — start with `-Dmetalcraft.disable=true`. If it then works, the
  bug is in this mod; check `logs/latest.log` for the `MetalCraft:` lines.
* **No FPS change** — make sure the F3 screen says `Metal on …`. If it says `OpenGL`, the fallback kicked in; the log
  says why.
* **Weird shader colours or missing GUI** — delete the `metalcraft/shader-cache` folder in your game directory so the
  MSL translations are regenerated.
* **Sodium version mismatch** — use exactly `mc1.21.11-0.8.14`, or remove Sodium for the first test.

## Why this instead of MetalMine?

MetalMine (the mod this was originally requested to replicate) is closed-source and has no build for 1.21.11, so it
cannot be ported or redistributed. This project is the closest open implementation: a real native Metal backend for
1.21.11, under a free licence, with the source in this repository.

## Licence and credits

GPL-3.0-only, inherited from [Im-Fran/MetalCraft](https://github.com/Im-Fran/MetalCraft) v0.1.0. See `LICENSE` and
`NOTICE`. If you redistribute a built jar, keep the licence and attribution intact.
