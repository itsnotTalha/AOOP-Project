package com.authvault.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.authvault.config.DatabaseConfiguration;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class SchemaInitializerTest {
    @TempDir Path directory;
    private DataSource source;
    private JdbcTemplate jdbc;
    private final SchemaInitializer initializer = new SchemaInitializer();

    @BeforeEach
    void isolatedDatabase() throws Exception {
        source = DatabaseConfiguration.createDataSource(directory.resolve("schema.sqlite"));
        jdbc = new JdbcTemplate(source);
    }

    @Test
    void initializesAllTablesIndexesAndDefaultSettings() throws Exception {
        initializer.initialize(source);
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", String.class))
                .containsExactlyInAnyOrder("users", "wallets", "wallet_transactions", "assets", "asset_metadata",
                        "asset_hashes", "documents", "ocr_results", "document_verifications", "verification_reports", "blockchain_blocks",
                        "marketplace_listings", "marketplace_transactions", "admin_activity_logs", "platform_settings",
                        "ownership_history", "fractional_ownership", "vault_items", "vaults", "vault_assets",
                        "vault_unlock_sessions", "vault_unlock_attempts", "notifications");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'", Integer.class))
                .isEqualTo(34); // 30 base indexes plus four migration indexes.
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='index'", String.class))
                .contains("idx_document_verifications_user_document", "idx_document_verifications_reference_document",
                        "idx_verification_reports_user_id", "idx_marketplace_active_asset",
                        "idx_marketplace_public_reference", "idx_ownership_transaction_reference");
        assertThat(setting("marketplace_commission_rate")).isEqualTo("0.05");
        assertThat(setting("minimum_listing_price")).isEqualTo("1");
        assertThat(column("verification_reports", "asset_id").get("notnull")).isEqualTo(0);
        assertThat(column("vaults", "auto_lock_minutes").get("dflt_value")).isEqualTo("10");
        assertThat(jdbc.queryForList("PRAGMA foreign_key_check")).isEmpty();
    }

    @Test
    void foreignKeysApplyToEveryIndependentConnection() throws Exception {
        initializer.initialize(source);
        try (var first = source.getConnection(); var second = source.getConnection(); var third = source.getConnection()) {
            for (var connection : List.of(first, second, third)) {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA foreign_keys")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
                assertThatThrownBy(() -> {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("INSERT INTO wallets(user_id) VALUES (999999)");
                    }
                }).isInstanceOf(SQLException.class).hasMessageContaining("FOREIGN KEY");
            }
        }
    }

    @Test
    void reinitializationPreservesAllRowsSequencesAndCustomSettings() throws Exception {
        initializer.initialize(source);
        seedOwnedAsset();
        jdbc.update("INSERT INTO wallets(user_id,balance) VALUES(1,12.345)");
        jdbc.update("INSERT INTO asset_hashes(asset_id,sha256_hash,phash) VALUES(10,'stored-sha','stored-phash')");
        jdbc.update("INSERT INTO asset_metadata(asset_id,metadata_json) VALUES(10,'{\"stored\":true}')");
        jdbc.update("UPDATE platform_settings SET setting_value='0.075' WHERE setting_key='marketplace_commission_rate'");
        jdbc.update("UPDATE platform_settings SET setting_value='25' WHERE setting_key='minimum_listing_price'");
        var rows = snapshot();
        var schema = jdbc.queryForList("SELECT type,name,sql FROM sqlite_master ORDER BY type,name");
        initializer.initialize(source);
        initializer.initialize(DatabaseConfiguration.createDataSource(directory.resolve("schema.sqlite")));
        assertThat(snapshot()).isEqualTo(rows);
        assertThat(jdbc.queryForList("SELECT type,name,sql FROM sqlite_master ORDER BY type,name")).isEqualTo(schema);
    }

    @Test
    void migratesLegacyRowsWithoutLosingReportsCredentialsOrFiles() throws Exception {
        legacyDatabase();
        initializer.initialize(source);
        assertThat(jdbc.queryForMap("SELECT * FROM verification_reports WHERE id=42"))
                .containsEntry("user_id", 1).containsEntry("asset_id", 10)
                .containsEntry("report_json", "{\"keep\":\"unchanged\"}")
                .containsEntry("created_at", "2020-01-02 03:04:05")
                .containsEntry("similarity_score", 98.5);
        assertThat(column("verification_reports", "asset_id").get("notnull")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=1", String.class)).isEqualTo("preserve-this-hash");
        assertThat(jdbc.queryForObject("SELECT file_path FROM assets WHERE id=10", String.class)).isEqualTo("/unchanged/old.png");
        assertThat(jdbc.queryForList("SELECT role FROM users ORDER BY id", String.class)).containsExactly("MODERATOR", "USER", "USER");
        assertThat(jdbc.queryForList("SELECT status FROM users ORDER BY id", String.class)).containsOnly("active");
        assertThat(jdbc.queryForMap("SELECT password_hash,auto_lock_minutes FROM vaults WHERE id=7"))
                .containsEntry("password_hash", null).containsEntry("auto_lock_minutes", 10);
        assertThat(jdbc.queryForMap("SELECT original_name,stored_name,ocr_status,page_count FROM documents WHERE id=20"))
                .containsEntry("original_name", null).containsEntry("stored_name", null)
                .containsEntry("ocr_status", "pending").containsEntry("page_count", 2);
        assertThat(column("documents", "stored_name").get("notnull")).isEqualTo(0);
        jdbc.update("INSERT INTO verification_reports(user_id,asset_id,status) VALUES(1,NULL,'no_match')");
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM verification_reports", Integer.class)).isEqualTo(43);
        jdbc.update("DELETE FROM documents WHERE id=20");
        jdbc.update("DELETE FROM assets WHERE id=10");
        assertThat(jdbc.queryForMap("SELECT asset_id,user_id FROM verification_reports WHERE id=42"))
                .containsEntry("asset_id", null).containsEntry("user_id", 1);
        jdbc.update("DELETE FROM users WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_reports", Integer.class)).isZero();
        assertThat(jdbc.queryForList("PRAGMA foreign_key_check")).isEmpty();
    }

    @Test
    void migratesMarketplaceReferencesAndCancelsDuplicateActiveListings() throws Exception {
        legacyDatabase();
        initializer.initialize(source);
        assertThat(jdbc.queryForList("SELECT public_reference FROM marketplace_listings ORDER BY id", String.class))
                .containsExactly("ML-00000B", "ML-00000C", "ML-00000D");
        assertThat(jdbc.queryForList("SELECT status FROM marketplace_listings ORDER BY id", String.class))
                .containsExactly("cancelled", "active", "cancelled");
        assertThat(jdbc.queryForList("SELECT title FROM marketplace_listings", String.class)).containsOnly("Original title");
        assertThat(jdbc.queryForObject("SELECT transaction_reference FROM ownership_history WHERE id=15", String.class))
                .isEqualTo("TX-00000F");
        assertThat(column("ownership_history", "price")).isNotEmpty();
        assertThat(column("ownership_history", "listing_id")).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketplace_transactions", Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marketplace_listings(asset_id,seller_id,status) VALUES(10,1,'active')"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("UNIQUE");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marketplace_listings(asset_id,seller_id,status,public_reference) VALUES(10,1,'cancelled','ML-00000B')"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("UNIQUE");
    }

    @Test
    void legacyRebuildIndexBehaviorAndRepeatedStartupArePreserved() throws Exception {
        legacyDatabase();
        initializer.initialize(source);
        var rows = snapshot();
        assertThat(indexes("verification_reports")).contains("idx_verification_reports_user_id")
                .doesNotContain("idx_verification_reports_asset_id", "idx_verification_reports_created_at");
        initializer.initialize(source);
        assertThat(indexes("verification_reports")).contains("idx_verification_reports_user_id",
                "idx_verification_reports_asset_id", "idx_verification_reports_created_at");
        assertThat(snapshot()).isEqualTo(rows);
        var schema = jdbc.queryForList("SELECT type,name,sql FROM sqlite_master ORDER BY type,name");
        initializer.initialize(source);
        assertThat(snapshot()).isEqualTo(rows);
        assertThat(jdbc.queryForList("SELECT type,name,sql FROM sqlite_master ORDER BY type,name")).isEqualTo(schema);
    }

    @Test
    void backfillsHistoricalCommissionOnceWithoutTouchingWallets() throws Exception {
        initializer.initialize(source);
        seedOwnedAsset();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES(2,'Buyer','buyer@example.test','hash2')");
        jdbc.update("INSERT INTO wallets(user_id,balance) VALUES(1,7),(2,8)");
        jdbc.update("UPDATE platform_settings SET setting_value='0.075' WHERE setting_key='marketplace_commission_rate'");
        jdbc.update("""
                INSERT INTO ownership_history(asset_id,previous_owner,new_owner,price,transaction_reference,transfer_type,transferred_at)
                VALUES(10,1,2,100,'TX-123456','marketplace_sale','2020-01-02 03:04:05')
                """);
        initializer.initialize(source);
        assertThat(jdbc.queryForMap("SELECT transaction_id,sale_amount,platform_fee,seller_amount,status,created_at FROM marketplace_transactions"))
                .containsEntry("transaction_id", "TX-123456").containsEntry("sale_amount", 100.0)
                .containsEntry("platform_fee", 7.5).containsEntry("seller_amount", 92.5)
                .containsEntry("status", "completed").containsEntry("created_at", "2020-01-02 03:04:05");
        jdbc.update("UPDATE platform_settings SET setting_value='0.2' WHERE setting_key='marketplace_commission_rate'");
        initializer.initialize(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketplace_transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT platform_fee FROM marketplace_transactions", Double.class)).isEqualTo(7.5);
        assertThat(jdbc.queryForList("SELECT balance FROM wallets ORDER BY user_id", Double.class)).containsExactly(7.0, 8.0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions", Integer.class)).isZero();
    }

    @Test
    void preservesExistingRequesterAndOrphanedLegacyReports() throws Exception {
        legacyDatabase();
        jdbc.execute("ALTER TABLE verification_reports ADD COLUMN user_id INTEGER");
        jdbc.update("UPDATE verification_reports SET user_id=2 WHERE id=42");
        jdbc.update("INSERT INTO verification_reports(id,asset_id,report_json) VALUES(50,999,'orphan evidence')");
        initializer.initialize(source);
        assertThat(jdbc.queryForObject("SELECT user_id FROM verification_reports WHERE id=42", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT user_id,asset_id,report_json FROM verification_reports WHERE id=50"))
                .containsEntry("user_id", null).containsEntry("asset_id", 999).containsEntry("report_json", "orphan evidence");
        // Legacy Node keeps old orphans during the FK-disabled rebuild; don't erase evidence.
        assertThat(jdbc.queryForList("PRAGMA foreign_key_check")).hasSize(1);
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedRebuildRollsBackAndRestoresForeignKeysOnTheSameConnection() throws Exception {
        legacyDatabase();
        jdbc.execute("CREATE TABLE verification_reports_migrated(id INTEGER)");
        try (var connection = source.getConnection()) {
            assertThatThrownBy(() -> initializer.initialize(connection)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("already exists");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA foreign_keys")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var statement = connection.createStatement()) {
                statement.execute("BEGIN TRANSACTION");
                statement.execute("ROLLBACK"); // No dangling transaction after failure.
            }
        }
        assertThat(jdbc.queryForObject("SELECT report_json FROM verification_reports WHERE id=42", String.class))
                .isEqualTo("{\"keep\":\"unchanged\"}");
        assertThat(column("verification_reports", "asset_id").get("notnull")).isEqualTo(1);
        jdbc.execute("DROP TABLE verification_reports_migrated");
        initializer.initialize(source);
        assertThat(column("verification_reports", "asset_id").get("notnull")).isEqualTo(0);
    }

    @Test
    void schemaConstraintsAndCascadesAreEnforced() throws Exception {
        initializer.initialize(source);
        seedOwnedAsset();
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role='ADMIN' WHERE id=1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status='disabled' WHERE id=1")).isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO vaults(id,user_id,public_reference,name) VALUES(1,1,'VT-123456','Vault')");
        assertThatThrownBy(() -> jdbc.update("UPDATE vaults SET auto_lock_minutes=7 WHERE id=1")).isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO vault_assets(vault_id,asset_id) VALUES(1,10)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO vault_assets(vault_id,asset_id) VALUES(1,10)"))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO vault_unlock_sessions(vault_id,user_id,token_fingerprint,expires_at) VALUES(1,1,'token','2030-01-01T00:00:00Z')");
        jdbc.update("INSERT INTO vault_unlock_attempts(vault_id,user_id,window_started_at) VALUES(1,1,'2020-01-01T00:00:00Z')");
        jdbc.update("DELETE FROM vaults WHERE id=1");
        for (String table : List.of("vault_assets", "vault_unlock_sessions", "vault_unlock_attempts")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets", Integer.class)).isEqualTo(1);
    }

    private void legacyDatabase() throws Exception {
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("database/legacy.sql"));
        }
    }

    private void seedOwnedAsset() {
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES(1,'Owner','owner@example.test','unchanged-hash')");
        jdbc.update("INSERT INTO assets(id,owner_id,title,file_name,file_path) VALUES(10,1,'Art','art.png','/unchanged/art.png')");
    }

    private String setting(String key) {
        return jdbc.queryForObject("SELECT setting_value FROM platform_settings WHERE setting_key=?", String.class, key);
    }

    private Map<String, Object> column(String table, String name) {
        return jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
                .filter(row -> name.equals(row.get("name"))).findFirst().orElseThrow();
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList("PRAGMA index_list(" + table + ")").stream().map(row -> (String) row.get("name")).toList();
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        for (String table : jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", String.class)) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY rowid"));
        }
        return rows;
    }
}
