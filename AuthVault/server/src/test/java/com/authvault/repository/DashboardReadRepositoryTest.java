package com.authvault.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardReadRepositoryTest {
    @TempDir static Path directory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("authvault.database-path", () -> directory.resolve("dashboard.sqlite").toString());
    }

    @Autowired DashboardReadRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedActivities() {
        jdbc.update("DELETE FROM users WHERE id = 9001");
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES(9001,'Dashboard User','dashboard@test.invalid','unused')");
        jdbc.update("INSERT INTO wallets(id,user_id,balance) VALUES(9001,9001,12.5)");
        jdbc.update("INSERT INTO assets(id,owner_id,title,file_name,file_path) VALUES(5,9001,'Artwork','art.png','unused')");
        jdbc.update("INSERT INTO documents(id,owner_id,original_name,stored_name,file_path,mime_type,file_size,sha256_hash) VALUES(7,9001,'Paper','paper.pdf','unused','application/pdf',1,'unused')");
        jdbc.update("INSERT INTO verification_reports(id,user_id,asset_id,status) VALUES(9001,9001,5,'original')");
        jdbc.update("INSERT INTO wallet_transactions(id,wallet_id,type,amount,reference_id) VALUES(9001,9001,'sale',12.5,'SALE-123')");
        for (String table : List.of("assets", "documents", "verification_reports", "wallet_transactions")) {
            jdbc.update("UPDATE " + table + " SET created_at = '2026-10-01 00:00:00'");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"assets", "documents", "verification_reports", "wallet_transactions"})
    @SuppressWarnings("unchecked")
    void readsMixedActivitiesRegardlessOfFirstRowType(String newestTable) {
        jdbc.update("UPDATE " + newestTable + " SET created_at = '2026-10-02 00:00:00'");

        Map<String, Object> summary = repository.summary(9001);
        List<Map<String, Object>> activities = (List<Map<String, Object>>) summary.get("recentActivity");
        assertThat(activities).hasSize(4);
        assertThat(activities.getFirst()).containsEntry("createdAt", java.sql.Timestamp.valueOf("2026-10-02 00:00:00"));
        Map<String, Object> asset = activity(activities, "asset_upload");
        assertThat(asset).containsEntry("reference", "AV-A000005").containsEntry("amount", null)
                .containsEntry("status", null).containsEntry("documentId", null);
        assertThat(((Number) asset.get("assetId")).longValue()).isEqualTo(5);
        Map<String, Object> document = activity(activities, "document_upload");
        assertThat(document).containsEntry("reference", "DOC-000007").containsEntry("status", "pending");
        assertThat(((Number) document.get("documentId")).longValue()).isEqualTo(7);
        assertThat(activity(activities, "verification")).containsEntry("reference", null).containsEntry("status", "original");
        Map<String, Object> sale = activity(activities, "sale");
        assertThat(sale).containsEntry("reference", "SALE-123").containsEntry("assetId", null);
        assertThat(((Number) sale.get("amount")).doubleValue()).isEqualTo(12.5);
    }

    private Map<String, Object> activity(List<Map<String, Object>> activities, String type) {
        return activities.stream().filter(row -> type.equals(row.get("type"))).findFirst().orElseThrow();
    }
}
