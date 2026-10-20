package cl.franciscosolis.metalcraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts the bundled {@code libmetalcraft.dylib} and loads it.
 *
 * <p>Runs in the pre-launch phase; a failure is reported and turns the Metal path off rather than crashing the game.
 */
public final class NativeLoader {
    private static final String RESOURCE = "/natives/libmetalcraft.dylib";
    private static boolean loaded;

    private NativeLoader() {}

    public static synchronized boolean load() {
        if (loaded) return true;
        try (InputStream in = NativeLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                MetalCraft.LOGGER.error("{} is missing from the mod jar; rebuild the mod on macOS with Xcode command line tools", RESOURCE);
                return false;
            }
            // Fresh temp copy per launch so an updated jar never runs an old library.
            Path lib = Files.createTempFile("metalcraft", ".dylib");
            lib.toFile().deleteOnExit();
            Files.copy(in, lib, StandardCopyOption.REPLACE_EXISTING);
            System.load(lib.toAbsolutePath().toString());
            loaded = true;
            return true;
        } catch (IOException e) {
            MetalCraft.LOGGER.error("Could not extract libmetalcraft.dylib", e);
        } catch (UnsatisfiedLinkError e) {
            MetalCraft.LOGGER.error("Could not load libmetalcraft.dylib (architecture or macOS version mismatch)", e);
        } catch (SecurityException e) {
            MetalCraft.LOGGER.error("Not allowed to load libmetalcraft.dylib", e);
        }
        return false;
    }
}
