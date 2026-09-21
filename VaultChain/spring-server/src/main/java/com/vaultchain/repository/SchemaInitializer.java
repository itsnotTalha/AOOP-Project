package com.vaultchain.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Ordered port of server/src/database/init.js; no ORM and no migration version table. */
public final class SchemaInitializer {
    public void initialize(DataSource source) throws SQLException {
        try (Connection connection = source.getConnection()) {
            initialize(connection);
        }
    }

    // One dedicated connection keeps PRAGMAs and the table-rebuild transaction together.
    void initialize(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Schema initialization requires an auto-commit connection");
        }
        execute(connection, "PRAGMA foreign_keys = ON");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("database/schema.sql"));
        migrateVerificationReports(connection);
        migrateVaultPasswords(connection);
        migrateMarketplaceOwnership(connection);
        migrateDocuments(connection);
        migrateAdminPlatform(connection);
    }

    private void migrateVerificationReports(Connection connection) throws SQLException {
        addColumn(connection, "verification_reports", "user_id",
                "INTEGER REFERENCES users(id) ON DELETE CASCADE");
        execute(connection, """
                UPDATE verification_reports
                SET user_id = (SELECT owner_id FROM assets WHERE assets.id = verification_reports.asset_id)
                WHERE user_id IS NULL
                """);
        if (isNotNull(connection, "verification_reports", "asset_id")) {
            rebuildVerificationReports(connection);
        }
        execute(connection, "CREATE INDEX IF NOT EXISTS idx_verification_reports_user_id ON verification_reports(user_id)");
    }

    private void rebuildVerificationReports(Connection connection) throws SQLException {
        // SQLite cannot toggle foreign_keys inside a transaction.
        execute(connection, "PRAGMA foreign_keys = OFF");
        boolean transactionStarted = false;
        try {
            execute(connection, "BEGIN TRANSACTION");
            transactionStarted = true;
            execute(connection, """
                    CREATE TABLE verification_reports_migrated (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      user_id INTEGER,
                      asset_id INTEGER,
                      verification_type TEXT,
                      sha256_match INTEGER,
                      similarity_score REAL,
                      status TEXT,
                      report_json TEXT,
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
                      FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE SET NULL
                    )
                    """);
            execute(connection, """
                    INSERT INTO verification_reports_migrated
                      (id, user_id, asset_id, verification_type, sha256_match, similarity_score, status, report_json, created_at)
                    SELECT id, user_id, asset_id, verification_type, sha256_match, similarity_score, status, report_json, created_at
                    FROM verification_reports
                    """);
            execute(connection, "DROP TABLE verification_reports");
            execute(connection, "ALTER TABLE verification_reports_migrated RENAME TO verification_reports");
            execute(connection, "COMMIT");
            transactionStarted = false;
        } catch (SQLException failure) {
            if (transactionStarted) {
                try {
                    execute(connection, "ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } finally {
            execute(connection, "PRAGMA foreign_keys = ON");
        }
        // Like Node, the rebuild drops the old table's base indexes. They return
        // when the base schema runs on the next startup; only user_id is added now.
    }

    private void migrateVaultPasswords(Connection connection) throws SQLException {
        addColumn(connection, "vaults", "password_hash", "TEXT");
        addColumn(connection, "vaults", "auto_lock_minutes",
                "INTEGER NOT NULL DEFAULT 10 CHECK(auto_lock_minutes IN (5, 10, 30))");
    }

    private void migrateMarketplaceOwnership(Connection connection) throws SQLException {
        addColumn(connection, "marketplace_listings", "public_reference", "TEXT");
        addColumn(connection, "marketplace_listings", "buyer_id", "INTEGER REFERENCES users(id) ON DELETE SET NULL");
        addColumn(connection, "marketplace_listings", "title", "TEXT");
        addColumn(connection, "marketplace_listings", "description", "TEXT");
        addColumn(connection, "marketplace_listings", "sold_at", "DATETIME");
        execute(connection, """
                UPDATE marketplace_listings SET public_reference = 'ML-' || printf('%06X', id)
                WHERE public_reference IS NULL
                """);
        execute(connection, """
                UPDATE marketplace_listings
                SET title = COALESCE((SELECT title FROM assets WHERE assets.id = marketplace_listings.asset_id), 'Marketplace asset')
                WHERE title IS NULL
                """);
        execute(connection, "UPDATE marketplace_listings SET status = 'cancelled' WHERE status = 'removed'");
        execute(connection, """
                UPDATE marketplace_listings SET status = 'cancelled'
                WHERE status = 'active' AND id NOT IN (
                  SELECT MAX(id) FROM marketplace_listings WHERE status = 'active' GROUP BY asset_id
                )
                """);
        execute(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_marketplace_public_reference ON marketplace_listings(public_reference)");
        execute(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_marketplace_active_asset ON marketplace_listings(asset_id) WHERE status = 'active'");
        addColumn(connection, "ownership_history", "listing_id", "INTEGER REFERENCES marketplace_listings(id) ON DELETE SET NULL");
        addColumn(connection, "ownership_history", "price", "REAL");
        addColumn(connection, "ownership_history", "transaction_reference", "TEXT");
        execute(connection, """
                UPDATE ownership_history SET transaction_reference = 'TX-' || printf('%06X', id)
                WHERE transaction_reference IS NULL
                """);
        execute(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_ownership_transaction_reference ON ownership_history(transaction_reference)");
    }

    private void migrateDocuments(Connection connection) throws SQLException {
        addColumn(connection, "documents", "original_name", "TEXT");
        addColumn(connection, "documents", "stored_name", "TEXT");
        addColumn(connection, "documents", "file_path", "TEXT");
        addColumn(connection, "documents", "mime_type", "TEXT");
        addColumn(connection, "documents", "file_size", "INTEGER");
        addColumn(connection, "documents", "sha256_hash", "TEXT");
        addColumn(connection, "documents", "ocr_status", "TEXT NOT NULL DEFAULT 'pending'");
        addColumn(connection, "documents", "ocr_error", "TEXT");
        addColumn(connection, "documents", "ocr_processed_at", "DATETIME");
        execute(connection, "CREATE INDEX IF NOT EXISTS idx_documents_owner_id ON documents(owner_id)");
    }

    private void migrateAdminPlatform(Connection connection) throws SQLException {
        addColumn(connection, "users", "status", "TEXT NOT NULL DEFAULT 'active'");
        execute(connection, "UPDATE users SET role = UPPER(role) WHERE role IS NOT NULL");
        execute(connection, """
                UPDATE users SET role = 'USER'
                WHERE role IS NULL OR role NOT IN ('SUPER_ADMIN', 'MODERATOR', 'FINANCE_ADMIN', 'VERIFICATION_ADMIN', 'USER')
                """);
        execute(connection, "INSERT OR IGNORE INTO platform_settings (setting_key, setting_value) VALUES ('marketplace_commission_rate', '0.05')");
        execute(connection, "INSERT OR IGNORE INTO platform_settings (setting_key, setting_value) VALUES ('minimum_listing_price', '1')");
        execute(connection, """
                INSERT OR IGNORE INTO marketplace_transactions
                  (transaction_id, asset_id, listing_id, seller_id, buyer_id, sale_amount, platform_fee, seller_amount, status, created_at)
                SELECT oh.transaction_reference, oh.asset_id, oh.listing_id, oh.previous_owner, oh.new_owner, oh.price,
                  ROUND(oh.price * CAST((SELECT setting_value FROM platform_settings WHERE setting_key = 'marketplace_commission_rate') AS REAL), 2),
                  ROUND(oh.price - (oh.price * CAST((SELECT setting_value FROM platform_settings WHERE setting_key = 'marketplace_commission_rate') AS REAL)), 2),
                  'completed', oh.transferred_at
                FROM ownership_history oh
                WHERE oh.transfer_type = 'marketplace_sale' AND oh.transaction_reference IS NOT NULL
                  AND oh.previous_owner IS NOT NULL AND oh.new_owner IS NOT NULL AND oh.price IS NOT NULL
                """);
    }

    private void addColumn(Connection connection, String table, String column, String definition) throws SQLException {
        // Identifiers/definitions come only from constants above, never HTTP input.
        if (!hasColumn(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                if (column.equals(rows.getString("name"))) return true;
            }
            return false;
        }
    }

    private boolean isNotNull(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                if (column.equals(rows.getString("name"))) return rows.getInt("notnull") != 0;
            }
            return false;
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
