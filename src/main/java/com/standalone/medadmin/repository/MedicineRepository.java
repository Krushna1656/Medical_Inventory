package com.standalone.medadmin.repository;

import com.standalone.medadmin.db.Database;
import com.standalone.medadmin.model.Medicine;
import com.standalone.medadmin.model.StockMovement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MedicineRepository {

    public record AuthenticatedAdmin(long id, String displayName) {}

    private Long activeAdminUserId;

    public void setActiveAdminUser(Long adminUserId) {
        this.activeAdminUserId = adminUserId;
    }

    public List<Medicine> findAllMedicines() {
        long adminUserId = requireActiveAdminUserId();
        String sql = "SELECT id, name, quantity, price, expiry_date FROM medicines WHERE admin_user_id = ? ORDER BY name";
        List<Medicine> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, adminUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMedicine(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch medicines", e);
        }
    }

    public List<Medicine> findNearExpiry(int days) {
        long adminUserId = requireActiveAdminUserId();
        String sql = "SELECT id, name, quantity, price, expiry_date FROM medicines WHERE admin_user_id = ? AND date(expiry_date) <= date('now', ?) ORDER BY expiry_date";
        List<Medicine> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, adminUserId);
            ps.setString(2, "+" + days + " day");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMedicine(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch near-expiry medicines", e);
        }
    }

    public void addMedicine(String name, int quantity, double price, LocalDate expiryDate) {
        long adminUserId = requireActiveAdminUserId();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Medicine name is required.");
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be >= 0.");
        if (price <= 0) throw new IllegalArgumentException("Price must be greater than zero.");
        if (expiryDate == null) throw new IllegalArgumentException("Expiry date is required.");

        String sql = "INSERT INTO medicines(name, quantity, price, expiry_date, admin_user_id) VALUES(?, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, quantity);
            ps.setDouble(3, price);
            ps.setString(4, expiryDate.toString());
            ps.setLong(5, adminUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add medicine", e);
        }
    }

    public void updateMedicine(long id, String name, double price, LocalDate expiryDate) {
        long adminUserId = requireActiveAdminUserId();
        if (id <= 0) throw new IllegalArgumentException("Invalid medicine id.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Medicine name is required.");
        if (price <= 0) throw new IllegalArgumentException("Price must be greater than zero.");
        if (expiryDate == null) throw new IllegalArgumentException("Expiry date is required.");

        String sql = "UPDATE medicines SET name = ?, price = ?, expiry_date = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND admin_user_id = ?";
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.setString(3, expiryDate.toString());
            ps.setLong(4, id);
            ps.setLong(5, adminUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update medicine", e);
        }
    }

    public void deleteMedicine(long id) {
        long adminUserId = requireActiveAdminUserId();
        try (Connection con = Database.getConnection();
             PreparedStatement delMovements = con.prepareStatement("DELETE FROM stock_movements WHERE medicine_id = ? AND admin_user_id = ?");
             PreparedStatement delMedicine = con.prepareStatement("DELETE FROM medicines WHERE id = ? AND admin_user_id = ?")) {
            delMovements.setLong(1, id);
            delMovements.setLong(2, adminUserId);
            delMovements.executeUpdate();
            delMedicine.setLong(1, id);
            delMedicine.setLong(2, adminUserId);
            delMedicine.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete medicine", e);
        }
    }

    public void restock(long medicineId, int qty, String reference, String note) {
        changeStock(medicineId, qty, "IN", reference, note);
    }

    public void consume(long medicineId, int qty, String reference, String note) {
        changeStock(medicineId, qty, "OUT", reference, note);
    }

    public List<StockMovement> findRecentMovements(int limit) {
        long adminUserId = requireActiveAdminUserId();
        String sql = """
                SELECT sm.id, m.name AS medicine_name, sm.type, sm.quantity, sm.reference_no, sm.note, sm.created_at
                FROM stock_movements sm
                JOIN medicines m ON m.id = sm.medicine_id
                WHERE sm.admin_user_id = ? AND m.admin_user_id = ?
                ORDER BY sm.id DESC
                LIMIT ?
                """;
        List<StockMovement> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, adminUserId);
            ps.setLong(2, adminUserId);
            ps.setInt(3, Math.max(limit, 1));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StockMovement(
                            rs.getLong("id"),
                            rs.getString("medicine_name"),
                            rs.getString("type"),
                            rs.getInt("quantity"),
                            rs.getString("reference_no"),
                            rs.getString("note"),
                            rs.getString("created_at")
                    ));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch stock movements", e);
        }
    }

    public boolean getNotifyLowStock() {
        long adminUserId = requireActiveAdminUserId();
        try (Connection con = Database.getConnection()) {
            ensureAdminSettingsRow(con, adminUserId);
            try (PreparedStatement ps = con.prepareStatement("SELECT notify_low_stock FROM admin_settings WHERE admin_user_id = ?")) {
                ps.setLong(1, adminUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return true;
                    return rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load settings", e);
        }
    }

    public int getLowStockThreshold() {
        long adminUserId = requireActiveAdminUserId();
        try (Connection con = Database.getConnection()) {
            ensureAdminSettingsRow(con, adminUserId);
            try (PreparedStatement ps = con.prepareStatement("SELECT low_stock_threshold FROM admin_settings WHERE admin_user_id = ?")) {
                ps.setLong(1, adminUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 10;
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load threshold", e);
        }
    }

    public void setNotifyLowStock(boolean notify) {
        long adminUserId = requireActiveAdminUserId();
        try (Connection con = Database.getConnection()) {
            ensureAdminSettingsRow(con, adminUserId);
            try (PreparedStatement ps = con.prepareStatement("UPDATE admin_settings SET notify_low_stock = ?, updated_at = CURRENT_TIMESTAMP WHERE admin_user_id = ?")) {
                ps.setInt(1, notify ? 1 : 0);
                ps.setLong(2, adminUserId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save settings", e);
        }
    }

    public void setLowStockThreshold(int threshold) {
        long adminUserId = requireActiveAdminUserId();
        if (threshold < 0) throw new IllegalArgumentException("Threshold must be >= 0.");
        try (Connection con = Database.getConnection()) {
            ensureAdminSettingsRow(con, adminUserId);
            try (PreparedStatement ps = con.prepareStatement("UPDATE admin_settings SET low_stock_threshold = ?, updated_at = CURRENT_TIMESTAMP WHERE admin_user_id = ?")) {
                ps.setInt(1, threshold);
                ps.setLong(2, adminUserId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save threshold", e);
        }
    }

    public boolean getDarkMode() {
        long adminUserId = requireActiveAdminUserId();
        try (Connection con = Database.getConnection()) {
            ensureAdminSettingsRow(con, adminUserId);
            try (PreparedStatement ps = con.prepareStatement("SELECT dark_mode FROM admin_settings WHERE admin_user_id = ?")) {
                ps.setLong(1, adminUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    return rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load theme setting", e);
        }
    }

    public void setDarkMode(boolean darkMode) {
        long adminUserId = requireActiveAdminUserId();
        try (Connection con = Database.getConnection()) {
            ensureAdminSettingsRow(con, adminUserId);
            try (PreparedStatement ps = con.prepareStatement("UPDATE admin_settings SET dark_mode = ?, updated_at = CURRENT_TIMESTAMP WHERE admin_user_id = ?")) {
                ps.setInt(1, darkMode ? 1 : 0);
                ps.setLong(2, adminUserId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save theme setting", e);
        }
    }

    public List<Medicine> findLowStockMedicines(int threshold) {
        long adminUserId = requireActiveAdminUserId();
        String sql = "SELECT id, name, quantity, price, expiry_date FROM medicines WHERE admin_user_id = ? AND quantity <= ? ORDER BY quantity ASC, name ASC";
        List<Medicine> out = new ArrayList<>();
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, adminUserId);
            ps.setInt(2, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMedicine(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch low-stock medicines", e);
        }
    }

    public boolean hasAnyAdminUser() {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM admin_users");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return false;
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check admin users", e);
        }
    }

    public void registerAdmin(String fullName, String username, String password) {
        String cleanFullName = fullName == null ? "" : fullName.trim();
        String cleanUsername = username == null ? "" : username.trim().toLowerCase();
        if (cleanFullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (cleanUsername.isBlank()) throw new IllegalArgumentException("Username is required.");
        if (cleanUsername.length() < 4) throw new IllegalArgumentException("Username must be at least 4 characters.");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");

        String insertUserSql = "INSERT INTO admin_users(username, password_hash, full_name) VALUES(?, ?, ?)";
        String findUserSql = "SELECT id FROM admin_users WHERE username = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement insertUser = con.prepareStatement(insertUserSql);
             PreparedStatement findUser = con.prepareStatement(findUserSql)) {
            insertUser.setString(1, cleanUsername);
            insertUser.setString(2, hashPassword(password));
            insertUser.setString(3, cleanFullName);
            insertUser.executeUpdate();

            findUser.setString(1, cleanUsername);
            try (ResultSet rs = findUser.executeQuery()) {
                if (rs.next()) {
                    ensureAdminSettingsRow(con, rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("unique")) {
                throw new IllegalArgumentException("Username already exists.");
            }
            throw new RuntimeException("Failed to create admin user", e);
        }
    }

    public AuthenticatedAdmin authenticateAdmin(String username, String password) {
        String cleanUsername = username == null ? "" : username.trim().toLowerCase();
        if (cleanUsername.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username and password are required.");
        }

        String sql = "SELECT id, full_name, password_hash FROM admin_users WHERE username = ?";
        try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cleanUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String storedHash = rs.getString("password_hash");
                if (!verifyPassword(password, storedHash)) return null;
                long id = rs.getLong("id");
                String fullName = rs.getString("full_name");
                ensureAdminSettingsRow(con, id);
                return new AuthenticatedAdmin(id, (fullName == null || fullName.isBlank()) ? cleanUsername : fullName);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to authenticate admin user", e);
        }
    }

    private void changeStock(long medicineId, int qty, String type, String reference, String note) {
        long adminUserId = requireActiveAdminUserId();
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");

        String select = "SELECT quantity FROM medicines WHERE id = ? AND admin_user_id = ?";
        String update = "UPDATE medicines SET quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND admin_user_id = ?";
        String movement = "INSERT INTO stock_movements(medicine_id, type, quantity, reference_no, note, admin_user_id) VALUES(?, ?, ?, ?, ?, ?)";

        Connection con = null;
        try {
            con = Database.getConnection();
            con.setAutoCommit(false);

            int current;
            try (PreparedStatement ps = con.prepareStatement(select)) {
                ps.setLong(1, medicineId);
                ps.setLong(2, adminUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Medicine not found.");
                    current = rs.getInt(1);
                }
            }

            int next = "IN".equals(type) ? current + qty : current - qty;
            if (next < 0) throw new IllegalArgumentException("Insufficient stock.");

            try (PreparedStatement ps = con.prepareStatement(update)) {
                ps.setInt(1, next);
                ps.setLong(2, medicineId);
                ps.setLong(3, adminUserId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(movement)) {
                ps.setLong(1, medicineId);
                ps.setString(2, type);
                ps.setInt(3, qty);
                ps.setString(4, reference == null || reference.isBlank() ? null : reference);
                ps.setString(5, note == null || note.isBlank() ? null : note);
                ps.setLong(6, adminUserId);
                ps.executeUpdate();
            }

            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw new RuntimeException("Failed to update stock", e);
        } catch (RuntimeException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw e;
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private Medicine mapMedicine(ResultSet rs) throws SQLException {
        return new Medicine(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("quantity"),
                rs.getDouble("price"),
                LocalDate.parse(rs.getString("expiry_date"))
        );
    }

    private long requireActiveAdminUserId() {
        if (activeAdminUserId == null || activeAdminUserId <= 0) {
            throw new IllegalStateException("No admin user is logged in.");
        }
        return activeAdminUserId;
    }

    private void ensureAdminSettingsRow(Connection con, long adminUserId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT OR IGNORE INTO admin_settings(admin_user_id, notify_low_stock, low_stock_threshold, dark_mode)
                SELECT ?, notify_low_stock, low_stock_threshold, dark_mode
                FROM app_settings
                WHERE id = 1
                """)) {
            ps.setLong(1, adminUserId);
            ps.executeUpdate();
        }
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) return false;
        String[] parts = storedHash.split(":");
        if (parts.length != 3 || !"v1".equals(parts[0])) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] actualHash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }
}
