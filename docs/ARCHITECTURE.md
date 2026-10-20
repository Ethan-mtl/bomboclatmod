# Architecture

Notes for anyone maintaining this fork. Everything here describes upstream MetalCraft 0.1.0 unless marked otherwise.

## Where the backend is installed

Minecraft 1.21.11 draws through `com.mojang.blaze3d.systems.GpuDevice` (buffers, textures, samplers, pipelines) and
`CommandEncoder` / `RenderPass` (recording). The vanilla implementation is `net.minecraft.client.gl.GlBackend`,
constructed inside `RenderSystem.initRenderer(window, debugVerbosity, syncDebug, shaderSource, debugLabels)`.

* `mixin/WindowMixin` – creates the GLFW window with `GLFW_NO_API` (no GL context) and redirects
  `glfwSwapInterval` to `Mtl.setVsync`.
* `mixin/RenderSystemMixin` – cancels `initRenderer` and assigns a `MetalDevice` to `RenderSystem.DEVICE`, plus the
  `DynamicUniforms` / `SamplerCache` helpers, then installs Sodium GL stubs when Sodium is present. It also redirects
  `glfwSwapBuffers` to a no-op, because presentation happens in `MetalCommandEncoder.presentTexture`.
* `MetalCraftPreLaunch` – runs before the game starts, checks the platform and loads the native library. If it says
  "no", `MetalCraft.isDisabled()` is true and the two mixins above do nothing, so the game is plain vanilla.

## Java/native boundary

`src/main/native/metalcraft.mm` is a JNI veneer. Every Metal object crosses into Java as a retained `jlong` and must be
released with `Mtl.release`. All calls happen on Minecraft's render thread (the macOS main thread), so the native state
is unsynchronised on purpose.

The native layer owns:

* `MTLDevice`, `MTLCommandQueue`, a `MTLSharedEvent` used as a frame fence, and a `CAMetalLayer` attached to the GLFW
  window's `contentView`;
* the current `MTLCommandBuffer` and the open blit/render encoders;
* the built-in present/clear shaders and the `MTLRenderPipelineState` used to blit the game's main target;
* optional GPU timestamp counters used by the dev profiler.

## Frame flow

1. `RenderPass.draw*` calls are recorded into the open `MTLRenderCommandEncoder`.
2. `CommandEncoder.presentTexture` ends the encoder, blits/copies the colour target into the drawable with the built-in
   present shader, presents, commits, and waits until the CPU is at most three frames ahead of the GPU.
3. Buffer writes use shared-storage buffers: small ones are "orphaned" (new storage, old memory kept for recorded
   commands), larger ones are copied through a staging ring buffer.
4. Texture uploads are packed into the staging ring in the destination pixel format and blitted.

## Shaders

`shader/ShaderTranslator` runs GLSL 330 through shaderc (`-D` defines already injected by
`GlslPreprocessor.injectDefines`) into SPIR-V, then SPIRV-Cross into MSL 2.4. Resource bindings are recovered from
SPIR-V reflection, so vanilla GLSL is not rewritten. Two fix-ups are applied:

* clip space: Y flipped (render targets keep GL's bottom-up layout) and Z remapped from [-1, 1] to [0, 1];
* loose uniforms are gathered into a `MetalCraftDefaults` std140 block.

Results are cached in `<gameDir>/metalcraft/shader-cache`, keyed by a hash that includes the translator's own bytecode.
If you change the translator, old entries are invalidated automatically.

## Invariants worth knowing

* A render encoder may stay open across Blaze3D render passes; `Mtl.beginPass` merges when attachments and load
  actions allow it. Breaking that invariant (issuing a blit while a pass is open) is what `assertNoRenderPass` catches.
* Nothing executes until the command buffer is committed, so CPU writes to a resource the GPU may still read have to go
  through the staging ring (or orphaning) to preserve GL's in-order semantics.
* `Mtl.fence()` returns the value the *next* commit will signal; it does not submit. Waiting on a value that has not
  been submitted commits first.

## Adding a pass or a feature

1. Look at how vanilla's `RenderPipeline` describes it; if it fits, no Java changes are needed — `MetalPipeline` builds
   the state lazily per attachment format.
2. If a new `GpuDevice`/`CommandEncoder` method is needed, implement it in the `Metal*` classes, not in a mixin.
3. If a new Metal entry point is needed, add it to `Mtl.java`, implement the `Java_cl_franciscosolis_metalcraft_mtl_Mtl_*`
   function in `metalcraft.mm`, and keep the Java-side signature in sync (the JNI header is generated at compile time).
