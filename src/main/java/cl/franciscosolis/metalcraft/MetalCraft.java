package cl.franciscosolis.metalcraft;

import cl.franciscosolis.metalcraft.mtl.Mtl;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. The actual backend swap happens in {@code RenderSystemMixin} once the game initialises its GPU
 * device; this class only keeps track of whether the Metal path is safe to use on this machine.
 *
 * <p>Deciding that early matters: the window is created without an OpenGL context when Metal is enabled, so a
 * fallback to OpenGL is only possible <em>before</em> the window exists. {@link MetalCraftPreLaunch} makes the call.
 */
public class MetalCraft implements ClientModInitializer {
    public static final String MOD_ID = "metalcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set by {@link MetalCraftPreLaunch}, or by {@code -Dmetalcraft.disable=true}. */
    private static volatile boolean disabled = Boolean.getBoolean("metalcraft.disable");
    private static volatile String disabledReason = disabled ? "-Dmetalcraft.disable=true" : null;

    public static boolean isDisabled() {
        return disabled;
    }

    /** @return why the OpenGL path was kept, or {@code null} while Metal is active. */
    public static String disabledReason() {
        return disabledReason;
    }

    static void disable(String reason) {
        if (disabled) return;
        disabled = true;
        disabledReason = reason;
    }

    @Override
    public void onInitializeClient() {
        if (disabled) {
            LOGGER.warn("Metal renderer unavailable ({}); Minecraft keeps using its normal OpenGL renderer", disabledReason);
            return;
        }
        LOGGER.info("Metal renderer active on {}", Mtl.deviceName());
    }
}
