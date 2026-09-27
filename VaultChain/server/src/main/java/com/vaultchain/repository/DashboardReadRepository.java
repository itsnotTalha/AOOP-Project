package com.vaultchain.repository;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardReadRepository {
    private final EntityManager entityManager;
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
                   (SELECT COUNT(*) FROM verification_reports WHERE user_id = ?1),
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
              SELECT 'asset_upload' AS activity_type,title,NULL AS amount,'VC-A'||printf('%06d',id) AS reference,
                     NULL AS status,id AS asset_id,NULL AS document_id,created_at,id AS sequence FROM assets WHERE owner_id=?1
              UNION ALL SELECT 'verification','Image verification',NULL,NULL,status,asset_id,NULL,created_at,id
                        FROM verification_reports WHERE user_id=?1
              UNION ALL SELECT wt.type,COALESCE(wt.description,wt.type),wt.amount,wt.reference_id,NULL,NULL,NULL,wt.created_at,wt.id
                        FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id
                        WHERE w.user_id=?1 AND wt.type IN ('purchase','sale')
              UNION ALL SELECT 'document_upload',original_name,NULL,'DOC-'||printf('%06d',id),ocr_status,NULL,id,created_at,id
                        FROM documents WHERE owner_id=?1
            ) ORDER BY created_at DESC,sequence DESC LIMIT 8
            """).setParameter(1,userId).getResultList()) {
            Object[] values=(Object[])raw;
            activities.add(row(new String[]{"type","title","amount","reference","status","assetId","documentId","createdAt"},values));
        }
        out.put("recentActivity",activities);return out;
    }
}
