package heos.folia.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

final class FoliaStoragePaths {
    private FoliaStoragePaths() {
    }

    static Path dataRoot(Path root) throws IOException {
        Path dataRoot = root.resolve("data");
        Files.createDirectories(dataRoot);
        return dataRoot;
    }

    static Path dataFile(Path root, String name) throws IOException {
        Path target = dataRoot(root).resolve(name);
        Path legacy = root.resolve(name);
        if (!Files.exists(target) && Files.exists(legacy)) {
            move(legacy, target);
        }
        return target;
    }

    static Path rootFile(Path root, String name) throws IOException {
        Files.createDirectories(root);
        Path target = root.resolve(name);
        Path nested = root.resolve("data").resolve(name);
        if (!Files.exists(target) && Files.exists(nested)) {
            move(nested, target);
        }
        return target;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}
