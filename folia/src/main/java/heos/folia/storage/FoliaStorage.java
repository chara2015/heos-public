package heos.folia.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class FoliaStorage {
    private static final Gson GSON = new GsonBuilder().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOGGER = Logger.getLogger("Heos");
    private static final String TABLE = "players";
    private static final String KEY_NAME = "player_data_encryption_key";

    private final Path root;
    private Connection connection;
    private SecretKeySpec key;

    public FoliaStorage(Path root) {
        this.root = root;
    }

    public synchronized void initialize() {
        if (connection != null) {
            return;
        }
        try {
            Files.createDirectories(root);
            Class.forName("org.sqlite.JDBC");
            Path database = FoliaStoragePaths.dataFile(root, "player_data.db");
            FoliaStoragePaths.dataFile(root, "player_data.db-wal");
            FoliaStoragePaths.dataFile(root, "player_data.db-shm");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + "username TEXT NOT NULL,"
                                + "username_lower TEXT NOT NULL UNIQUE,"
                                + "uuid TEXT NULL,"
                                + "last_ip TEXT NULL,"
                                + "data TEXT NOT NULL"
                                + ");");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS metadata ("
                                + "name TEXT PRIMARY KEY,"
                                + "value BLOB NOT NULL"
                                + ");");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize Heos Folia storage", exception);
        }
    }

    public synchronized FoliaPlayerData load(String username) {
        return load(username, false);
    }

    public synchronized FoliaPlayerData load(String username, boolean onlineAccount) {
        initialize();
        String key = cacheKey(username);
        FoliaPlayerData data = loadByKey(username, key);
        if (data != null) {
            data.storageKey = key;
            return data;
        }

        FoliaPlayerData legacyData = loadByKey(username, legacySplitKey(username, onlineAccount));
        if (legacyData != null) {
            legacyData.storageKey = key;
            return legacyData;
        }

        FoliaPlayerData emptyData = new FoliaPlayerData(username);
        emptyData.isOnlineAccount = onlineAccount;
        emptyData.storageKey = key;
        return emptyData;
    }

    public synchronized FoliaPlayerData loadStored(String username) {
        initialize();
        String normalized = username.toLowerCase(Locale.ENGLISH);
        FoliaPlayerData data = loadByKey(username, normalized);
        if (data != null) {
            data.storageKey = normalized;
            return data;
        }

        data = loadByKey(username, legacySplitKey(username, true));
        if (data != null) {
            data.storageKey = normalized;
            return data;
        }

        data = loadByKey(username, legacySplitKey(username, false));
        if (data != null) {
            data.storageKey = normalized;
        }
        return data;
    }

    private FoliaPlayerData loadByKey(String username, String key) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT username, uuid, last_ip, data FROM " + TABLE + " WHERE username_lower = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                FoliaPlayerData data = GSON.fromJson(decrypt(resultSet.getString("data")), FoliaPlayerData.class);
                if (data == null) {
                    data = new FoliaPlayerData(username);
                }
                data.username = resultSet.getString("username");
                return data;
            }
        } catch (Exception exception) {
            LOGGER.warning("Failed to load player data for " + username + ": " + exception.getMessage());
            return null;
        }
    }

    public synchronized void save(FoliaPlayerData data) {
        initialize();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + TABLE + " (username, username_lower, uuid, last_ip, data) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(username_lower) DO UPDATE SET username = excluded.username, uuid = excluded.uuid, last_ip = excluded.last_ip, data = excluded.data")) {
            statement.setString(1, data.username);
            statement.setString(2, storageKey(data));
            statement.setString(3, data.uuid == null ? null : data.uuid.toString());
            statement.setString(4, data.lastIp);
            statement.setString(5, encrypt(GSON.toJson(data)));
            statement.executeUpdate();
        } catch (Exception exception) {
            LOGGER.warning("Failed to save player data for " + data.username + ": " + exception.getMessage());
        }
    }

    public synchronized boolean exists(String username) {
        initialize();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + TABLE + " WHERE username_lower IN (?, ?, ?) LIMIT 1")) {
            statement.setString(1, username.toLowerCase(Locale.ENGLISH));
            statement.setString(2, legacySplitKey(username, true));
            statement.setString(3, legacySplitKey(username, false));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception exception) {
            LOGGER.warning("Failed to check player data for " + username + ": " + exception.getMessage());
            return false;
        }
    }

    public synchronized boolean delete(String username) {
        initialize();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + TABLE + " WHERE username_lower IN (?, ?, ?)")) {
            statement.setString(1, username.toLowerCase(Locale.ENGLISH));
            statement.setString(2, legacySplitKey(username, true));
            statement.setString(3, legacySplitKey(username, false));
            return statement.executeUpdate() > 0;
        } catch (Exception exception) {
            LOGGER.warning("Failed to delete player data for " + username + ": " + exception.getMessage());
            return false;
        }
    }

    public synchronized Set<String> usernames() {
        initialize();
        Set<String> usernames = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DISTINCT username FROM " + TABLE + " ORDER BY username COLLATE NOCASE")) {
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                if (username != null && !username.isBlank()) {
                    usernames.add(username);
                }
            }
        } catch (Exception exception) {
            LOGGER.warning("Failed to list stored player names: " + exception.getMessage());
        }
        return usernames;
    }

    public synchronized int clearRuleAgreements() {
        int cleared = 0;
        for (String username : usernames()) {
            FoliaPlayerData data = loadStored(username);
            if (data == null || (!data.hasAcceptedRules && !data.hasSeenRulesBook)) {
                continue;
            }
            data.hasAcceptedRules = false;
            data.hasSeenRulesBook = false;
            save(data);
            cleared++;
        }
        return cleared;
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception exception) {
            LOGGER.warning("Failed to close Heos Folia storage: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    private String encrypt(String plainText) throws Exception {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private String decrypt(String encryptedText) throws Exception {
        if (encryptedText.contains(":")) {
            String[] parts = encryptedText.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted data");
            }
            byte[] nonce = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            return decrypt(nonce, encrypted);
        }

        byte[] combined = Base64.getDecoder().decode(encryptedText);
        if (combined.length <= 12) {
            throw new IllegalArgumentException("Invalid encrypted data");
        }
        byte[] nonce = new byte[12];
        byte[] encrypted = new byte[combined.length - nonce.length];
        System.arraycopy(combined, 0, nonce, 0, nonce.length);
        System.arraycopy(combined, nonce.length, encrypted, 0, encrypted.length);
        return decrypt(nonce, encrypted);
    }

    private String decrypt(byte[] nonce, byte[] encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKeySpec key() throws Exception {
        if (key != null) {
            return key;
        }

        Path keyPath = FoliaStoragePaths.dataFile(root, "secret.key");
        byte[] databaseKey = readDatabaseKey();
        if (databaseKey != null) {
            key = createKey(databaseKey);
            deleteLegacyKeyFile(keyPath);
            return key;
        }

        byte[] keyBytes;
        if (Files.exists(keyPath)) {
            keyBytes = Files.readAllBytes(keyPath);
            createKey(keyBytes);
        } else {
            keyBytes = new byte[32];
            RANDOM.nextBytes(keyBytes);
        }

        writeDatabaseKey(keyBytes);
        deleteLegacyKeyFile(keyPath);
        LOGGER.fine("Stored local player data encryption key in player_data.db");
        key = createKey(keyBytes);
        return key;
    }

    private byte[] readDatabaseKey() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM metadata WHERE name = ?")) {
            statement.setString(1, KEY_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBytes("value") : null;
            }
        }
    }

    private void writeDatabaseKey(byte[] keyBytes) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO metadata (name, value) VALUES (?, ?)")) {
            statement.setString(1, KEY_NAME);
            statement.setBytes(2, keyBytes);
            statement.executeUpdate();
        }
    }

    private static SecretKeySpec createKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("Invalid Heos secret key length: " + (keyBytes == null ? 0 : keyBytes.length));
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static void deleteLegacyKeyFile(Path keyPath) {
        try {
            Files.deleteIfExists(keyPath);
        } catch (Exception exception) {
            LOGGER.warning("Failed to delete migrated secret key file " + keyPath);
        }
    }

    public static String cacheKey(String username) {
        return username.toLowerCase(Locale.ENGLISH);
    }

    private static String legacySplitKey(String username, boolean onlineAccount) {
        return (onlineAccount ? "online:" : "offline:") + username.toLowerCase(Locale.ENGLISH);
    }

    private String storageKey(FoliaPlayerData data) {
        if (data.storageKey == null || data.storageKey.isEmpty()) {
            data.storageKey = cacheKey(data.username);
        }
        return data.storageKey;
    }
}
