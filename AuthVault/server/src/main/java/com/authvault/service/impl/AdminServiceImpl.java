package com.authvault.service.impl;

import com.authvault.entity.*;
import com.authvault.exception.ApiException;
import com.authvault.repository.*;
import com.authvault.security.*;
import com.authvault.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.authvault.security.Role.*;

@Service
public class AdminServiceImpl implements AdminService {
    private final AdminReadRepository db;
    private final UserJpaRepository users;
    private final AssetJpaRepository assets;
    private final MarketplaceListingJpaRepository listings;
    private final PlatformSettingJpaRepository settings;
    private final AdminActivityLogJpaRepository logs;
    private final NotificationJpaRepository notifications;
    private final com.authvault.service.BlockchainService blockchainService;
    private final jakarta.persistence.EntityManager manager;
    private final ObjectMapper json;
    private final Clock clock;
    private static final DateTimeFormatter SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AdminServiceImpl(AdminReadRepository db, UserJpaRepository users, AssetJpaRepository assets,
                            MarketplaceListingJpaRepository listings, PlatformSettingJpaRepository settings,
                            AdminActivityLogJpaRepository logs, NotificationJpaRepository notifications,
                            com.authvault.service.BlockchainService blockchainService,
                            jakarta.persistence.EntityManager manager,
                            ObjectMapper json, Clock clock) {
        this.db = db;
        this.users = users;
        this.assets = assets;
        this.listings = listings;
        this.settings = settings;
        this.logs = logs;
        this.notifications = notifications;
        this.blockchainService = blockchainService;
        this.manager = manager;
        this.json = json;
        this.clock = clock;
    }

    static Map<String, Object> m(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    static double n(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    static double pct(Object a, Object b) {
        return n(b) == 0 ? 0 : Math.round(n(a) / n(b) * 1000) / 10.0;
    }

    static double change(Object a, Object b) {
        return n(b) == 0 ? (n(a) == 0 ? 0 : 100) : Math.round((n(a) - n(b)) / Math.abs(n(b)) * 1000) / 10.0;
    }

    private String now() {
        return LocalDateTime.now(clock).format(SQL);
    }

    private void authorize(CurrentUser user, String section) {
        switch (section) {
            case "users", "logs", "settings" -> requireAny(user, SUPER_ADMIN);
            case "revenue", "transactions" -> requireAny(user, SUPER_ADMIN, FINANCE_ADMIN);
            case "assets", "verification" -> requireAny(user, SUPER_ADMIN, MODERATOR, VERIFICATION_ADMIN);
            case "security", "listings" -> requireAny(user, SUPER_ADMIN, MODERATOR);
            default -> requireAny(user, SUPER_ADMIN, MODERATOR, FINANCE_ADMIN, VERIFICATION_ADMIN);
        }
    }

    record Range(String from, String to, String previousFrom, String previousTo, String resolution) {}

    private LocalDateTime boundary(String input, boolean end) {
        try {
            if (input == null) return null;
            if (input.length() == 10) return LocalDate.parse(input).atTime(end ? LocalTime.of(23, 59, 59) : LocalTime.MIDNIGHT);
            return LocalDateTime.ofInstant(Instant.parse(input), ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Range range(Map<String, String> options) {
        LocalDateTime end = boundary(options.get("to"), true);
        if (end == null) end = LocalDateTime.now(clock);
        LocalDateTime start = boundary(options.get("from"), false);
        String resolution = "day";
        if (start == null) {
            switch (options.getOrDefault("range", "30d")) {
                case "today" -> { start = end.toLocalDate().atStartOfDay(); resolution = "hour"; }
                case "7d" -> start = end.toLocalDate().minusDays(6).atStartOfDay();
                case "1y" -> { start = end.toLocalDate().withDayOfMonth(1).minusMonths(11).atStartOfDay(); resolution = "month"; }
                default -> start = end.toLocalDate().minusDays(29).atStartOfDay();
            }
        }
        long duration = Math.max(3600000, Duration.between(start, end).toMillis());
        if (duration > 62L * 86400000) resolution = "month";
        return new Range(start.format(SQL), end.format(SQL), start.minusNanos(duration * 1000000).format(SQL), start.minusNanos(1000000).format(SQL), resolution);
    }

    private String bucket(Range r) {
        return "strftime('" + switch (r.resolution()) {
            case "hour" -> "%Y-%m-%d %H";
            case "month" -> "%Y-%m";
            default -> "%Y-%m-%d";
        } + "', created_at)";
    }

    private List<Map<String, Object>> grouped(String table, String expression, String condition, Range r) {
        return db.rows("SELECT " + bucket(r) + " AS bucket, " + expression + " AS value FROM " + table + " WHERE created_at BETWEEN ? AND ? " + condition + " GROUP BY bucket ORDER BY bucket", r.from(), r.to());
    }

    private List<Map<String, Object>> timeline(Range r, Map<String, List<Map<String, Object>>> series) {
        LocalDateTime cursor = LocalDateTime.parse(r.from(), SQL), end = LocalDateTime.parse(r.to(), SQL);
        if (r.resolution().equals("day")) cursor = cursor.toLocalDate().atStartOfDay();
        if (r.resolution().equals("month")) cursor = cursor.toLocalDate().withDayOfMonth(1).atStartOfDay();
        String keyPattern = switch (r.resolution()) { case "hour" -> "yyyy-MM-dd HH"; case "month" -> "yyyy-MM"; default -> "yyyy-MM-dd"; };
        String labelPattern = switch (r.resolution()) { case "hour" -> "h a"; case "month" -> "MMM yy"; default -> "MMM d"; };
        Map<String, Map<String, Object>> lookup = new LinkedHashMap<>();
        series.forEach((name, rows) -> {
            Map<String, Object> values = new HashMap<>();
            rows.forEach(row -> values.put((String) row.get("bucket"), row.get("value")));
            lookup.put(name, values);
        });
        List<Map<String, Object>> points = new ArrayList<>();
        while (!cursor.isAfter(end) && points.size() < 400) {
            String key = cursor.format(DateTimeFormatter.ofPattern(keyPattern));
            Map<String, Object> point = m("key", key, "label", cursor.format(DateTimeFormatter.ofPattern(labelPattern, Locale.ENGLISH)));
            lookup.forEach((name, values) -> point.put(name, values.getOrDefault(key, 0)));
            points.add(point);
            cursor = switch (r.resolution()) { case "hour" -> cursor.plusHours(1); case "month" -> cursor.plusMonths(1); default -> cursor.plusDays(1); };
        }
        return points;
    }

    private double rate() {
        return settings.findById("marketplace_commission_rate").map(v -> Double.parseDouble(v.getSettingValue())).orElse(0.0);
    }

    @Override
    @Transactional(readOnly = true)
    public Object read(CurrentUser user, String section, Map<String, String> options) {
        authorize(user, section);
        Range r = range(options);
        return switch (section) {
            case "transactions" -> transactions(r);
            case "revenue" -> revenue(r);
            case "marketplace" -> marketplace(r);
            case "users" -> users(r);
            case "assets" -> assets(r);
            case "verification" -> verification(r);
            case "analytics" -> analytics(r);
            case "overview" -> overview(user, r);
            case "security" -> security();
            case "logs" -> activity();
            case "notifications" -> db.rows("SELECT id, title, message, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 20", user.id());
            case "settings" -> {
                Map<String, Object> result = new LinkedHashMap<>();
                settings.findAll().forEach(s -> result.put(s.getSettingKey(), m("value", s.getSettingValue(), "updatedAt", s.getUpdatedAt())));
                yield result;
            }
            default -> throw new ApiException(404, "Not Found");
        };
    }

    private Map<String, Object> transactions(Range r) {
        Map<String, Object> summary = db.one("SELECT COUNT(*) AS total, COALESCE(SUM(sale_amount), 0) AS gross_volume, SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed, SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending, SUM(CASE WHEN status = 'refunded' THEN 1 ELSE 0 END) AS refunded, COALESCE(SUM(CASE WHEN status = 'refunded' THEN sale_amount ELSE 0 END), 0) AS refunded_value FROM marketplace_transactions WHERE created_at BETWEEN ? AND ?", r.from(), r.to());
        summary.put("successRate", pct(summary.get("completed"), summary.get("total")));
        return m("range", r, "summary", summary, "rows", db.rows("SELECT mt.transaction_id, a.title AS asset, seller.full_name AS seller, buyer.full_name AS buyer, mt.sale_amount, mt.platform_fee, mt.seller_amount, mt.status, mt.created_at FROM marketplace_transactions mt JOIN assets a ON a.id = mt.asset_id JOIN users seller ON seller.id = mt.seller_id JOIN users buyer ON buyer.id = mt.buyer_id WHERE mt.created_at BETWEEN ? AND ? ORDER BY mt.created_at DESC", r.from(), r.to()));
    }

    private Map<String, Object> revenueSummary(String from, String to) {
        return db.one("SELECT COALESCE(SUM(CASE WHEN status = 'completed' THEN platform_fee ELSE 0 END), 0) AS revenue, COALESCE(SUM(CASE WHEN status = 'pending' THEN platform_fee ELSE 0 END), 0) AS pending, COALESCE(SUM(CASE WHEN status = 'refunded' THEN platform_fee ELSE 0 END), 0) AS refunds, SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed, COUNT(*) AS total FROM marketplace_transactions WHERE created_at BETWEEN ? AND ?", from, to);
    }

    private List<Map<String, Object>> salesTrend(Range r) {
        return timeline(r, mSeries("revenue", grouped("marketplace_transactions", "COALESCE(SUM(platform_fee), 0)", "AND status = 'completed'", r),
                "transactions", grouped("marketplace_transactions", "COUNT(*)", "AND status = 'completed'", r)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> mSeries(Object... pairs) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], (List<Map<String, Object>>) pairs[i + 1]);
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstRows(Map<String, Object> report, int count) {
        return ((List<Map<String, Object>>) report.get("rows")).stream().limit(count).toList();
    }

    private Map<String, Object> revenue(Range r) {
        var s = revenueSummary(r.from(), r.to());
        var p = revenueSummary(r.previousFrom(), r.previousTo());
        return m("range", r, "summary", m(
                "totalRevenue", s.get("revenue"),
                "marketplaceFees", s.get("revenue"),
                "pendingPayments", s.get("pending"),
                "refunds", s.get("refunds"),
                "completedTransactions", n(s.get("completed")),
                "totalTransactions", s.get("total"),
                "commissionRate", rate() * 100
        ), "changes", m(
                "revenue", change(s.get("revenue"), p.get("revenue")),
                "transactions", change(s.get("completed"), p.get("completed"))
        ), "sources", n(s.get("revenue")) == 0 ? List.of() : List.of(m("name", "Marketplace commission", "amount", s.get("revenue"), "percentage", 100)),
                "trend", salesTrend(r), "transactions", firstRows(transactions(r), 5));
    }

    private Map<String, Object> marketplace(Range r) {
        var s = db.one("SELECT COUNT(*) AS total, SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END) AS active, SUM(CASE WHEN status = 'sold' THEN 1 ELSE 0 END) AS sold, COALESCE(AVG(CASE WHEN status = 'sold' THEN price END), 0) AS average_price FROM marketplace_listings WHERE created_at BETWEEN ? AND ?", r.from(), r.to());
        var rows = db.rows("SELECT ml.id, ml.public_reference, COALESCE(ml.title, a.title) AS asset, u.full_name AS owner, ml.price, ml.status, ml.created_at, ml.sold_at, COALESCE(mt.platform_fee, 0) AS platform_fee FROM marketplace_listings ml JOIN assets a ON a.id = ml.asset_id JOIN users u ON u.id = ml.seller_id LEFT JOIN marketplace_transactions mt ON mt.listing_id = ml.id WHERE ml.created_at BETWEEN ? AND ? ORDER BY ml.created_at DESC", r.from(), r.to());
        double rate = rate();
        rows.forEach(row -> {
            if (n(row.get("platform_fee")) == 0) row.put("platform_fee", n(row.get("price")) * rate);
        });
        return m("range", r, "summary", m(
                "totalListings", s.get("total"),
                "activeListings", n(s.get("active")),
                "soldAssets", n(s.get("sold")),
                "averageSellingPrice", s.get("average_price"),
                "commissionEarned", revenueSummary(r.from(), r.to()).get("revenue")
        ), "rows", rows);
    }

    private Map<String, Object> users(Range r) {
        return m("range", r, "summary", db.one("SELECT COUNT(*) AS total, SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END) AS active, SUM(CASE WHEN role != 'USER' THEN 1 ELSE 0 END) AS admins, SUM(CASE WHEN status = 'review' THEN 1 ELSE 0 END) AS review, SUM(CASE WHEN created_at BETWEEN ? AND ? THEN 1 ELSE 0 END) AS new_users FROM users", r.from(), r.to()),
                "rows", db.rows("SELECT u.id, u.full_name, u.email, u.role, u.status, u.created_at, (SELECT COUNT(*) FROM assets a WHERE a.owner_id = u.id) AS assets, (SELECT COUNT(*) FROM marketplace_transactions mt WHERE mt.seller_id = u.id OR mt.buyer_id = u.id) AS transactions, (SELECT COALESCE(SUM(mt.platform_fee), 0) FROM marketplace_transactions mt WHERE mt.seller_id = u.id) AS revenue_generated FROM users u ORDER BY u.created_at DESC"));
    }

    private Map<String, Object> assets(Range r) {
        var s = db.one("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN EXISTS(SELECT 1 FROM verification_reports vr WHERE vr.asset_id = a.id) THEN 1 ELSE 0 END), 0) AS verified, COALESCE(SUM(CASE WHEN LOWER(a.status) IN ('suspicious', 'review') THEN 1 ELSE 0 END), 0) AS suspicious, COALESCE(SUM(CASE WHEN NOT EXISTS(SELECT 1 FROM verification_reports vr WHERE vr.asset_id = a.id) THEN 1 ELSE 0 END), 0) AS pending FROM assets a WHERE a.created_at BETWEEN ? AND ?", r.from(), r.to());
        return m("range", r, "summary", s, "rows", db.rows("SELECT a.id, a.title, a.category, a.status AS asset_status, a.created_at, u.full_name AS owner, (SELECT MAX(CASE WHEN vr.sha256_match = 1 THEN 100 ELSE vr.similarity_score END) FROM verification_reports vr WHERE vr.asset_id = a.id) AS verification_score, (SELECT ml.status FROM marketplace_listings ml WHERE ml.asset_id = a.id ORDER BY ml.created_at DESC LIMIT 1) AS marketplace_status, EXISTS(SELECT 1 FROM verification_reports vr WHERE vr.asset_id = a.id) AS has_verification FROM assets a JOIN users u ON u.id = a.owner_id WHERE a.created_at BETWEEN ? AND ? ORDER BY a.created_at DESC", r.from(), r.to()));
    }

    private Double score(Map<String, Object> row) {
        if (n(row.get("sha256_match")) != 0) return 100.0;
        if (row.get("similarity_score") instanceof Number number) return number.doubleValue();
        try {
            var match = json.readTree(String.valueOf(row.get("report_json"))).path("matches").path(0);
            double bits = match.path("hashBits").asDouble();
            if (bits != 0) return Math.max(0, Math.round((1 - match.path("distance").asDouble() / bits) * 1000) / 10.0);
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> verification(Range r) {
        var rows = db.rows("SELECT sha256_match, similarity_score, status, report_json FROM verification_reports WHERE created_at BETWEEN ? AND ?", r.from(), r.to());
        int successful = 0, duplicates = 0, rejected = 0, scored = 0;
        int[] counts = new int[4];
        for (var row : rows) {
            String status = String.valueOf(row.get("status"));
            if (!Set.of("failed", "rejected").contains(status)) successful++;
            if (status.equals("rejected")) rejected++;
            if (n(row.get("sha256_match")) != 0) duplicates++;
            Double sc = score(row);
            if (sc != null) {
                scored++;
                counts[sc < 80 ? 0 : sc < 90 ? 1 : sc < 95 ? 2 : 3]++;
            }
        }
        var trend = timeline(r, mSeries("total", grouped("verification_reports", "COUNT(*)", "", r),
                "successful", grouped("verification_reports", "SUM(CASE WHEN status NOT IN ('failed', 'rejected') THEN 1 ELSE 0 END)", "", r)));
        trend.forEach(p -> p.put("rate", pct(p.get("successful"), p.get("total"))));
        List<Map<String, Object>> disputes;
        try {
            disputes = db.rows("""
                SELECT d.id, d.dispute_reference, d.verification_reference, d.asset_id, d.claimant_id, d.registered_owner_id,
                       d.reason, d.evidence, d.contact_email, d.match_type, d.confidence, d.status, d.admin_notes,
                       d.created_at, d.updated_at,
                       c.full_name AS claimant_name, c.email AS claimant_email,
                       o.full_name AS owner_name, o.email AS owner_email,
                       a.title AS asset_title, a.status AS asset_status
                FROM asset_disputes d
                JOIN users c ON c.id = d.claimant_id
                JOIN users o ON o.id = d.registered_owner_id
                JOIN assets a ON a.id = d.asset_id
                ORDER BY d.created_at DESC
            """);
        } catch (Exception e) {
            disputes = List.of();
        }
        long totalDisputes = disputes.size();
        long pendingDisputes = disputes.stream().filter(d -> "pending".equals(d.get("status")) || "under_review".equals(d.get("status"))).count();
        var summary = m("total", rows.size(), "successful", successful, "duplicates", duplicates, "rejected", rejected, "scored", scored,
                "totalDisputes", totalDisputes, "pendingDisputes", pendingDisputes);
        return m("range", r, "summary", summary, "disputes", disputes,
                "trend", trend, "confidence", List.of(
                        m("range", "<80%", "count", counts[0]),
                        m("range", "80–90%", "count", counts[1]),
                        m("range", "90–95%", "count", counts[2]),
                        m("range", "95–100%", "count", counts[3])
                ));
    }

    private Map<String, Object> analytics(Range r) {
        var usersSeries = grouped("users", "COUNT(*)", "", r);
        var assetsSeries = grouped("assets", "COUNT(*)", "", r);
        var transSeries = grouped("marketplace_transactions", "COUNT(*)", "AND status = 'completed'", r);
        var revSeries = grouped("marketplace_transactions", "COALESCE(SUM(platform_fee), 0)", "AND status = 'completed'", r);
        var priorCounts = db.one("SELECT (SELECT COUNT(*) FROM users WHERE created_at < ?) AS users, (SELECT COUNT(*) FROM assets WHERE created_at < ?) AS assets", r.from(), r.from());
        var totals = db.one("SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM assets) AS assets, (SELECT COUNT(DISTINCT asset_id) FROM verification_reports WHERE asset_id IS NOT NULL) AS verified, (SELECT COUNT(*) FROM marketplace_listings) AS listings, (SELECT COUNT(*) FROM marketplace_listings WHERE status = 'sold') AS sold, (SELECT COALESCE(SUM(sale_amount), 0) FROM marketplace_transactions WHERE status = 'completed' AND created_at BETWEEN ? AND ?) AS gmv", r.from(), r.to());
        var activeUsers = db.one("SELECT COUNT(DISTINCT user_id) AS count FROM (SELECT owner_id AS user_id FROM assets WHERE created_at BETWEEN ? AND ? UNION SELECT user_id FROM verification_reports WHERE created_at BETWEEN ? AND ? UNION SELECT seller_id FROM marketplace_transactions WHERE created_at BETWEEN ? AND ? UNION SELECT buyer_id FROM marketplace_transactions WHERE created_at BETWEEN ? AND ?)", r.from(), r.to(), r.from(), r.to(), r.from(), r.to(), r.from(), r.to());
        var volumeRows = db.rows("SELECT " + bucket(r) + " AS bucket, COALESCE(SUM(sale_amount), 0) AS value FROM marketplace_transactions WHERE status = 'completed' AND created_at BETWEEN ? AND ? GROUP BY bucket", r.from(), r.to());
        var payment = db.one("SELECT COUNT(*) AS total, SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed FROM marketplace_transactions WHERE created_at BETWEEN ? AND ?", r.from(), r.to());

        Map<String, Object> volumeMap = new HashMap<>();
        for (var row : volumeRows) volumeMap.put((String) row.get("bucket"), row.get("value"));

        var tl = timeline(r, mSeries("newUsers", usersSeries, "newAssets", assetsSeries, "transactions", transSeries, "revenue", revSeries));
        long cumulativeUsers = ((Number) priorCounts.getOrDefault("users", 0)).longValue();
        long cumulativeAssets = ((Number) priorCounts.getOrDefault("assets", 0)).longValue();
        long newAssetsSum = 0;
        for (var p : tl) {
            long nu = ((Number) p.getOrDefault("newUsers", 0)).longValue();
            long na = ((Number) p.getOrDefault("newAssets", 0)).longValue();
            cumulativeUsers += nu;
            cumulativeAssets += na;
            newAssetsSum += na;
            p.put("users", cumulativeUsers);
            p.put("assets", cumulativeAssets);
            p.put("volume", volumeMap.getOrDefault(p.get("key"), 0));
        }

        double totalAssets = n(totals.get("assets"));
        double totalListings = n(totals.get("listings"));
        double totalUsers = n(totals.get("users"));
        double paymentTotal = n(payment.get("total"));

        List<Map<String, Object>> performance = List.of(
                m("metric", "Verification", "value", totalAssets > 0 ? Math.round(n(totals.get("verified")) / totalAssets * 100) : 0),
                m("metric", "Sell-through", "value", totalListings > 0 ? Math.round(n(totals.get("sold")) / totalListings * 100) : 0),
                m("metric", "Payment success", "value", paymentTotal > 0 ? Math.round(n(payment.get("completed")) / paymentTotal * 100) : 0),
                m("metric", "Active users", "value", totalUsers > 0 ? Math.round(n(activeUsers.get("count")) / totalUsers * 100) : 0)
        );

        return m("range", r, "summary", m("monthlyActiveUsers", activeUsers.get("count"), "newAssets", newAssetsSum, "grossVolume", totals.get("gmv"), "activeRegions", null),
                "timeline", tl, "performance", performance, "regions", List.of(), "geographyAvailable", false);
    }

    private List<Map<String, Object>> activity() {
        return db.rows("SELECT l.id, u.full_name AS admin, l.action, l.target_type, l.target_id, l.details_json, l.ip_address, l.created_at FROM admin_activity_logs l JOIN users u ON u.id = l.admin_id ORDER BY l.created_at DESC LIMIT 250");
    }

    private Map<String, Object> security() {
        long start = System.currentTimeMillis();
        db.one("SELECT 1 AS healthy");
        long latency = System.currentTimeMillis() - start;

        var stats = db.one("SELECT (SELECT COUNT(*) FROM vault_unlock_sessions WHERE expires_at > CURRENT_TIMESTAMP) AS active_sessions, (SELECT COUNT(*) FROM users WHERE status = 'review') AS risky_accounts, (SELECT COUNT(*) FROM vault_unlock_attempts WHERE blocked_until > CURRENT_TIMESTAMP) AS blocked_threats");
        var attempts = db.rows("SELECT vua.updated_at AS created_at, vua.attempt_count, vua.blocked_until, u.email FROM vault_unlock_attempts vua JOIN users u ON u.id = vua.user_id WHERE vua.attempt_count > 0 ORDER BY vua.updated_at DESC LIMIT 50");
        var actLogs = activity();

        List<Map<String, Object>> events = new ArrayList<>();
        for (var row : attempts) {
            boolean blocked = row.get("blocked_until") != null;
            long count = ((Number) row.getOrDefault("attempt_count", 0)).longValue();
            events.add(m(
                    "time", row.get("created_at"),
                    "title", blocked ? "Vault access temporarily blocked" : "Failed Vault unlock attempts",
                    "detail", count + " attempt" + (count == 1 ? "" : "s") + " · " + row.get("email"),
                    "level", blocked ? "high" : "medium",
                    "type", "Vault access"
            ));
        }
        for (var row : actLogs) {
            String act = String.valueOf(row.get("action"));
            if (List.of("updated_user_access", "changed_commission_rate", "updated_marketplace_settings").contains(act)) {
                String targetType = row.get("target_type") != null ? String.valueOf(row.get("target_type")) : "system";
                String targetId = row.get("target_id") != null ? String.valueOf(row.get("target_id")) : "";
                events.add(m(
                        "time", row.get("created_at"),
                        "title", act.replace('_', ' '),
                        "detail", row.get("admin") + " · " + targetType + " " + targetId,
                        "level", "low",
                        "type", "Admin"
                ));
            }
        }
        events.sort((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time"))));
        if (events.size() > 50) events = new ArrayList<>(events.subList(0, 50));

        double blocked = n(stats.get("blocked_threats"));
        double risky = n(stats.get("risky_accounts"));
        double score = Math.max(0, 100 - blocked * 5 - risky * 2);

        long uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        List<Map<String, Object>> controls = List.of(
                m("name", "JWT authentication", "detail", "Required on protected API routes", "status", "Active"),
                m("name", "Admin audit logging", "detail", "Privileged mutations are recorded", "status", "Active"),
                m("name", "Vault password protection", "detail", "Rate-limited bcrypt verification", "status", "Active"),
                m("name", "Database at-rest encryption", "detail", "No encryption provider configured", "status", "Not configured")
        );

        return m(
                "summary", m("securityScore", score, "blockedThreats", stats.get("blocked_threats"), "activeSessions", stats.get("active_sessions"), "riskyAccounts", stats.get("risky_accounts")),
                "health", m("databaseLatencyMs", latency, "processUptimeSeconds", uptime, "database", "operational"),
                "events", events,
                "controls", controls,
                "riskySessions", List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> overview(CurrentUser user, Range r) {
        var totals = db.one("SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM assets) AS assets, (SELECT COUNT(DISTINCT asset_id) FROM verification_reports WHERE asset_id IS NOT NULL) AS verified_assets");
        var current = db.one("SELECT (SELECT COUNT(*) FROM users WHERE created_at BETWEEN ? AND ?) AS users, (SELECT COUNT(*) FROM assets WHERE created_at BETWEEN ? AND ?) AS assets", r.from(), r.to(), r.from(), r.to());
        var prior = db.one("SELECT (SELECT COUNT(*) FROM users WHERE created_at BETWEEN ? AND ?) AS users, (SELECT COUNT(*) FROM assets WHERE created_at BETWEEN ? AND ?) AS assets", r.previousFrom(), r.previousTo(), r.previousFrom(), r.previousTo());
        var revenueRows = grouped("marketplace_transactions", "COALESCE(SUM(platform_fee), 0)", "AND status = 'completed'", r);
        var transRows = grouped("marketplace_transactions", "COUNT(*)", "AND status = 'completed'", r);
        var sales = db.one("SELECT COUNT(*) AS count, COALESCE(SUM(platform_fee), 0) AS revenue, COALESCE(SUM(sale_amount), 0) AS gross FROM marketplace_transactions WHERE status = 'completed' AND created_at BETWEEN ? AND ?", r.from(), r.to());
        var priorSales = db.one("SELECT COUNT(*) AS count, COALESCE(SUM(platform_fee), 0) AS revenue FROM marketplace_transactions WHERE status = 'completed' AND created_at BETWEEN ? AND ?", r.previousFrom(), r.previousTo());
        var tx = transactions(r);
        var operational = db.one("SELECT (SELECT COUNT(*) FROM marketplace_listings WHERE status = 'active') AS active_listings, (SELECT COUNT(*) FROM assets a WHERE NOT EXISTS (SELECT 1 FROM verification_reports vr WHERE vr.asset_id = a.id)) AS verification_queue, (SELECT COUNT(*) FROM users WHERE status = 'review') AS flagged_accounts, (SELECT COUNT(*) FROM marketplace_transactions WHERE created_at BETWEEN ? AND ?) AS payments, (SELECT COUNT(*) FROM marketplace_transactions WHERE status = 'completed' AND created_at BETWEEN ? AND ?) AS successful_payments", r.from(), r.to(), r.from(), r.to());
        var sec = security();
        double commRate = rate() * 100;

        double totalAssets = n(totals.get("assets"));
        double verifiedAssets = n(totals.get("verified_assets"));

        var health = new LinkedHashMap<String, Object>((Map<String, Object>) sec.get("health"));
        health.put("activeListings", operational.get("active_listings"));
        health.put("verificationQueue", operational.get("verification_queue"));
        health.put("flaggedAccounts", operational.get("flagged_accounts"));
        health.put("paymentSuccess", pct(operational.get("successful_payments"), operational.get("payments")));

        List<Map<String, Object>> secEvents = (List<Map<String, Object>>) sec.get("events");
        boolean canSeeSecurity = "SUPER_ADMIN".equalsIgnoreCase(user.role()) || "MODERATOR".equalsIgnoreCase(user.role());
        List<Map<String, Object>> userSecEvents = canSeeSecurity ? secEvents.stream().limit(3).toList() : List.of();

        return m(
                "range", r,
                "totals", m("users", totals.get("users"), "assets", totals.get("assets"), "verifiedAssets", totals.get("verified_assets"), "verificationRate", totalAssets > 0 ? Math.round(verifiedAssets / totalAssets * 1000) / 10.0 : 0.0),
                "period", m("newUsers", current.get("users"), "newAssets", current.get("assets"), "transactions", sales.get("count"), "grossVolume", sales.get("gross"), "marketplaceRevenue", sales.get("revenue")),
                "changes", m("users", change(current.get("users"), prior.get("users")), "assets", change(current.get("assets"), prior.get("assets")), "transactions", change(sales.get("count"), priorSales.get("count")), "revenue", change(sales.get("revenue"), priorSales.get("revenue"))),
                "trend", timeline(r, mSeries("revenue", revenueRows, "transactions", transRows)),
                "recentTransactions", firstRows(tx, 4),
                "health", health,
                "securityEvents", userSecEvents,
                "commissionRate", commRate
        );
    }

    @Override
    @Transactional
    public void markNotificationsRead(CurrentUser user) {
        notifications.markAllReadByUserId(user.id());
    }

    @Override
    @Transactional
    public Object update(CurrentUser user, String section, Long id, Map<String, Object> body, String address) {
        return switch (section) {
            case "settings" -> {
                requireAny(user, SUPER_ADMIN);
                double commission = n(body.get("commissionPercentage"));
                double minPrice = n(body.get("minimumListingPrice"));
                if (commission < 0 || commission > 20) {
                    throw new ApiException(400, "Commission must be between 0% and 20%");
                }
                if (minPrice <= 0 || minPrice > 1_000_000_000) {
                    throw new ApiException(400, "Minimum listing price must be a positive number");
                }
                saveSetting("marketplace_commission_rate", String.valueOf(commission / 100.0), user.id());
                saveSetting("minimum_listing_price", String.valueOf(minPrice), user.id());
                logAction(user.id(), "updated_marketplace_settings", "platform_settings", "marketplace",
                        m("commissionPercentage", commission, "minimumListingPrice", minPrice), address);
                yield m("commissionPercentage", commission, "minimumListingPrice", minPrice);
            }
            case "users" -> {
                requireAny(user, SUPER_ADMIN);
                String role = body.get("role") != null ? String.valueOf(body.get("role")).toUpperCase() : null;
                String status = body.get("status") != null ? String.valueOf(body.get("status")).toLowerCase() : null;
                if (role != null && !Set.of("SUPER_ADMIN", "MODERATOR", "FINANCE_ADMIN", "VERIFICATION_ADMIN", "USER").contains(role)) {
                    throw new ApiException(400, "Invalid role");
                }
                if (status != null && !Set.of("active", "suspended", "review").contains(status)) {
                    throw new ApiException(400, "Invalid status");
                }
                if (id != null && user.id() == id && ("suspended".equals(status) || "USER".equals(role))) {
                    throw new ApiException(400, "You cannot remove your own admin access");
                }
                var target = users.findById(id).orElseThrow(() -> new ApiException(404, "User not found"));
                if (role != null) target.setRole(role);
                if (status != null) target.setStatus(status);
                target.setUpdatedAt(now());
                users.save(target);
                logAction(user.id(), "updated_user_access", "user", String.valueOf(id),
                        m("role", role, "status", status), address);
                yield m("id", target.getId(), "fullName", target.getFullName(), "email", target.getEmail(), "role", target.getRole(), "status", target.getStatus());
            }
            case "listings" -> {
                requireAny(user, SUPER_ADMIN, MODERATOR);
                String status = body.get("status") != null ? String.valueOf(body.get("status")).toLowerCase() : "";
                if (!Set.of("active", "cancelled").contains(status)) {
                    throw new ApiException(400, "Invalid listing status");
                }
                var listing = listings.findById(id).orElseThrow(() -> new ApiException(404, "Listing not found"));
                if (!Set.of("active", "cancelled").contains(listing.getStatus())) {
                    throw new ApiException(404, "Listing not found");
                }
                listing.setStatus(status);
                listings.save(listing);
                logAction(user.id(), "updated_listing_status", "listing", listing.getPublicReference(),
                        m("status", status), address);
                yield m("id", listing.getId(), "public_reference", listing.getPublicReference(), "status", listing.getStatus());
            }
            case "assets" -> {
                requireAny(user, SUPER_ADMIN, MODERATOR, VERIFICATION_ADMIN);
                String status = body.get("status") != null ? String.valueOf(body.get("status")).toLowerCase() : "";
                if (!Set.of("active", "review", "suspicious", "suspended").contains(status)) {
                    throw new ApiException(400, "Invalid asset status");
                }
                var asset = assets.findById(id).orElseThrow(() -> new ApiException(404, "Asset not found"));
                asset.setStatus(status);
                asset.setUpdatedAt(now());
                assets.save(asset);
                logAction(user.id(), "updated_asset_status", "asset", String.valueOf(id),
                        m("status", status), address);
                yield m("id", asset.getId(), "title", asset.getTitle(), "status", asset.getStatus());
            }
            case "disputes" -> {
                requireAny(user, SUPER_ADMIN, MODERATOR, VERIFICATION_ADMIN);
                String status = body.get("status") != null ? String.valueOf(body.get("status")).toLowerCase() : "";
                if (!Set.of("pending", "under_review", "resolved_transferred", "resolved_removed", "rejected").contains(status)) {
                    throw new ApiException(400, "Invalid dispute status: " + status);
                }
                String adminNotes = body.get("adminNotes") != null ? String.valueOf(body.get("adminNotes")).trim() : "";
                var dList = db.rows("SELECT * FROM asset_disputes WHERE id = ?", id);
                if (dList.isEmpty()) throw new ApiException(404, "Dispute not found");
                var dispute = dList.getFirst();
                long assetId = ((Number) dispute.get("asset_id")).longValue();
                long claimantId = ((Number) dispute.get("claimant_id")).longValue();
                long ownerId = ((Number) dispute.get("registered_owner_id")).longValue();
                String disputeRef = String.valueOf(dispute.get("dispute_reference"));

                Asset a = assets.findById(assetId).orElse(null);

                if ("resolved_transferred".equals(status) && a != null) {
                    a.setOwnerId(claimantId);
                    a.setStatus("active");
                    a.setUpdatedAt(now());
                    assets.saveAndFlush(a);

                    manager.createNativeQuery("UPDATE marketplace_listings SET status = 'cancelled' WHERE asset_id = :assetId AND status = 'active'")
                            .setParameter("assetId", assetId)
                            .executeUpdate();

                    manager.createNativeQuery("DELETE FROM vault_assets WHERE asset_id = :assetId AND vault_id IN (SELECT id FROM vaults WHERE user_id = :oldOwner)")
                            .setParameter("assetId", assetId)
                            .setParameter("oldOwner", ownerId)
                            .executeUpdate();

                    blockchainService.recordBlock(assetId, claimantId, "DISPUTE_TRANSFER",
                            "Dispute " + disputeRef + " resolved by Admin #" + user.id() + ". Asset ownership transferred to verified creator User #" + claimantId);

                    notifyUser(claimantId, "Ownership Dispute Approved: " + disputeRef,
                            "Your ownership claim for asset '" + a.getTitle() + "' was approved by administrators. The asset has been transferred to your vault.");

                    notifyUser(ownerId, "Ownership Dispute Notice: " + disputeRef,
                            "An administrative dispute review determined prior authentic creation for asset '" + a.getTitle() + "'. Ownership has been reassigned to User #" + claimantId + ".");
                } else if ("resolved_removed".equals(status) && a != null) {
                    a.setStatus("suspended");
                    a.setUpdatedAt(now());
                    assets.saveAndFlush(a);

                    manager.createNativeQuery("UPDATE marketplace_listings SET status = 'cancelled' WHERE asset_id = :assetId AND status = 'active'")
                            .setParameter("assetId", assetId)
                            .executeUpdate();

                    notifyUser(claimantId, "Dispute Resolved: " + disputeRef,
                            "Your report for asset '" + a.getTitle() + "' was confirmed. The reported asset has been removed from the platform.");
                    notifyUser(ownerId, "Asset Suspended: " + disputeRef,
                            "Your asset '" + a.getTitle() + "' has been suspended following an administrative ownership dispute.");
                } else if ("rejected".equals(status) && a != null) {
                    notifyUser(claimantId, "Dispute Dismissed: " + disputeRef,
                            "Your ownership claim for asset '" + a.getTitle() + "' was reviewed and dismissed. Notes: " + (adminNotes.isEmpty() ? "Insufficient prior ownership evidence." : adminNotes));
                }

                manager.createNativeQuery("UPDATE asset_disputes SET status = :status, admin_notes = :notes, reviewed_by = :adminId, updated_at = :now WHERE id = :id")
                        .setParameter("status", status)
                        .setParameter("notes", adminNotes)
                        .setParameter("adminId", user.id())
                        .setParameter("now", now())
                        .setParameter("id", id)
                        .executeUpdate();

                logAction(user.id(), "resolved_asset_dispute", "asset_dispute", disputeRef,
                        m("status", status, "adminNotes", adminNotes, "assetId", assetId), address);

                yield m("id", id, "disputeReference", disputeRef, "status", status, "adminNotes", adminNotes);
            }
            default -> throw new ApiException(404, "Not Found");
        };
    }

    private void saveSetting(String key, String value, Long userId) {
        var setting = settings.findById(key).orElseGet(() -> {
            var s = new PlatformSetting();
            s.setSettingKey(key);
            return s;
        });
        setting.setSettingValue(value);
        setting.setUpdatedBy(userId);
        setting.setUpdatedAt(now());
        settings.save(setting);
    }

    private void logAction(Long adminId, String action, String targetType, String targetId, Object details, String ipAddress) {
        AdminActivityLog log = new AdminActivityLog();
        log.setAdminId(adminId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        try {
            log.setDetailsJson(details != null ? json.writeValueAsString(details) : null);
        } catch (Exception e) {
            log.setDetailsJson(null);
        }
        log.setIpAddress(ipAddress);
        log.setCreatedAt(now());
        logs.save(log);
    }

    private void notifyUser(long targetUserId, String title, String message) {
        Notification n = new Notification();
        n.setUserId(targetUserId);
        n.setTitle(title);
        n.setMessage(message);
        n.setIsRead(0L);
        n.setCreatedAt(now());
        notifications.save(n);
    }
}
