package cl.franciscosolis.metalcraft;

import java.util.Locale;

/**
 * Static capability checks that decide whether the Metal backend may replace OpenGL on this machine.
 *
 * <p>These run before Minecraft starts and touch no native or game code, so an unsupported machine simply keeps the
 * vanilla renderer instead of crashing. A native Metal device is a 64-bit Apple Silicon GPU; the backend also needs
 * macOS 14 (Sonoma) for the Metal APIs it uses.
 */
public final class MetalSupport {
    private MetalSupport() {}

    public static final String OS_NAME = System.getProperty("os.name", "");
    public static final String OS_VERSION = System.getProperty("os.version", "");
    public static final String OS_ARCH = System.getProperty("os.arch", "");

    public static final int MINIMUM_MACOS_MAJOR = 14;

    /** @return {@code null} when this machine can run the Metal backend, otherwise a human-readable reason it can't. */
    public static String unsupportedReason() {
        if (!isMacOs(OS_NAME)) {
            return "not running on macOS (os.name=" + OS_NAME + ")";
        }
        if (!isAppleSilicon(OS_ARCH)) {
            return "not an Apple Silicon JVM (os.arch=" + OS_ARCH + ")";
        }
        int major = majorVersion(OS_VERSION);
        if (major > 0 && major < MINIMUM_MACOS_MAJOR) {
            return "macOS " + OS_VERSION + " is older than macOS " + MINIMUM_MACOS_MAJOR + " (Sonoma)";
        }
        return null;
    }

    static boolean isMacOs(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    static boolean isAppleSilicon(String arch) {
        String a = arch.toLowerCase(Locale.ROOT);
        return a.equals("aarch64") || a.equals("arm64");
    }

    /** "14.5.0" -> 14; -1 when the string doesn't start with a number. */
    static int majorVersion(String version) {
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        if (end == 0) return -1;
        try {
            return Integer.parseInt(version.substring(0, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
