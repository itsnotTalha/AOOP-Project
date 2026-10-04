package com.authvault.repository;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardReadRepository {
    private final EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate db;
    public DashboardReadRepository(EntityManager entityManager){this.entityManager=entityManager;}
    private static long number(Object value){return value==null?0:((Number)value).longValue();}
    private static double decimal(Object value){return value==null?0:((Number)value).doubleValue();}
    private static Map<String,Object> row(String[] keys,Object[] values){Map<String,Object> out=new LinkedHashMap<>();
        for(int i=0;i<keys.length;i++)out.put(keys[i],values[i]);return out;}
    @SuppressWarnings("unchecked")
    public Map<String,Object> summary(long userId){
        Object[] counts=(Object[])entityManager.createNativeQuery("""
            SELECT (SELECT COUNT(*) FROM assets WHERE owner_id = ?1),
                   (SELECT COUNT(*) FROM documents WHERE owner_id = ?1),
                   ((SELECT COUNT(*) FROM verification_reports WHERE user_id = ?1)+(SELECT COUNT(*) FROM document_verifications WHERE user_id=?1)),
                   (SELECT COUNT(*) FROM vaults WHERE user_id = ?1),
                   (SELECT COUNT(DISTINCT va.asset_id) FROM vault_assets va JOIN vaults v ON v.id=va.vault_id WHERE v.user_id=?1),
                   (SELECT COUNT(*) FROM marketplace_listings WHERE seller_id=?1 AND status='active'),
                   (SELECT COALESCE(balance,0) FROM wallets WHERE user_id=?1)
            """).setParameter(1,userId).getSingleResult();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("totalAssets",number(counts[0]));out.put("totalDocuments",number(counts[1]));
        out.put("totalVerificationReports",number(counts[2]));out.put("totalVaults",number(counts[3]));
        out.put("totalOrganizedAssets",number(counts[4]));out.put("totalVaultItems",number(counts[4]));
        out.put("activeListings",number(counts[5]));out.put("walletBalance",decimal(counts[6]));
        List<Map<String,Object>> recentAssets=new ArrayList<>();
        for(Object raw:entityManager.createNativeQuery("SELECT id,title,category,mime_type,created_at FROM assets WHERE owner_id=?1 ORDER BY created_at DESC,id DESC LIMIT 5").setParameter(1,userId).getResultList())
            recentAssets.add(row(new String[]{"id","title","category","mimeType","createdAt"},(Object[])raw));
        out.put("recentAssets",recentAssets);
        List<Map<String,Object>> recentDocuments=new ArrayList<>();
        for(Object raw:entityManager.createNativeQuery("SELECT id,original_name,mime_type,ocr_status,created_at FROM documents WHERE owner_id=?1 ORDER BY created_at DESC,id DESC LIMIT 5").setParameter(1,userId).getResultList())
            recentDocuments.add(row(new String[]{"id","originalName","mimeType","ocrStatus","createdAt"},(Object[])raw));
        out.put("recentDocuments",recentDocuments);
        List<Map<String,Object>> activities=new ArrayList<>();
        for(Object raw:entityManager.createNativeQuery("""
            SELECT activity_type,title,amount,reference,status,asset_id,document_id,created_at FROM (
              SELECT 'asset_upload' AS activity_type,title,NULL AS amount,'AV-A'||printf('%06d',id) AS reference,
                     NULL AS status,id AS asset_id,NULL AS document_id,created_at,id AS sequence FROM assets WHERE owner_id=?1
              UNION ALL SELECT 'verification','Image verification',NULL,NULL,status,asset_id,NULL,created_at,id
                        FROM verification_reports WHERE user_id=?1
              UNION ALL SELECT wt.type,COALESCE(wt.description,wt.type),wt.amount,wt.reference_id,NULL,NULL,NULL,wt.created_at,wt.id
                        FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id
                        WHERE w.user_id=?1 AND wt.type IN ('purchase','sale')
              UNION ALL SELECT 'document_upload',original_name,NULL,'DOC-'||printf('%06d',id),ocr_status,NULL,id,created_at,id
                        FROM documents WHERE owner_id=?1
            ) ORDER BY created_at DESC,sequence DESC LIMIT 8
            """).unwrap(NativeQuery.class)
            // SQLite UNION metadata depends on the first row, including its NULLs.
            // Declare every scalar so later text references/statuses stay textual.
            .addScalar("activity_type",String.class)
            .addScalar("title",String.class)
            .addScalar("amount",Double.class)
            .addScalar("reference",String.class)
            .addScalar("status",String.class)
            .addScalar("asset_id",Long.class)
            .addScalar("document_id",Long.class)
            .addScalar("created_at",java.sql.Timestamp.class)
            .setParameter(1,userId).getResultList()) {
            Object[] values=(Object[])raw;
            activities.add(row(new String[]{"type","title","amount","reference","status","assetId","documentId","createdAt"},values));
        }
        out.put("recentActivity",activities);out.put("analytics",analytics(userId));return out;
    }
    private Map<String,Object> analytics(long user){
        var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);var start=today.minusDays(29);
        Map<String,Map<String,Object>> daily=new LinkedHashMap<>();
        for(int i=0;i<30;i++){String date=start.plusDays(i).toString();var day=new LinkedHashMap<String,Object>();day.put("date",date);for(String key:List.of("assets","documents","verifications","earnings","spending"))day.put(key,0);daily.put(date,day);}
        var activity=db.queryForList("""
            SELECT day,kind,COUNT(*) AS count FROM (
              SELECT date(created_at) AS day,'assets' AS kind FROM assets WHERE owner_id=?
              UNION ALL SELECT date(created_at),'documents' FROM documents WHERE owner_id=?
              UNION ALL SELECT date(created_at),'verifications' FROM verification_reports WHERE user_id=?
              UNION ALL SELECT date(created_at),'verifications' FROM document_verifications WHERE user_id=?
            ) WHERE day BETWEEN ? AND ? GROUP BY day,kind
            """,user,user,user,user,start.toString(),today.toString());
        for(var r:activity)daily.get(r.get("day")).put((String)r.get("kind"),r.get("count"));
        var trading=db.queryForList("""
            SELECT date(t.created_at) AS day,
             SUM(CASE WHEN seller_id=? AND o.listing_id IS NULL THEN seller_amount ELSE 0 END) AS earnings,
             SUM(CASE WHEN buyer_id=? THEN sale_amount ELSE 0 END) AS spending
            FROM marketplace_transactions t LEFT JOIN organization_listings o ON o.listing_id=t.listing_id
            WHERE (seller_id=? OR buyer_id=?) AND t.status='completed' AND date(t.created_at) BETWEEN ? AND ? GROUP BY date(t.created_at)
            """,user,user,user,user,start.toString(),today.toString());
        for(var r:trading){daily.get(r.get("day")).put("earnings",r.get("earnings"));daily.get(r.get("day")).put("spending",r.get("spending"));}
        return Map.of("daily",new ArrayList<>(daily.values()),"timezone","UTC",
            "categories",db.queryForList("SELECT COALESCE(NULLIF(category,''),'Uncategorized') AS name,COUNT(*) AS value FROM assets WHERE owner_id=? GROUP BY COALESCE(NULLIF(category,''),'Uncategorized') ORDER BY value DESC,name",user),
            "ocr",db.queryForList("SELECT ocr_status AS name,COUNT(*) AS value FROM documents WHERE owner_id=? GROUP BY ocr_status ORDER BY value DESC",user),
            "verification",db.queryForList("SELECT COALESCE(status,'unknown') AS name,COUNT(*) AS value FROM (SELECT status FROM verification_reports WHERE user_id=? UNION ALL SELECT status FROM document_verifications WHERE user_id=?) GROUP BY status ORDER BY value DESC",user,user));
    }

}
