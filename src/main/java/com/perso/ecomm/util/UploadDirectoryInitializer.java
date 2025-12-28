package com.perso.ecomm.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class UploadDirectoryInitializer {

    @Value("${upload.path}")
    private String uploadPath;

    @PostConstruct
    public void init() {
        File directory = new File(uploadPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }
}

