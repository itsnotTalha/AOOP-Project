package com.vaultchain.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultchain.entity.Asset;
import com.vaultchain.entity.AssetHash;
import com.vaultchain.entity.Document;
import com.vaultchain.entity.DocumentVerification;
import com.vaultchain.entity.OcrResult;
import com.vaultchain.exception.ApiException;
import com.vaultchain.repository.AssetHashJpaRepository;
import com.vaultchain.repository.AssetJpaRepository;
import com.vaultchain.repository.DocumentJpaRepository;
import com.vaultchain.repository.DocumentVerificationJpaRepository;
import com.vaultchain.repository.OcrResultJpaRepository;
import com.vaultchain.service.BlockchainService;
import com.vaultchain.service.DocumentService;

import jakarta.persistence.EntityManager;

@Service
public class DocumentServiceImpl implements DocumentService {
    private static final DateTimeFormatter SQLITE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DocumentJpaRepository documents;
    private final OcrResultJpaRepository ocrResults;
    private final DocumentVerificationJpaRepository verifications;
    private final AssetJpaRepository assets;
    private final AssetHashJpaRepository assetHashes;
    private final BlockchainService blockchainService;
    private final EntityManager entityManager;
    private final ObjectMapper json;
    private final Path directory;

    public DocumentServiceImpl(
            DocumentJpaRepository documents,
            OcrResultJpaRepository ocrResults,
            DocumentVerificationJpaRepository verifications,
            AssetJpaRepository assets,
            AssetHashJpaRepository assetHashes,
            BlockchainService blockchainService,
            EntityManager entityManager,
            ObjectMapper json,
            @Value("${vaultchain.document-directory:${DOCUMENT_UPLOAD_DIRECTORY:./data/documents}}") String directory) {
        this.documents = documents;
        this.ocrResults = ocrResults;
        this.verifications = verifications;
        this.assets = assets;
        this.assetHashes = assetHashes;
        this.blockchainService = blockchainService;
        this.entityManager = entityManager;
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    private static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {}
        throw new ApiException(404, "Document not found");
    }

    private Document owned(long userId, String value) {
        return documents.findByIdAndOwnerId(id(value), userId)
            .orElseThrow(() -> new ApiException(404, "Document not found"));
    }

    private Path path(Document d) {
        return directory.resolve(Path.of(d.getStoredName()).getFileName()).normalize();
    }

    private static String iso(String value) {
        return value == null ? null : value.replace(' ', 'T') + "Z";
    }

    private static String now() {
        return SQLITE_TIME.format(LocalDateTime.now(ZoneOffset.UTC));
    }

    private static String sha(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String md5(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception failure) {
            return null;
        }
    }

    private static String simHash(String text) {
        if (text == null || text.isBlank()) return null;
        int[] v = new int[64];
        String[] words = normalize(text).split("\\s+");
        for (String word : words) {
            if (word.isBlank()) continue;
            long hash = fnv1a64(word);
            for (int i = 0; i < 64; i++) {
                if (((hash >> i) & 1L) == 1L) v[i]++;
                else v[i]--;
            }
        }
        long sim = 0L;
        for (int i = 0; i < 64; i++) {
            if (v[i] > 0) sim |= (1L << i);
        }
        return String.format("%016x", sim);
    }

    private static long fnv1a64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private OcrResult result(Document d) {
        return ocrResults.findByDocumentId(d.getId()).orElse(null);
    }

    private Map<String, Object> extractMetadata(Document d, String extractedText) {
        Map<String, Object> meta = new LinkedHashMap<>();
        Path file = path(d);
        if (d.getMimeType().equals("application/pdf")) {
            try {
                String info = run("pdfinfo", file.toString());
                for (String line : info.split("\\R")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT).replace(" ", "_");
                        String val = line.substring(colon + 1).trim();
                        meta.put(key, val);
                    }
                }
            } catch (Exception ignored) {}
        } else if (d.getMimeType().startsWith("image/")) {
            try {
                BufferedImage img = ImageIO.read(file.toFile());
                if (img != null) {
                    meta.put("width", img.getWidth());
                    meta.put("height", img.getHeight());
                    meta.put("color_type", img.getColorModel().getColorSpace().getType());
                }
            } catch (Exception ignored) {}
        }
        if (extractedText != null && !extractedText.isBlank()) {
            String[] words = extractedText.trim().split("\\s+");
            String[] lines = extractedText.split("\\R");
            String[] paras = extractedText.split("(\\R\\s*){2,}");
            meta.put("wordCount", words.length);
            meta.put("characterCount", extractedText.length());
            meta.put("lineCount", lines.length);
            meta.put("paragraphCount", paras.length);
        } else {
            meta.put("wordCount", 0);
            meta.put("characterCount", 0);
            meta.put("lineCount", 0);
            meta.put("paragraphCount", 0);
        }
        return meta;
    }

    private Map<String, Object> extractHashes(Document d, OcrResult r) {
        Map<String, Object> hashes = new LinkedHashMap<>();
        hashes.put("sha256", d.getSha256Hash());
        try {
            byte[] bytes = Files.readAllBytes(path(d));
            hashes.put("md5", md5(bytes));
        } catch (Exception e) {
            hashes.put("md5", null);
        }
        String text = r != null ? r.getExtractedText() : null;
        hashes.put("semanticHash", r != null ? r.getSemanticHash() : null);
        hashes.put("structureHash", simHash(text));
        return hashes;
    }

    private Map<String, Object> publicDocument(Document d, boolean detailed) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.getId());
        out.put("assetId", d.getAssetId());
        out.put("reference", "DOC-" + String.format("%06d", d.getId()));
        out.put("originalName", d.getOriginalName());
        out.put("mimeType", d.getMimeType());
        out.put("fileSize", d.getFileSize());
        out.put("sha256", d.getSha256Hash());
        out.put("pageCount", d.getPageCount());
        out.put("language", d.getLanguage());
        out.put("ocrMode", d.getLanguage() != null && d.getLanguage().contains("handwritten") ? "handwritten" : "printed");
        out.put("ocrStatus", d.getOcrStatus());
        out.put("ocrProcessedAt", iso(d.getOcrProcessedAt()));
        out.put("createdAt", iso(d.getCreatedAt()));
        out.put("contentUrl", "/api/documents/" + d.getId() + "/content");

        OcrResult r = result(d);
        String text = r != null && r.getExtractedText() != null ? r.getExtractedText() : "";
        out.put("hashes", extractHashes(d, r));
        out.put("metadata", extractMetadata(d, text));

        if (detailed) {
            out.put("extractedText", text);
            out.put("ocrError", d.getOcrError());
            out.put("confidence", r == null ? null : r.getConfidence());
            out.put("duplicateInfo", duplicateInfo(d.getOwnerId(), d));
            if (d.getAssetId() != null) {
                out.put("blockchain", blockchainService.getVerificationFlow(d.getAssetId(), d.getSha256Hash()));
            }
        }
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> upload(long userId, MultipartFile file, String mode) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "Document file is required");
        if (file.getSize() > 20L * 1024 * 1024) throw new ApiException(413, "File size exceeds the 20 MB limit");
        String original = Path.of(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replace('\\', '/')).getFileName().toString();
        original = original.replaceAll("[\\x00-\\x1f\\x7f]", "");
        if (original.isBlank()) original = "document";
        if (original.length() > 180) original = original.substring(0, 180);
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        String mime = file.getContentType();
        if (!Set.of(".pdf", ".jpg", ".jpeg", ".png").contains(ext) ||
            !Set.of("application/pdf", "image/jpeg", "image/png").contains(mime)) {
            throw new ApiException(400, "Only PDF, JPG, JPEG, and PNG documents are allowed");
        }
        Path stored = null;
        try {
            Files.createDirectories(directory);
            stored = directory.resolve(UUID.randomUUID() + ext);
            file.transferTo(stored);
            byte[] fileBytes = Files.readAllBytes(stored);
            String fileSha = sha(fileBytes);

            var docDups = documents.findBySha256Hash(fileSha);
            if (!docDups.isEmpty()) {
                try { Files.deleteIfExists(stored); } catch (IOException ignored) {}
                throw new ApiException(409, "Duplicate document rejected: A document with identical cryptographic hash already exists in VaultChain (\"" + docDups.get(0).getOriginalName() + "\").");
            }
            var assetDups = assetHashes.findBySha256HashOrderByAssetIdAsc(fileSha);
            if (!assetDups.isEmpty()) {
                try { Files.deleteIfExists(stored); } catch (IOException ignored) {}
                throw new ApiException(409, "Duplicate upload rejected: A digital file with identical cryptographic hash already exists in VaultChain.");
            }

            String imagePhash = null;
            if (mime.startsWith("image/")) {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(fileBytes));
                    if (img != null) {
                        imagePhash = BlockHash.hash(img);
                        if (imagePhash != null) {
                            var phashDups = assetHashes.findByPhashOrderByAssetIdAsc(imagePhash);
                            if (!phashDups.isEmpty()) {
                                try { Files.deleteIfExists(stored); } catch (IOException ignored) {}
                                throw new ApiException(409, "Duplicate upload rejected: An image with visually identical perceptual hash already exists in VaultChain.");
                            }
                        }
                    }
                } catch (ApiException e) {
                    throw e;
                } catch (Exception ignored) {}
            }

            Document d = new Document();
            d.setOwnerId(userId);
            d.setOriginalName(original);
            d.setStoredName(stored.getFileName().toString());
            d.setFilePath(stored.toString());
            d.setMimeType(mime);
            d.setFileSize(file.getSize());
            d.setSha256Hash(fileSha);
            d.setLanguage("eng");
            d.setOcrStatus("pending");

            // Bridge to Asset entity
            Asset a = new Asset();
            a.setOwnerId(userId);
            a.setTitle(original);
            a.setDescription("Verified document: " + original);
            a.setCategory("Document");
            a.setFileName(stored.getFileName().toString());
            a.setFilePath(stored.toString());
            a.setFileSize(file.getSize());
            a.setMimeType(mime);
            a.setStatus("active");
            a = assets.saveAndFlush(a);
            d.setAssetId(a.getId());

            AssetHash ah = new AssetHash();
            ah.setAssetId(a.getId());
            ah.setSha256Hash(fileSha);
            ah.setPhash(imagePhash);
            assetHashes.saveAndFlush(ah);

            d = documents.saveAndFlush(d);
            entityManager.refresh(d);

            // Record initial blockchain block
            blockchainService.recordBlock(a.getId(), userId, "REGISTER_DOCUMENT", "DOC-" + d.getId() + ":" + fileSha);

            Map<String, Object> result = retryOcr(userId, String.valueOf(d.getId()), mode);
            @SuppressWarnings("unchecked")
            Map<String, Object> dup = (Map<String, Object>) result.get("duplicateInfo");
            if (dup != null && Boolean.TRUE.equals(dup.get("isDuplicate"))) {
                String matchType = (String) dup.get("matchType");
                if ("exact_sha256".equals(matchType) || "semantic_ocr".equals(matchType)) {
                    delete(userId, String.valueOf(d.getId()));
                    throw new ApiException(409, "Duplicate document rejected: Identical text content already exists in VaultChain.");
                }
            }
            return result;
        } catch (IOException failure) {
            if (stored != null) try { Files.deleteIfExists(stored); } catch (IOException ignored) {}
            throw new ApiException(500, failure.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> list(long userId, String search, String type, String status) {
        search = search == null ? "" : search.trim();
        type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        status = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (search.length() > 200) throw new ApiException(400, "Search must be 200 characters or fewer");
        if (!type.isEmpty() && !Set.of("pdf", "image").contains(type)) throw new ApiException(400, "Invalid document type filter");
        if (!status.isEmpty() && !Set.of("pending", "processing", "completed", "failed").contains(status)) {
            throw new ApiException(400, "Invalid OCR status filter");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Document d : documents.findByOwnerIdOrderByCreatedAtDescIdDesc(userId)) {
            if (type.equals("pdf") && !d.getMimeType().equals("application/pdf")) continue;
            if (type.equals("image") && !d.getMimeType().startsWith("image/")) continue;
            if (!status.isEmpty() && !status.equals(d.getOcrStatus())) continue;
            OcrResult r = result(d);
            String ocr = r == null || r.getExtractedText() == null ? "" : r.getExtractedText();
            String lower = search.toLowerCase(Locale.ROOT);
            int pos = ocr.toLowerCase(Locale.ROOT).indexOf(lower);
            if (!search.isEmpty() && !d.getOriginalName().toLowerCase(Locale.ROOT).contains(lower) &&
                !d.getSha256Hash().toLowerCase(Locale.ROOT).contains(lower) &&
                !("DOC-" + String.format("%06d", d.getId())).toLowerCase(Locale.ROOT).contains(lower) &&
                !d.getId().toString().contains(search) && pos < 0) continue;
            Map<String, Object> item = publicDocument(d, false);
            if (!search.isEmpty()) {
                item.put("matchedOcrText", pos >= 0);
                item.put("ocrSnippet", pos < 0 ? null : ocr.substring(Math.max(0, pos - 50), Math.min(ocr.length(), Math.max(0, pos - 50) + 160)).trim());
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public Map<String, Object> get(long userId, String value) {
        return publicDocument(owned(userId, value), true);
    }

    @Override
    public Map<String, Object> ocr(long userId, String value) {
        Document d = owned(userId, value);
        OcrResult r = result(d);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", d.getOcrStatus());
        out.put("extractedText", r == null || r.getExtractedText() == null ? "" : r.getExtractedText());
        out.put("processedAt", iso(d.getOcrProcessedAt()));
        out.put("error", d.getOcrError());
        out.put("confidence", r == null ? null : r.getConfidence());
        return out;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private static String run(String... command) throws Exception {
        Path output = Files.createTempFile("vaultchain-command-", ".out");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out");
            }
            String text = Files.readString(output);
            if (process.exitValue() != 0) throw new IOException(text);
            return text;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static Long pageCount(Path file) {
        try {
            String text = run("pdfinfo", file.toString());
            for (String line : text.split("\\R")) {
                if (line.startsWith("Pages:")) return Long.parseLong(line.substring(6).trim());
            }
            return null;
        } catch (Exception failure) {
            return null;
        }
    }

    private static Path resolveScript() {
        List<Path> candidates = List.of(
            Path.of("scripts/handwriting_ocr.py").toAbsolutePath(),
            Path.of("server/scripts/handwriting_ocr.py").toAbsolutePath(),
            Path.of("VaultChain/server/scripts/handwriting_ocr.py").toAbsolutePath(),
            Path.of("src/main/resources/scripts/handwriting_ocr.py").toAbsolutePath(),
            Path.of("target/classes/scripts/handwriting_ocr.py").toAbsolutePath(),
            Path.of("/home/potato/Project/AOOP-Project/VaultChain/server/scripts/handwriting_ocr.py"),
            Path.of("/home/potato/Project/AOOP-Project/VaultChain/server/src/main/resources/scripts/handwriting_ocr.py")
        );
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static String extract(Path file, String mime, String mode) throws Exception {
        if (mime.equals("application/pdf")) {
            String text = "";
            try {
                if (mode == null || !mode.equalsIgnoreCase("handwritten")) {
                    text = run("pdftotext", "-layout", file.toString(), "-");
                }
            } catch (Exception ignored) {}
            if (text != null && text.trim().length() >= 10) return text;

            Long pages = pageCount(file);
            if (pages != null && pages > 10) throw new IOException("Scanned PDF OCR is limited to 10 pages");
            Path temp = Files.createTempDirectory("vaultchain-pdf-ocr-");
            try {
                Path prefix = temp.resolve("page");
                run("pdftoppm", "-png", "-r", "150", file.toString(), prefix.toString());
                List<Path> images;
                try (var stream = Files.list(temp)) {
                    images = stream.filter(p -> p.toString().endsWith(".png"))
                        .sorted(java.util.Comparator.comparingInt(p -> {
                            String n = p.getFileName().toString();
                            try { return Integer.parseInt(n.substring(n.lastIndexOf('-') + 1, n.lastIndexOf('.'))); }
                            catch (Exception ignored) { return 0; }
                        })).toList();
                }
                List<String> parts = new ArrayList<>();
                for (Path image : images) {
                    try {
                        String part = extractImage(image, "image/png", mode);
                        if (part != null && !part.isBlank()) parts.add(part);
                    } catch (Exception ignored) {}
                }
                return String.join("\n\n--- Page Break ---\n\n", parts);
            } finally {
                try (var stream = Files.list(temp)) {
                    for (Path image : stream.toList()) Files.deleteIfExists(image);
                }
                Files.deleteIfExists(temp);
            }
        }
        return extractImage(file, mime, mode);
    }

    private static String extractImage(Path file, String mime, String mode) throws Exception {
        byte[] head = Files.readAllBytes(file);
        boolean png = head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8), new byte[]{(byte) 0x89, 80, 78, 71, 13, 10, 26, 10});
        boolean jpeg = head.length >= 3 && (head[0] & 255) == 255 && (head[1] & 255) == 216 && (head[2] & 255) == 255;
        if (mime.equals("image/png") && !png || mime.equals("image/jpeg") && !jpeg) {
            throw new IOException("Invalid image data");
        }

        Path script = resolveScript();
        if (script != null) {
            try {
                String res = run("python3", script.toString(), file.toString(), "--mode", mode == null ? "handwritten" : mode);
                if (res != null && !res.isBlank()) {
                    return res.trim();
                }
            } catch (Exception ignored) {}
        }

        try {
            return run("tesseract", file.toString(), "stdout", "-l", "eng");
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public Map<String, Object> retryOcr(long userId, String value, String mode) {
        Document d = owned(userId, value);
        boolean isHandwritten = mode != null && (mode.equalsIgnoreCase("handwritten") || mode.equalsIgnoreCase("eng-handwritten"));
        d.setLanguage(isHandwritten ? "eng-handwritten" : "eng");
        d.setOcrStatus("processing");
        d.setOcrError(null);
        documents.saveAndFlush(d);
        try {
            String text = extract(path(d), d.getMimeType(), mode);
            OcrResult r = result(d);
            if (r == null) {
                r = new OcrResult();
                r.setDocumentId(d.getId());
            }
            r.setExtractedText(text == null ? "" : text);
            r.setConfidence(text != null && !text.isBlank() ? 0.95 : null);
            r.setSemanticHash(text == null || text.isBlank() ? null : sha(normalize(text).getBytes(StandardCharsets.UTF_8)));
            ocrResults.saveAndFlush(r);
            d.setPageCount(d.getMimeType().equals("application/pdf") ? pageCount(path(d)) : 1L);
            d.setOcrStatus("completed");
            d.setOcrError(null);
        } catch (Exception failure) {
            d.setOcrStatus("failed");
            d.setOcrError("Text extraction failed. You can retry OCR.");
        }
        d.setOcrProcessedAt(now());
        documents.saveAndFlush(d);

        Map<String, Object> out = publicDocument(d, true);
        out.put("duplicateInfo", duplicateInfo(userId, d));
        if (d.getAssetId() != null) {
            out.put("blockchain", blockchainService.getVerificationFlow(d.getAssetId(), d.getSha256Hash()));
        }
        return out;
    }

    private Map<String, Object> duplicateInfo(long ownerId, Document current) {
        OcrResult currentText = result(current);
        Document best = null;
        String type = null;
        double score = 0;
        List<Document> candidates = documents.findAll().stream()
            .filter(d -> !d.getId().equals(current.getId()))
            .sorted(java.util.Comparator.comparing((Document d) -> d.getOwnerId().equals(ownerId)).reversed()
                .thenComparing(Document::getId, java.util.Comparator.reverseOrder()))
            .toList();

        for (Document candidate : candidates) {
            if (current.getSha256Hash().equalsIgnoreCase(candidate.getSha256Hash())) {
                best = candidate;
                type = "exact_sha256";
                score = 1.0;
                break;
            }
        }
        if (best == null && currentText != null && currentText.getSemanticHash() != null) {
            for (Document candidate : candidates) {
                OcrResult other = result(candidate);
                if (other != null && currentText.getSemanticHash().equals(other.getSemanticHash())) {
                    best = candidate;
                    type = "semantic_ocr";
                    score = 1.0;
                    break;
                }
            }
            if (best == null) {
                for (Document candidate : candidates) {
                    OcrResult other = result(candidate);
                    if (other == null || other.getExtractedText() == null) continue;
                    Set<String> a = tokens(currentText.getExtractedText());
                    Set<String> b = tokens(other.getExtractedText());
                    if (a.isEmpty() || b.isEmpty()) continue;
                    Set<String> union = new HashSet<>(a);
                    union.addAll(b);
                    Set<String> common = new HashSet<>(a);
                    common.retainAll(b);
                    double candidateScore = Math.round(10000.0 * common.size() / union.size()) / 10000.0;
                    if (candidateScore > score) {
                        score = candidateScore;
                        best = candidate;
                    }
                }
                if (best != null && type == null) {
                    if (score >= 0.70) type = "modified";
                    else best = null;
                }
            }
        }
        if (best == null) {
            return null;
        }

        int percent = (int) Math.round(score * 100);
        int changed = 100 - percent;
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("id", best.getId());
        match.put("originalName", best.getOriginalName());
        match.put("reference", "DOC-" + String.format("%06d", best.getId()));
        match.put("sha256", best.getSha256Hash());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("isDuplicate", true);
        info.put("matchType", type);
        info.put("status", score >= 0.98 ? "original" : "modified");
        info.put("similarityScore", score);
        info.put("ocrMatchPercent", percent);
        info.put("modificationPercent", changed);
        info.put("sha256Match", "exact_sha256".equals(type));
        info.put("matchedDocument", match);

        String quoted = "\"" + best.getOriginalName() + "\"";
        String message = "exact_sha256".equals(type) ?
            "Duplicate document detected! Both SHA-256 hash and OCR text are 100% identical to " + quoted + "." :
            "semantic_ocr".equals(type) || percent == 100 ?
            "Duplicate content detected! OCR text matches 100% with " + quoted + " (SHA-256 binary hash differs)." :
            "Modified document detected: OCR text matches " + percent + "% with " + quoted + " (" + changed + "% content modified).";
        info.put("message", message);
        return info;
    }

    @Override
    public ResponseEntity<byte[]> content(long userId, String value, boolean preview) {
        Document d = owned(userId, value);
        Path file = path(d);
        try {
            byte[] bytes;
            String mime = d.getMimeType();
            if (preview && mime.equals("application/pdf")) {
                Path temp = Files.createTempDirectory("vaultchain-preview-");
                try {
                    Path prefix = temp.resolve("page");
                    run("pdftoppm", "-png", "-r", "150", "-f", "1", "-singlefile", file.toString(), prefix.toString());
                    bytes = Files.readAllBytes(temp.resolve("page.png"));
                    mime = "image/png";
                } finally {
                    Files.deleteIfExists(temp.resolve("page.png"));
                    Files.deleteIfExists(temp);
                }
            } else {
                bytes = Files.readAllBytes(file);
            }
            return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(mime))
                .body(bytes);
        } catch (IOException failure) {
            throw new ApiException(404, "Document file not found");
        } catch (Exception failure) {
            throw new ApiException(500, failure.getMessage());
        }
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        for (String word : normalize(text).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) result.add(word);
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> verify(long userId, String value, Object referenceValue) {
        long targetId;
        try {
            targetId = Long.parseLong(value);
            if (targetId <= 0) throw new NumberFormatException();
        } catch (Exception failure) {
            throw new ApiException(400, "Document id must be a positive integer");
        }
        long referenceId;
        try {
            referenceId = Long.parseLong(String.valueOf(referenceValue));
            if (referenceId <= 0) throw new NumberFormatException();
        } catch (Exception failure) {
            throw new ApiException(400, "Reference document id must be a positive integer");
        }
        if (targetId == referenceId) {
            throw new ApiException(400, "Reference document must be different from the document being verified");
        }
        Document target = owned(userId, value);
        Document reference = owned(userId, String.valueOf(referenceId));
        OcrResult a = result(target);
        OcrResult b = result(reference);
        boolean sha = target.getSha256Hash().equalsIgnoreCase(reference.getSha256Hash());
        boolean ready = "completed".equals(target.getOcrStatus()) && "completed".equals(reference.getOcrStatus()) &&
            a != null && b != null && a.getSemanticHash() != null && b.getSemanticHash() != null;
        Boolean semantic = ready ? a.getSemanticHash().equals(b.getSemanticHash()) : null;
        Set<String> ta = ready ? tokens(a.getExtractedText()) : Set.of();
        Set<String> tb = ready ? tokens(b.getExtractedText()) : Set.of();
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        Set<String> common = new HashSet<>(ta);
        common.retainAll(tb);
        Double similarity = !ready ? null : Boolean.TRUE.equals(semantic) ? 1.0 : union.isEmpty() ? null : Math.round(10000.0 * common.size() / union.size()) / 10000.0;
        String status = !ready ? "unknown" : Boolean.TRUE.equals(semantic) ? "original" : "modified";

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("algorithm", "sha256_binary_and_normalized_token_jaccard");
        report.put("sha256Match", sha);
        report.put("targetSha256", target.getSha256Hash());
        report.put("referenceSha256", reference.getSha256Hash());
        report.put("reason", !ready ? "Both documents must complete OCR with extracted text before full text comparison." :
            sha ? "Exact byte-for-byte binary match (identical SHA-256 hash)." : Boolean.TRUE.equals(semantic) ?
            "Normalized OCR text is identical (same content, but different binary encoding/metadata)." : "Normalized OCR text differs from the reference document.");
        if (ready) {
            report.put("tokenComparison", Map.of(
                "similarityScore", similarity == null ? 0 : similarity,
                "targetTokenCount", ta.size(),
                "referenceTokenCount", tb.size(),
                "commonTokenCount", common.size()
            ));
        } else {
            report.put("targetOcrStatus", target.getOcrStatus());
            report.put("referenceOcrStatus", reference.getOcrStatus());
        }

        DocumentVerification v = new DocumentVerification();
        v.setUserId(userId);
        v.setDocumentId(targetId);
        v.setReferenceDocumentId(referenceId);
        v.setSemanticHashMatch(semantic == null ? null : semantic ? 1L : 0L);
        v.setSimilarityScore(similarity);
        v.setStatus(status);
        try {
            v.setReportJson(json.writeValueAsString(report));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        v = verifications.saveAndFlush(v);
        entityManager.refresh(v);

        // Record verification on blockchain
        if (target.getAssetId() != null) {
            blockchainService.recordBlock(target.getAssetId(), userId, "DOCUMENT_VERIFIED", "REF:" + referenceId + ":STATUS:" + status);
        }

        return verification(v);
    }

    private Map<String, Object> verification(DocumentVerification v) {
        Document d = documents.findById(v.getDocumentId()).orElseThrow();
        Document r = documents.findById(v.getReferenceDocumentId()).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", v.getId());
        out.put("documentId", v.getDocumentId());
        out.put("documentName", d.getOriginalName());
        out.put("referenceDocumentId", v.getReferenceDocumentId());
        out.put("referenceDocumentName", r.getOriginalName());
        out.put("semanticHashMatch", v.getSemanticHashMatch() == null ? null : v.getSemanticHashMatch() != 0);
        out.put("similarityScore", v.getSimilarityScore());
        out.put("status", v.getStatus());
        try {
            out.put("report", json.readValue(v.getReportJson(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception failure) {
            out.put("report", Map.of());
        }
        out.put("createdAt", iso(v.getCreatedAt()));
        return out;
    }

    @Override
    public List<Map<String, Object>> history(long userId, String value) {
        Document d = owned(userId, value);
        return verifications.findByDocumentIdAndUserIdOrderByCreatedAtDescIdDesc(d.getId(), userId)
            .stream()
            .map(this::verification)
            .toList();
    }

    @Override
    public void delete(long userId, String value) {
        Document d = owned(userId, value);
        Path file = path(d);
        if (d.getAssetId() != null) {
            assetHashes.findByAssetId(d.getAssetId()).ifPresent(assetHashes::delete);
            assets.deleteById(d.getAssetId());
        }
        documents.delete(d);
        documents.flush();
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }
}
