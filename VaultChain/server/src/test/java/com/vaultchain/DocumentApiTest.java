package com.vaultchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("vaultchain.database-path",()->directory.resolve("documents.sqlite").toString());
        r.add("vaultchain.document-directory",()->directory.resolve("files").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String register() throws Exception {
        String body=json.writeValueAsString(java.util.Map.of("fullName","Document User","email",UUID.randomUUID()+"@example.test","password","Password123!"));
        MvcResult result=mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        return "Bearer "+json.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
    @Test void uploadReadVerifyHistoryAndDeleteAreOwnerScoped() throws Exception {
        String owner=register(),other=register();
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
        MockMultipartFile file=new MockMultipartFile("file","broken.png","image/png",new byte[]{1,2,3,4});
        MvcResult uploaded=mvc.perform(multipart("/api/documents").file(file).header("Authorization",owner))
            .andExpect(status().isCreated()).andReturn();
        JsonNode first=json.readTree(uploaded.getResponse().getContentAsString()).path("document");
        long id=first.path("id").asLong();
        assertThat(first.path("ocrStatus").asText()).isEqualTo("failed");
        assertThat(first.path("duplicateInfo").isNull()).isTrue();
        mvc.perform(get("/api/documents/"+id).header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.document.originalName").value("broken.png"));
        mvc.perform(get("/api/documents/"+id+"/content").header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{1,2,3,4}));
        mvc.perform(get("/api/documents/"+id+"/preview").header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(get("/api/documents/"+id+"/ocr").header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.ocr.status").value("failed"));
        mvc.perform(get("/api/documents/"+id).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents?search=broken").header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.documents.length()").value(1));
        mvc.perform(get("/api/documents").header("Authorization",other)).andExpect(status().isOk())
            .andExpect(jsonPath("$.documents.length()").value(0));

        // Re-uploading duplicate must return 409 Conflict
        mvc.perform(multipart("/api/documents").file(file).header("Authorization",owner))
            .andExpect(status().isConflict());

        // Upload distinct file to obtain reference document for verification
        MockMultipartFile file2=new MockMultipartFile("file","second.png","image/png",new byte[]{5,6,7,8});
        MvcResult second=mvc.perform(multipart("/api/documents").file(file2).header("Authorization",owner))
            .andExpect(status().isCreated()).andReturn();
        long reference=json.readTree(second.getResponse().getContentAsString()).path("document").path("id").asLong();

        mvc.perform(post("/api/documents/"+id+"/verify").header("Authorization",owner)
            .contentType("application/json").content("{\"referenceDocumentId\":"+reference+"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.verification.status").value("unknown"));
        mvc.perform(get("/api/documents/"+id+"/report").header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.history.length()").value(1));
        mvc.perform(get("/api/documents/"+id+"/report").header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/"+id).header("Authorization",owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/"+id).header("Authorization",owner)).andExpect(status().isNotFound());
        assertThat(Files.list(directory.resolve("files")).count()).isEqualTo(1);
    }

    @Test void uploadHandwrittenEnglishDocumentExtractsText() throws Exception {
        String owner = register();
        Path sample = Path.of("/home/potato/.gemini/antigravity/brain/af2e7315-2572-46a4-984e-59190392ff29/scratch/sample.png");
        byte[] bytes;
        if (Files.exists(sample)) {
            bytes = Files.readAllBytes(sample);
        } else {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(300, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 300, 60);
            g.setColor(java.awt.Color.BLACK);
            g.drawString("VaultChain Note", 10, 35);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            bytes = baos.toByteArray();
        }
        MockMultipartFile handwrittenFile = new MockMultipartFile("file", "handwritten_document.png", "image/png", bytes);
        MvcResult res = mvc.perform(multipart("/api/documents")
                .file(handwrittenFile)
                .param("ocrMode", "handwritten")
                .header("Authorization", owner))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode doc = json.readTree(res.getResponse().getContentAsString()).path("document");
        assertThat(doc.path("ocrStatus").asText()).isEqualTo("completed");
        assertThat(doc.path("ocrMode").asText()).isEqualTo("handwritten");
        String extracted = doc.path("extractedText").asText();
        assertThat(extracted).isNotBlank();
    }
}
