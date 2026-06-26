package com.standalone.medadmin.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Database {

    private static final String APP_DIR_NAME = "MediCore";
    private static final String LEGACY_DIR_NAME = ".med-admin";
    private static final String DB_FILE_NAME = "medadmin.db";

    private static Path appDirectory;

    private Database() {
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + getAppDirectory().resolve(DB_FILE_NAME));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect database", e);
        }
    }

    public static void initialize() {
        getAppDirectory();

        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS admin_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT UNIQUE NOT NULL,
                        password_hash TEXT NOT NULL,
                        full_name TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS medicines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        quantity INTEGER NOT NULL DEFAULT 0,
                        price REAL NOT NULL,
                        expiry_date TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        medicine_id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        reference_no TEXT,
                        note TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (medicine_id) REFERENCES medicines(id)
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        notify_low_stock INTEGER NOT NULL DEFAULT 1,
                        low_stock_threshold INTEGER NOT NULL DEFAULT 10,
                        dark_mode INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS admin_settings (
                        admin_user_id INTEGER PRIMARY KEY,
                        notify_low_stock INTEGER NOT NULL DEFAULT 1,
                        low_stock_threshold INTEGER NOT NULL DEFAULT 10,
                        dark_mode INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (admin_user_id) REFERENCES admin_users(id)
                    )
                    """);

            st.executeUpdate("INSERT OR IGNORE INTO app_settings(id, notify_low_stock) VALUES(1, 1)");
            ensureColumnExists(con, "app_settings", "low_stock_threshold",
                    "ALTER TABLE app_settings ADD COLUMN low_stock_threshold INTEGER NOT NULL DEFAULT 10");
            ensureColumnExists(con, "app_settings", "dark_mode",
                    "ALTER TABLE app_settings ADD COLUMN dark_mode INTEGER NOT NULL DEFAULT 0");
            ensureColumnExists(con, "medicines", "admin_user_id",
                    "ALTER TABLE medicines ADD COLUMN admin_user_id INTEGER NOT NULL DEFAULT 0");
            ensureColumnExists(con, "stock_movements", "admin_user_id",
                    "ALTER TABLE stock_movements ADD COLUMN admin_user_id INTEGER NOT NULL DEFAULT 0");

            st.executeUpdate("""
                    UPDATE medicines
                    SET admin_user_id = (SELECT id FROM admin_users ORDER BY id LIMIT 1)
                    WHERE admin_user_id = 0
                    AND EXISTS (SELECT 1 FROM admin_users)
                    """);
            st.executeUpdate("""
                    UPDATE stock_movements
                    SET admin_user_id = (SELECT id FROM admin_users ORDER BY id LIMIT 1)
                    WHERE admin_user_id = 0
                    AND EXISTS (SELECT 1 FROM admin_users)
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private static void ensureColumnExists(Connection con, String tableName, String columnName, String alterSql) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(alterSql);
        }
    }

    private static synchronized Path getAppDirectory() {
        if (appDirectory != null) {
            return appDirectory;
        }

        List<Path> candidates = buildCandidateDirectories();
        List<String> failures = new ArrayList<>();

        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            try {
                Files.createDirectories(candidate);
                appDirectory = candidate;
                return appDirectory;
            } catch (Exception ex) {
                failures.add(candidate + " -> " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        throw new RuntimeException("Failed to create app directory. Tried: " + String.join(" | ", failures));
    }

    private static List<Path> buildCandidateDirectories() {
        Set<Path> candidates = new LinkedHashSet<>();

        addOverride(candidates, System.getProperty("medicore.data.dir"));
        addOverride(candidates, System.getenv("MEDICORE_DATA_DIR"));

        Path legacyHomeDir = resolveLegacyHomeDirectory();
        if (legacyHomeDir != null && Files.exists(legacyHomeDir)) {
            candidates.add(legacyHomeDir);
        }

        if (isWindows()) {
            addChildPath(candidates, System.getenv("LOCALAPPDATA"), APP_DIR_NAME);
            addChildPath(candidates, System.getenv("APPDATA"), APP_DIR_NAME);
        }

        if (legacyHomeDir != null) {
            candidates.add(legacyHomeDir);
        }

        addChildPath(candidates, System.getProperty("user.dir"), LEGACY_DIR_NAME);
        addChildPath(candidates, System.getProperty("java.io.tmpdir"), APP_DIR_NAME);

        return new ArrayList<>(candidates);
    }

    private static void addOverride(Set<Path> candidates, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        candidates.add(Path.of(value));
    }

    private static void addChildPath(Set<Path> candidates, String parent, String child) {
        if (parent == null || parent.isBlank()) {
            return;
        }
        candidates.add(Path.of(parent, child));
    }

    private static Path resolveLegacyHomeDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null;
        }
        return Path.of(userHome, LEGACY_DIR_NAME);
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }
}
