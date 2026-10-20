package cl.franciscosolis.metalcraft.mixin;

import cl.franciscosolis.metalcraft.MetalCraft;
import cl.franciscosolis.metalcraft.NativeLoader;
import cl.franciscosolis.metalcraft.compat.sodium.SodiumMetal;
import cl.franciscosolis.metalcraft.device.MetalDevice;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.DynamicUniforms;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
abstract class RenderSystemMixin {
    @Shadow private static GpuDevice DEVICE;
    @Shadow private static String apiDescription;
    @Shadow private static DynamicUniforms dynamicUniforms;
    @Shadow private static SamplerCache samplerCache;

    /** Same as vanilla initRenderer, with MetalDevice in place of GlDevice. */
    @Inject(method = "initRenderer", at = @At("HEAD"), cancellable = true)
    private static void metalcraft$initMetal(long window, int debugVerbosity, boolean syncDebug, ShaderSource shaderSource, boolean debugLabels, CallbackInfo ci) {
        if (MetalCraft.isDisabled()) return;
        if (!NativeLoader.load()) {
            // The window already exists without a GL context, so the OpenGL renderer can no longer start.
            throw new IllegalStateException("MetalCraft: libmetalcraft.dylib failed to load. Start the game with "
                    + "-Dmetalcraft.disable=true to use OpenGL instead.");
        }
        try {
            DEVICE = new MetalDevice(window, shaderSource);
        } catch (RuntimeException | Error e) {
            MetalCraft.LOGGER.error("MetalCraft: could not create the Metal device. Start the game with "
                    + "-Dmetalcraft.disable=true to use OpenGL instead.", e);
            throw e;
        }
        apiDescription = DEVICE.getImplementationInformation();
        dynamicUniforms = new DynamicUniforms();
        samplerCache.initialize();
        if (FabricLoader.getInstance().isModLoaded("sodium")) SodiumMetal.installGlStubs();
        ci.cancel();
    }

    /** Frames are presented by MetalCommandEncoder.presentTexture; there's no GL back buffer to swap. */
    @Redirect(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void metalcraft$noSwap(long window) {
        if (MetalCraft.isDisabled()) GLFW.glfwSwapBuffers(window);
    }
}
