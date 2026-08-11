package com.authvault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "authvault.upload")
@Getter
@Setter
public class UploadProperties {

    private DataSize imageMaxSize = DataSize.ofMegabytes(25);
    private DataSize documentMaxSize = DataSize.ofMegabytes(50);
    private Path rootDirectory = Path.of("uploads");
}
