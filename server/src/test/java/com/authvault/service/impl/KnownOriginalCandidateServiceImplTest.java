package com.authvault.service.impl;

import com.authvault.config.PerceptualHashProperties;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.AssetStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnownOriginalCandidateServiceImplTest {

    private DigitalAssetRepository repository;
    private AssetStorageService storageService;
    private PerceptualHashServiceImpl perceptualHashService;
    private PerceptualHashProperties properties;
    private KnownOriginalCandidateServiceImpl service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        repository = mock(DigitalAssetRepository.class);
        storageService = mock(AssetStorageService.class);
        perceptualHashService = new PerceptualHashServiceImpl();
        properties = new PerceptualHashProperties();
        service = new KnownOriginalCandidateServiceImpl(
                repository, storageService, perceptualHashService, properties);

        currentUser = user(7L, "owner");
        CustomUserDetails userDetails = new CustomUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsPreviousOwnedImagesRankedByDistanceAndEnforcesMaximum() {
        properties.setMaxCandidates(2);
        LocalDateTime targetTime = LocalDateTime.now();
        DigitalAsset target = image("target", targetTime, "0000000000000000");
        DigitalAsset distanceThree = image(
                "distance-three", targetTime.minusHours(3), "0000000000000007");
        DigitalAsset distanceOne = image(
                "distance-one", targetTime.minusHours(1), "0000000000000001");
        DigitalAsset distanceTwo = image(
                "distance-two", targetTime.minusHours(2), "0000000000000003");
        DigitalAsset currentTarget = image(
                target.getUuid(), targetTime.minusDays(1), "0000000000000000");
        DigitalAsset future = image(
                "future", targetTime.plusMinutes(1), "0000000000000000");
        DigitalAsset document = image(
                "document", targetTime.minusDays(1), "0000000000000000");
        document.setAssetType(DigitalAsset.AssetType.DOCUMENT);

        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(repository.findPreviousOwnedImages(currentUser, target.getUuid(), targetTime))
                .thenReturn(List.of(
                        distanceThree, document, currentTarget, future, distanceOne, distanceTwo));

        SimilarImagesResponse response = service.findSimilarImages(target.getUuid());

        assertEquals(target.getUuid(), response.getAssetId());
        assertEquals(target.getPerceptualHash(), response.getPerceptualHash());
        assertEquals(2, response.getClosestMatches().size());
        assertEquals("distance-one", response.getClosestMatches().get(0).getMatchedAssetId());
        assertEquals(1, response.getClosestMatches().get(0).getHammingDistance());
        assertEquals(PerceptualMatchBand.NEAR_DUPLICATE,
                response.getClosestMatches().get(0).getMatchBand());
        assertEquals("distance-two", response.getClosestMatches().get(1).getMatchedAssetId());
        verify(repository).findPreviousOwnedImages(currentUser, target.getUuid(), targetTime);
        verify(storageService, never()).loadStoredAsset(any());
    }

    @Test
    void lazilyPopulatesMissingHashesAndSkipsMissingOrMalformedCandidates() throws Exception {
        LocalDateTime targetTime = LocalDateTime.now();
        DigitalAsset target = image("target", targetTime, null);
        target.setStoragePath("images/target.png");
        DigitalAsset validHistorical = image("valid", targetTime.minusDays(1), null);
        validHistorical.setStoragePath("images/valid.png");
        DigitalAsset missingHistorical = image("missing", targetTime.minusDays(2), null);
        missingHistorical.setStoragePath("images/missing.png");
        DigitalAsset corruptHistorical = image("corrupt", targetTime.minusDays(3), null);
        corruptHistorical.setStoragePath("images/corrupt.png");
        byte[] validImage = createImageBytes();

        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(repository.findPreviousOwnedImages(currentUser, target.getUuid(), targetTime))
                .thenReturn(List.of(missingHistorical, corruptHistorical, validHistorical));
        when(repository.save(any(DigitalAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.loadStoredAsset("images/target.png"))
                .thenReturn(new ByteArrayInputStream(validImage));
        when(storageService.loadStoredAsset("images/valid.png"))
                .thenReturn(new ByteArrayInputStream(validImage));
        when(storageService.loadStoredAsset("images/missing.png"))
                .thenThrow(new ResourceNotFoundException("Stored asset file was not found"));
        when(storageService.loadStoredAsset("images/corrupt.png"))
                .thenReturn(new ByteArrayInputStream("corrupt".getBytes()));

        SimilarImagesResponse response = service.findSimilarImages(target.getUuid());

        assertTrue(target.getPerceptualHash().matches("[0-9a-f]{16}"));
        assertEquals(target.getPerceptualHash(), validHistorical.getPerceptualHash());
        assertEquals(1, response.getClosestMatches().size());
        assertEquals("valid", response.getClosestMatches().getFirst().getMatchedAssetId());
        assertEquals(PerceptualMatchBand.EXACT_VISUAL_HASH,
                response.getClosestMatches().getFirst().getMatchBand());

        ArgumentCaptor<DigitalAsset> savedAssets = ArgumentCaptor.forClass(DigitalAsset.class);
        verify(repository, org.mockito.Mockito.times(2)).save(savedAssets.capture());
        assertTrue(savedAssets.getAllValues().containsAll(List.of(target, validHistorical)));
    }

    @Test
    void rejectsDocumentTargetWithoutLoadingCandidates() {
        DigitalAsset document = image("document", LocalDateTime.now(), null);
        document.setAssetType(DigitalAsset.AssetType.DOCUMENT);
        when(repository.findByUuidAndCurrentOwner(document.getUuid(), currentUser))
                .thenReturn(Optional.of(document));

        assertThrows(BadRequestException.class,
                () -> service.findSimilarImages(document.getUuid()));

        verify(repository, never()).findPreviousOwnedImages(any(), any(), any());
    }

    @Test
    void returnsEmptyCandidatesWhenNoPreviousOwnedImagesExist() {
        DigitalAsset target = image(
                "target", LocalDateTime.now(), "0000000000000000");
        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(repository.findPreviousOwnedImages(
                currentUser, target.getUuid(), target.getUploadDate()))
                .thenReturn(List.of());

        SimilarImagesResponse response = service.findSimilarImages(target.getUuid());

        assertNotNull(response.getClosestMatches());
        assertTrue(response.getClosestMatches().isEmpty());
    }

    @Test
    void nonexistentOrAnotherUsersAssetIsInaccessible() {
        String missingId = UUID.randomUUID().toString();
        when(repository.findByUuidAndCurrentOwner(eq(missingId), eq(currentUser)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.findSimilarImages(missingId));
    }

    private DigitalAsset image(String uuid, LocalDateTime uploadDate, String perceptualHash) {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(uuid);
        asset.setCurrentOwner(currentUser);
        asset.setAssetType(DigitalAsset.AssetType.IMAGE);
        asset.setTitle("Title " + uuid);
        asset.setOriginalFilename(uuid + ".png");
        asset.setUploadDate(uploadDate);
        asset.setPerceptualHash(perceptualHash);
        return asset;
    }

    private User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUuid(UUID.randomUUID().toString());
        user.setFullName("Test User");
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private byte[] createImageBytes() throws Exception {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 80, 60);
        graphics.setColor(Color.BLUE);
        graphics.fillOval(10, 10, 35, 30);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
