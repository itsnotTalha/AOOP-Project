package com.authvault.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.authvault.entity.Asset;
import com.authvault.entity.AssetMetadata;
import com.authvault.entity.Document;
import com.authvault.entity.MarketplaceListing;
import com.authvault.entity.MarketplaceTransaction;
import com.authvault.entity.OwnershipHistory;
import com.authvault.entity.Wallet;
import com.authvault.entity.WalletTransaction;
import com.authvault.exception.ApiException;
import com.authvault.repository.AssetJpaRepository;
import com.authvault.repository.AssetMetadataJpaRepository;
import com.authvault.repository.DocumentJpaRepository;
import com.authvault.repository.MarketplaceListingJpaRepository;
import com.authvault.repository.MarketplaceTransactionJpaRepository;
import com.authvault.repository.OwnershipHistoryJpaRepository;
import com.authvault.repository.PlatformSettingJpaRepository;
import com.authvault.repository.VaultAssetJpaRepository;
import com.authvault.repository.WalletJpaRepository;
import com.authvault.repository.WalletTransactionJpaRepository;
import com.authvault.service.BlockchainService;
import com.authvault.service.MarketplaceService;
import com.authvault.service.VaultAccessService;

import jakarta.persistence.EntityManager;

@Service
public class MarketplaceServiceImpl implements MarketplaceService {
    private final MarketplaceListingJpaRepository listings;
    private final AssetJpaRepository assets;
    private final AssetMetadataJpaRepository metadata;
    private final DocumentJpaRepository documents;
    private final WalletJpaRepository wallets;
    private final WalletTransactionJpaRepository transactions;
    private final OwnershipHistoryJpaRepository histories;
    private final VaultAssetJpaRepository vaultAssets;
    private final VaultAccessService access;
    private final BlockchainService blockchainService;
    private final TransactionTemplate transaction;
    private final EntityManager manager;
    private final PlatformSettingJpaRepository settings;
    private final MarketplaceTransactionJpaRepository sales;
    private final Path directory;
    private final Path docDirectory;
    private final String secret;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate db;

    public MarketplaceServiceImpl(
            MarketplaceListingJpaRepository listings,
            AssetJpaRepository assets,
            AssetMetadataJpaRepository metadata,
            DocumentJpaRepository documents,
            WalletJpaRepository wallets,
            WalletTransactionJpaRepository transactions,
            OwnershipHistoryJpaRepository histories,
            VaultAssetJpaRepository vaultAssets,
            VaultAccessService access,
            BlockchainService blockchainService,
            TransactionTemplate transaction,
            EntityManager manager,
            PlatformSettingJpaRepository settings,
            MarketplaceTransactionJpaRepository sales,
            @Value("${authvault.asset-directory:${UPLOAD_DIRECTORY:./data/uploads}}") String directory,
            @Value("${authvault.document-directory:${DOCUMENT_UPLOAD_DIRECTORY:./data/documents}}") String docDirectory,
            @Value("${authvault.public-id-secret:${PUBLIC_ID_SECRET:${JWT_SECRET:authvault-development-secret}}}") String secret) {
        this.settings = settings;
        this.sales = sales;
        this.listings = listings;
        this.assets = assets;
        this.metadata = metadata;
        this.documents = documents;
        this.wallets = wallets;
        this.transactions = transactions;
        this.histories = histories;
        this.vaultAssets = vaultAssets;
        this.access = access;
        this.blockchainService = blockchainService;
        this.transaction = transaction;
        this.manager = manager;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.docDirectory = Path.of(docDirectory).toAbsolutePath().normalize();
        this.secret = secret;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
        return out;
    }

    private String owner(long id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return "VC-" + HexFormat.of().formatHex(mac.doFinal(("owner:" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 8).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String reference(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("ML-[A-F0-9]{6}")) throw new ApiException(404, "Listing not found");
        return value;
    }

    private MarketplaceListing internal(String raw) {
        return listings.findByPublicReference(reference(raw)).orElseThrow(() -> new ApiException(404, "Listing not found"));
    }

    private static String text(Object value, String label, int max, boolean required) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (required && result.isEmpty()) throw new ApiException(400, label + " is required");
        if (result.length() > max) throw new ApiException(400, label + " must be " + max + " characters or fewer");
        return result.isEmpty() ? null : result;
    }

    private double price(Object value) {
        double number;
        try {
            number = Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            throw new ApiException(400, "Price must be a positive number");
        }
        double minimum = setting("minimum_listing_price", 1);
        if (!Double.isFinite(number) || number < minimum || number > 1_000_000_000) {
            throw new ApiException(400, "Price must be at least " + java.math.BigDecimal.valueOf(minimum).stripTrailingZeros().toPlainString());
        }
        if (Math.abs(number * 100 - Math.round(number * 100)) > 1e-8) {
            throw new ApiException(400, "Price may have at most two decimal places");
        }
        return number;
    }

    private double setting(String key, double fallback) {
        return settings.findById(key).map(v -> {
            try {
                double n = Double.parseDouble(v.getSettingValue());
                return Double.isFinite(n) ? n : fallback;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private Map<String, Object> publicListing(MarketplaceListing l, long user, String token) {
        Asset a = assets.findById(l.getAssetId()).orElseThrow(() -> new ApiException(404, "Listing not found"));
        AssetMetadata m = metadata.findByAssetId(a.getId()).orElse(null);
        Document doc = documents.findByAssetId(a.getId()).orElse(null);
        boolean isDocument = doc != null || "Document".equalsIgnoreCase(a.getCategory());

        boolean seller = l.getSellerId() == user;
        Map<String, Object> protection = map("passwordProtected", false, "isLocked", false);
        if ("active".equals(l.getStatus()) && Objects.equals(a.getOwnerId(), l.getSellerId())) {
            protection = access.protection(l.getSellerId(), a.getId(), seller ? token : null);
        }
        boolean locked = Boolean.TRUE.equals(protection.get("isLocked"));
        var requests = db.queryForList("SELECT id,status,created_at AS createdAt FROM marketplace_previews WHERE listing_id=? AND buyer_id=?", l.getId(), user);
        boolean approved = !requests.isEmpty() && "approved".equals(requests.getFirst().get("status"));
        boolean preview = "active".equals(l.getStatus()) && (seller ? !locked : approved);
        boolean anonymous = db.queryForList("SELECT listing_id FROM marketplace_options WHERE listing_id=? AND anonymous=1",l.getId()).size()>0;

        Map<String, Object> out = map(
            "reference", l.getPublicReference(),
            "title", l.getTitle(),
            "description", l.getDescription(),
            "price", agreedPrice(l,user),
            "isAnonymous", anonymous,
            "previewRequest", requests.isEmpty()?null:requests.getFirst(),
            "currency", "VaultChain Credits",
            "status", l.getStatus(),
            "createdAt", l.getCreatedAt(),
            "soldAt", l.getSoldAt(),
            "isDocument", isDocument,
            "document", doc == null ? null : map(
                "id", doc.getId(),
                "originalName", doc.getOriginalName(),
                "mimeType", doc.getMimeType(),
                "pageCount", doc.getPageCount(),
                "sha256", doc.getSha256Hash()
            ),
            "seller", map("reference", owner(l.getSellerId()), "name", anonymous ? "Anonymous creator" : db.queryForObject("SELECT full_name FROM users WHERE id=?",String.class,l.getSellerId()), "isCurrentUser", seller),
            "asset", map(
                "id", seller ? a.getId() : null,
                "reference", "AV-A" + String.format("%06d", a.getId()),
                "title", a.getTitle(),
                "category", locked ? null : a.getCategory(),
                "mimeType", locked ? null : a.getMimeType(),
                "fileSize", locked ? null : a.getFileSize(),
                "width", preview && m != null ? m.getWidth() : null,
                "height", preview && m != null ? m.getHeight() : null,
                "previewAvailable", preview,
                "contentUrl", preview ? "/api/marketplace/listings/" + l.getPublicReference() + "/content" : null,
                "passwordProtected", protection.get("passwordProtected"),
                "isLocked", locked
            )
        );
        return out;
    }

    @Override
    public Map<String, Object> create(long user, String token, Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        long assetId;
        if (body.get("documentId") != null) {
            long documentId;
            try {
                documentId = Long.parseLong(String.valueOf(body.get("documentId")));
            } catch (Exception e) {
                throw new ApiException(400, "documentId must be a positive integer");
            }
            Document d = documents.findByIdAndOwnerId(documentId, user)
                .orElseThrow(() -> new ApiException(404, "Document not found"));
            if (d.getAssetId() == null) {
                Asset a = new Asset();
                a.setOwnerId(user);
                a.setTitle(d.getOriginalName());
                a.setDescription("Verified document: " + d.getOriginalName());
                a.setCategory("Document");
                a.setFileName(d.getStoredName());
                a.setFilePath(d.getFilePath());
                a.setFileSize(d.getFileSize());
                a.setMimeType(d.getMimeType());
                a.setStatus("active");
                a = assets.saveAndFlush(a);
                d.setAssetId(a.getId());
                documents.saveAndFlush(d);
            }
            assetId = d.getAssetId();
        } else {
            try {
                assetId = Long.parseLong(String.valueOf(body.get("assetId")));
            } catch (Exception e) {
                throw new ApiException(400, "assetId is required");
            }
            if (assetId <= 0) throw new ApiException(400, "assetId is required");
        }

        Asset a = assets.findByIdAndOwnerId(assetId, user).orElseThrow(() -> new ApiException(404, "Asset not found"));
        access.assertAssetUnlocked(user, a.getId(), token);
        if (listings.existsByAssetIdAndStatus(assetId, "active")) {
            throw new ApiException(409, "This asset already has an active listing");
        }
        String title = text(body.get("title"), "Title", 120, true);
        String description = text(body.get("description"), "Description", 1000, false);
        double cost = price(body.get("price"));

        return transaction.execute(state -> {
            MarketplaceListing l = new MarketplaceListing();
            l.setAssetId(assetId);
            l.setSellerId(user);
            l.setTitle(title);
            l.setDescription(description);
            l.setPrice(cost);
            l.setListingType("sale");
            l.setStatus("active");
            boolean allocated = false;
            for (int i = 0; i < 8; i++) {
                byte[] bytes = new byte[3];
                new java.security.SecureRandom().nextBytes(bytes);
                String candidate = "ML-" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
                if (listings.findByPublicReference(candidate).isEmpty()) {
                    l.setPublicReference(candidate);
                    allocated = true;
                    break;
                }
            }
            if (!allocated) throw new ApiException(503, "Could not allocate a listing reference");
            l = listings.saveAndFlush(l);
            manager.refresh(l);
            db.update("INSERT INTO marketplace_options(listing_id,anonymous) VALUES(?,?)",l.getId(),Boolean.TRUE.equals(body.get("isAnonymous"))?1:0);
            return publicListing(l, user, token);
        });
    }

    @Override
    public List<Map<String, Object>> list(long user, String token) {
        return listings.findAllByOrderByCreatedAtDescIdDesc().stream()
            .map(l -> publicListing(l, user, token))
            .toList();
    }

    @Override
    public Map<String, Object> get(long user, String ref, String token) {
        return publicListing(internal(ref), user, token);
    }

    @Override
    public Map<String, Object> update(long user, String ref, String token, Map<String, Object> request) {
        return transaction.execute(state -> {
            MarketplaceListing l = internal(ref);
            if (l.getSellerId() != user) throw new ApiException(404, "Listing not found");
            if (!"active".equals(l.getStatus())) throw new ApiException(409, "Only active listings can be updated");
            Map<String, Object> body = request == null ? Map.of() : request;
            if (body.get("title") != null) l.setTitle(text(body.get("title"), "Title", 120, true));
            if (body.get("description") != null) {
                String d = text(body.get("description"), "Description", 1000, false);
                if (d != null) l.setDescription(d);
            }
            if (body.get("price") != null) l.setPrice(price(body.get("price")));
            l = listings.saveAndFlush(l);
            return publicListing(l, user, token);
        });
    }

    @Override
    public Map<String, Object> cancel(long user, String ref, String token) {
        return transaction.execute(state -> {
            MarketplaceListing l = internal(ref);
            if (l.getSellerId() != user) throw new ApiException(404, "Listing not found");
            if (!"active".equals(l.getStatus())) throw new ApiException(409, "Only active listings can be cancelled");
            l.setStatus("cancelled");
            l = listings.saveAndFlush(l);
            return publicListing(l, user, token);
        });
    }

    @Override
    public ResponseEntity<byte[]> content(long user, String ref, String token) {
        MarketplaceListing l = internal(ref);
        Asset a = assets.findById(l.getAssetId()).orElseThrow(() -> new ApiException(404, "Listing content not found"));
        if (!"active".equals(l.getStatus()) || !Objects.equals(a.getOwnerId(), l.getSellerId())) {
            throw new ApiException(404, "Listing content not found");
        }
        if (user == l.getSellerId()) access.assertAssetUnlocked(user,a.getId(),token);
        else if (db.queryForList("SELECT id FROM marketplace_previews WHERE listing_id=? AND buyer_id=? AND status='approved'",l.getId(),user).isEmpty()) throw new ApiException(403,"Request preview access from the seller first");
        try {
            Path targetFile = directory.resolve(Path.of(a.getFileName()).getFileName());
            if (!Files.exists(targetFile)) {
                targetFile = docDirectory.resolve(Path.of(a.getFileName()).getFileName());
            }
            return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(a.getMimeType() == null ? "application/octet-stream" : a.getMimeType()))
                .body(Files.readAllBytes(targetFile));
        } catch (Exception e) {
            throw new ApiException(404, "Not Found");
        }
    }

    public synchronized Map<String,Object> tip(long user,String ref){
        return transaction.execute(state->{
            var l=internal(ref);if(user==l.getSellerId())throw new ApiException(400,"You cannot tip yourself");
            String tx="TIP-"+java.util.UUID.randomUUID();
            if(db.update("UPDATE wallets SET balance=ROUND(balance-5,2) WHERE user_id=? AND balance>=5",user)!=1)throw new ApiException(400,"Insufficient wallet balance");
            db.update("UPDATE wallets SET balance=ROUND(balance+5,2) WHERE user_id=?",l.getSellerId());
            db.update("INSERT INTO wallet_transactions(wallet_id,type,amount,description,reference_id) SELECT id,'withdrawal',5,'Creator tip',? FROM wallets WHERE user_id=?",tx,user);
            db.update("INSERT INTO wallet_transactions(wallet_id,type,amount,description,reference_id) SELECT id,'deposit',5,'Creator tip received',? FROM wallets WHERE user_id=?",tx,l.getSellerId());
            return Map.of("success",true,"amount",5,"reference",tx);
        });
    }

    private double agreedPrice(MarketplaceListing l,long user){
        return db.query("SELECT amount FROM marketplace_offers WHERE listing_id=? AND buyer_id=?",(r,n)->r.getDouble(1),l.getId(),user).stream().findFirst().orElse(l.getPrice());
    }
    public Map<String,Object> previewRequest(long user,String ref){
        var l=internal(ref);
        if(l.getSellerId()==user||!"active".equals(l.getStatus()))throw new ApiException(409,"Only buyers of active listings can request access");
        db.update("INSERT INTO marketplace_previews(listing_id,buyer_id) VALUES(?,?) ON CONFLICT DO NOTHING",l.getId(),user);
        return db.queryForMap("SELECT id,status,created_at AS createdAt FROM marketplace_previews WHERE listing_id=? AND buyer_id=?",l.getId(),user);
    }
    public List<Map<String,Object>> previews(long user,String ref){
        var l=internal(ref);if(l.getSellerId()!=user)throw new ApiException(404,"Listing not found");
        return db.queryForList("SELECT p.id,p.status,p.created_at AS createdAt,u.full_name AS buyerName FROM marketplace_previews p JOIN users u ON p.buyer_id=u.id WHERE listing_id=? ORDER BY p.id DESC",l.getId());
    }
    public Map<String,Object> decidePreview(long user,String token,String ref,long id,String status){
        var l=internal(ref);if(l.getSellerId()!=user)throw new ApiException(404,"Listing not found");
        if(!List.of("approved","denied","revoked").contains(status))throw new ApiException(400,"Invalid request status");
        if("approved".equals(status)){if(!"active".equals(l.getStatus()))throw new ApiException(409,"Listing is no longer active");access.assertAssetUnlocked(user,l.getAssetId(),token);}
        if(db.update("UPDATE marketplace_previews SET status=? WHERE id=? AND listing_id=?",status,id,l.getId())==0)throw new ApiException(404,"Request not found");
        return db.queryForMap("SELECT id,status FROM marketplace_previews WHERE id=?",id);
    }
    public List<Map<String,Object>> messages(long user,String ref){
        var l=internal(ref);
        return db.queryForList("SELECT m.id,m.buyer_id AS buyerId,m.sender_id AS senderId,m.message AS text,m.amount,m.created_at AS time,u.full_name AS senderName FROM marketplace_messages m JOIN users u ON u.id=m.sender_id WHERE listing_id=? AND (?= ? OR buyer_id=?) ORDER BY m.id",l.getId(),user,l.getSellerId(),user);
    }
    public Map<String,Object> sendMessage(long user,String ref,Map<String,Object>b){
        var l=internal(ref);if(!"active".equals(l.getStatus()))throw new ApiException(409,"Listing is no longer active");
        boolean seller=l.getSellerId()==user;
        long buyer=user;
        if(seller){try{buyer=Long.parseLong(String.valueOf(b.get("buyerId")));}catch(Exception e){throw new ApiException(400,"Choose a buyer conversation");}
            if(db.queryForList("SELECT id FROM marketplace_messages WHERE listing_id=? AND buyer_id=?",l.getId(),buyer).isEmpty())throw new ApiException(404,"Conversation not found");}
        String message=text(b.get("text"),"Message",2000,b.get("amount")==null);
        Double amount=b.get("amount")==null?null:price(b.get("amount"));
        db.update("INSERT INTO marketplace_messages(listing_id,buyer_id,sender_id,message,amount) VALUES(?,?,?,?,?)",l.getId(),buyer,user,message==null?"Price offer":message,amount);
        return Map.of("success",true);
    }
    public Map<String,Object> acceptOffer(long user,String ref,long id){
        var l=internal(ref);
        var rows=db.queryForList("SELECT * FROM marketplace_messages WHERE id=? AND listing_id=? AND amount IS NOT NULL",id,l.getId());
        if(rows.isEmpty())throw new ApiException(404,"Offer not found");var offer=rows.getFirst();
        long buyer=((Number)offer.get("buyer_id")).longValue(),sender=((Number)offer.get("sender_id")).longValue();
        if(!"active".equals(l.getStatus())||sender==user || !(user==l.getSellerId()||user==buyer))throw new ApiException(403,"You cannot accept this offer");
        db.update("INSERT INTO marketplace_offers(listing_id,buyer_id,amount) VALUES(?,?,?) ON CONFLICT(listing_id,buyer_id) DO UPDATE SET amount=excluded.amount",l.getId(),buyer,offer.get("amount"));
        return Map.of("success",true,"price",offer.get("amount"));
    }

    @Override
    public synchronized Map<String, Object> purchase(long user, String ref) {
        return transaction.execute(state -> {
            MarketplaceListing l = internal(ref);
            if (!"active".equals(l.getStatus())) throw new ApiException(409, "This listing is no longer available");
            Asset a = assets.findById(l.getAssetId()).orElseThrow(() -> new ApiException(409, "The seller no longer owns this asset"));
            if (!Objects.equals(a.getOwnerId(), l.getSellerId())) throw new ApiException(409, "The seller no longer owns this asset");
            if (l.getSellerId() == user) throw new ApiException(409, "You cannot purchase your own listing");
            l.setPrice(agreedPrice(l,user));
            Wallet buyer = wallets.findByUserId(user).orElseThrow(() -> new ApiException(404, "Wallet not found"));
            Wallet seller = wallets.findByUserId(l.getSellerId()).orElseThrow(() -> new ApiException(404, "Wallet not found"));
            if (buyer.getBalance() < l.getPrice()) throw new ApiException(400, "Insufficient VaultChain Credits");

            double newBuyer = Math.round((buyer.getBalance() - l.getPrice()) * 100) / 100.0;
            double rate = Math.max(0, Math.min(1, setting("marketplace_commission_rate", 0.05)));
            double fee = Math.round(l.getPrice() * rate * 100) / 100.0;
            double payout = Math.round((l.getPrice() - fee) * 100) / 100.0;

            buyer.setBalance(newBuyer);
            var organizationIds=db.query("SELECT organization_id FROM organization_listings WHERE listing_id=?",(r,n)->r.getString(1),l.getId());
            boolean organizationSale=!organizationIds.isEmpty();
            seller.setBalance(Math.round((seller.getBalance() + (organizationSale?0:payout)) * 100) / 100.0);
            if(organizationSale){
                String organizationId=organizationIds.getFirst();
                db.update("UPDATE organizations SET data=json_set(data,'$.treasuryBalance',ROUND(COALESCE(json_extract(data,'$.treasuryBalance'),0)+?,2),'$.totalSales',ROUND(COALESCE(json_extract(data,'$.totalSales'),0)+?,2)) WHERE id=?",payout,l.getPrice(),organizationId);
                db.update("UPDATE organizations SET data=json_insert(data,'$.sales[#]',json_object('id',?,'date',datetime('now'),'assetTitle',?,'buyer',?,'grossAmount',?,'treasuryCut',?,'creatorPayout',0,'txHash',?), '$.treasuryTransactions[#]',json_object('id',?,'type','Inflow','category','Marketplace sale','amount',?,'recipient','Treasury','date',datetime('now'),'note',?,'status','Executed','txHash',?)) WHERE id=?","sale-"+l.getPublicReference(),l.getTitle(),owner(user),l.getPrice(),payout,l.getPublicReference(),l.getPublicReference(),payout,l.getTitle(),l.getPublicReference(),organizationId);
            }
            wallets.saveAndFlush(buyer);
            wallets.saveAndFlush(seller);

            a.setOwnerId(user);
            a.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            assets.saveAndFlush(a);

            // Also transfer Document ownership if applicable
            documents.findByAssetId(a.getId()).ifPresent(doc -> {
                doc.setOwnerId(user);
                documents.saveAndFlush(doc);
            });

            l.setStatus("sold");
            l.setBuyerId(user);
            l.setSoldAt(a.getUpdatedAt());
            listings.saveAndFlush(l);
            vaultAssets.deleteSellerMemberships(a.getId(), seller.getUserId());

            byte[] bytes = new byte[3];
            new java.security.SecureRandom().nextBytes(bytes);
            String tx = "TX-" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);

            OwnershipHistory h = new OwnershipHistory();
            h.setAssetId(a.getId());
            h.setPreviousOwner(seller.getUserId());
            h.setNewOwner(user);
            h.setListingId(l.getId());
            h.setPrice(l.getPrice());
            h.setTransactionReference(tx);
            h.setTransferType("marketplace_sale");
            histories.saveAndFlush(h);

            // Record ownership transfer in Blockchain ledger
            blockchainService.recordBlock(a.getId(), user, "TRANSFER_OWNERSHIP", "TX:" + tx + ":PRICE:" + l.getPrice() + ":FROM:" + seller.getUserId() + ":TO:" + user);

            MarketplaceTransaction sale = new MarketplaceTransaction();
            sale.setTransactionId(tx);
            sale.setAssetId(a.getId());
            sale.setListingId(l.getId());
            sale.setSellerId(seller.getUserId());
            sale.setBuyerId(user);
            sale.setSaleAmount(l.getPrice());
            sale.setPlatformFee(fee);
            sale.setSellerAmount(payout);
            sale.setStatus("completed");
            sale.setCreatedAt(l.getSoldAt());
            sales.saveAndFlush(sale);

            WalletTransaction debit = new WalletTransaction();
            debit.setWalletId(buyer.getId());
            debit.setType("purchase");
            debit.setAmount(l.getPrice());
            debit.setDescription("Marketplace purchase: " + a.getTitle());
            debit.setReferenceId(tx);
            transactions.saveAndFlush(debit);

            if (!organizationSale) {
            WalletTransaction credit = new WalletTransaction();
            credit.setWalletId(seller.getId());
            credit.setType("sale");
            credit.setAmount(payout);
            credit.setDescription("Marketplace sale payout after " + java.math.BigDecimal.valueOf(rate * 100).stripTrailingZeros().toPlainString() + "% platform fee: " + a.getTitle());
            credit.setReferenceId(tx);
            transactions.saveAndFlush(credit);
            }

            return map(
                "transactionReference", tx,
                "listingReference", l.getPublicReference(),
                "asset", map("reference", "AV-A" + String.format("%06d", a.getId()), "title", a.getTitle()),
                "previousOwner", owner(seller.getUserId()),
                "newOwner", owner(user),
                "price", l.getPrice(),
                "currency", "VaultChain Credits",
                "platformFee", fee,
                "sellerAmount", payout,
                "sellerBalance", seller.getBalance(),
                "completedAt", l.getSoldAt(),
                "buyerBalance", newBuyer
            );
        });
    }
}
