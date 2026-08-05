package heos.storage;

import heos.Heos;

import java.io.File;
import java.nio.file.Path;

/**
 * Central paths for Heos server-side data under `server/heos`.
 */
public final class StoragePaths {
    private static final String ROOT_DIR = "heos";
    private static final String DATA_DIR = "data";

    private StoragePaths() {
    }

    public static Path root() {
        return Heos.gameDirectory.resolve(ROOT_DIR);
    }

    public static File file(String name) {
        return root().resolve(name).toFile();
    }

    public static Path dataRoot() {
        return root().resolve(DATA_DIR);
    }

    public static File dataFile(String name) {
        return dataRoot().resolve(name).toFile();
    }

    public static void ensureRoot() {
        File root = root().toFile();
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Failed to create Heos data directory: " + root.getPath());
        }
    }

    public static void ensureDataRoot() {
        ensureRoot();
        File dataRoot = dataRoot().toFile();
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            throw new IllegalStateException("Failed to create Heos data directory: " + dataRoot.getPath());
        }
    }
}
