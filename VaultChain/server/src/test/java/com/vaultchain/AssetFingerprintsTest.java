package com.vaultchain;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultchain.service.impl.AssetFingerprints;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class AssetFingerprintsTest {
    @Test void matchesOriginalNodeMetadataShaAndBlockHashForSyntheticPng() throws Exception {
        byte[] bytes;
        try(InputStream stream=getClass().getResourceAsStream("/image/synthetic-rgba.png")){
            assertThat(stream).isNotNull();bytes=stream.readAllBytes();
        }
        AssetFingerprints.Result result=new AssetFingerprints(new ObjectMapper()).compute(bytes,"image/png",true);
        assertThat(result.sha256()).isEqualTo("4770209760d91976516b5d9889f0628c605998de291fd0be11ca1cb18d580fa0");
        assertThat(result.phash()).isEqualTo("010113272f6f4e7f1ebeff917c222024444c18b8b1b9737bf7f4efe0e8a00121");
        assertThat(result.metadata().get("pixelCount")).isEqualTo(1073L);
    }
}
