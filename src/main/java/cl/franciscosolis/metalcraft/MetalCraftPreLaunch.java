package cl.franciscosolis.metalcraft;

import cl.franciscosolis.metalcraft.mtl.Mtl;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Decides the rendering backend before Minecraft creates its window.
 *
 * <p>When Metal is used, the GLFW window is created with {@code GLFW_NO_API} and no OpenGL context at all, so this is
 * the last point where falling back to OpenGL is still possible. If anything about the machine or the native library
 * is wrong we disable Metal here and the game starts exactly like vanilla.
 */
public final class MetalCraftPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if (MetalCraft.isDisabled()) {
            MetalCraft.LOGGER.warn("Metal renderer disabled ({}); using the OpenGL renderer", MetalCraft.disabledReason());
            return;
        }

        String unsupported = MetalSupport.unsupportedReason();
        if (unsupported != null) {
            MetalCraft.disable(unsupported);
            MetalCraft.LOGGER.warn("Metal renderer unavailable: {}; using the OpenGL renderer", unsupported);
            return;
        }

        if (!NativeLoader.load()) {
            MetalCraft.disable("libmetalcraft.dylib could not be loaded");
            return;
        }

        String device = Mtl.deviceName();
        if (device == null || device.isEmpty() || "none".equals(device)) {
            MetalCraft.disable("Metal reported no usable GPU");
            MetalCraft.LOGGER.warn("Metal renderer unavailable: no usable Metal device; using the OpenGL renderer");
            return;
        }

        MetalCraft.LOGGER.info("Metal renderer selected (GPU: {})", device);
    }
}
