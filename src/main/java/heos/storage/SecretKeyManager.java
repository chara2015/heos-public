package heos.storage;

import heos.utils.HeosLogger;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class SecretKeyManager {
    private static final String KEY_FILE = "secret.key";
    private static final String KEY_NAME = "player_data_encryption_key";
    private static final String LEGACY_KEY_SEED = "heos-player-data-v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static SecretKeySpec currentKey;

    private SecretKeyManager() {
    }

    public static synchronized SecretKeySpec currentKey(Connection connection) throws IOException {
        if (currentKey == null) {
            currentKey = loadOrGenerateKey(connection);
        }
        return currentKey;
    }

    public static SecretKeySpec legacyKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new SecretKeySpec(digest.digest(LEGACY_KEY_SEED.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "AES");
    }

    private static SecretKeySpec loadOrGenerateKey(Connection connection) throws IOException {
        StoragePaths.ensureDataRoot();
        File keyFile = StoragePaths.dataFile(KEY_FILE);
        File legacyKeyFile = StoragePaths.file(KEY_FILE);
        byte[] databaseKey = readDatabaseKey(connection);
        if (databaseKey != null) {
            deleteLegacyKeyFiles(keyFile, legacyKeyFile);
            return createKey(databaseKey);
        }

        File existingKeyFile = keyFile.exists() ? keyFile : legacyKeyFile;
        byte[] keyBytes;
        if (existingKeyFile.exists()) {
            keyBytes = Files.readAllBytes(existingKeyFile.toPath());
            createKey(keyBytes);
        } else {
            keyBytes = new byte[32];
            RANDOM.nextBytes(keyBytes);
        }

        writeDatabaseKey(connection, keyBytes);
        deleteLegacyKeyFiles(keyFile, legacyKeyFile);
        HeosLogger.debug("Stored local player data encryption key in player_data.db");
        return createKey(keyBytes);
    }

    private static byte[] readDatabaseKey(Connection connection) throws IOException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM metadata WHERE name = ?")) {
            statement.setString(1, KEY_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBytes("value") : null;
            }
        } catch (Exception e) {
            throw new IOException("Failed to load Heos secret key from player_data.db", e);
        }
    }

    private static void writeDatabaseKey(Connection connection, byte[] keyBytes) throws IOException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO metadata (name, value) VALUES (?, ?)")) {
            statement.setString(1, KEY_NAME);
            statement.setBytes(2, keyBytes);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IOException("Failed to store Heos secret key in player_data.db", e);
        }
    }

    private static SecretKeySpec createKey(byte[] keyBytes) throws IOException {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IOException("Invalid Heos secret key length: " + (keyBytes == null ? 0 : keyBytes.length));
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static void deleteLegacyKeyFiles(File keyFile, File legacyKeyFile) {
        deleteLegacyKeyFile(keyFile);
        deleteLegacyKeyFile(legacyKeyFile);
    }

    private static void deleteLegacyKeyFile(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            HeosLogger.warn("Failed to delete migrated secret key file " + file.getPath());
        }
    }
}
