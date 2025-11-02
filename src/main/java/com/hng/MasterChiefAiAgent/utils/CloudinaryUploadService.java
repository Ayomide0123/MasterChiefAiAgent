package com.hng.MasterChiefAiAgent.utils;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryUploadService {

    private final Cloudinary cloudinary;

    public CloudinaryUploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadPdf(byte[] pdfBytes) throws IOException {
        // Create a temporary file to store the PDF
        String filename = "prd-" + UUID.randomUUID();
        File tempFile = File.createTempFile(filename, ".pdf");

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(pdfBytes);
        }

        try {
            // Upload to Cloudinary (use resource_type=raw for non-image files)
            Map uploadResult = cloudinary.uploader().upload(tempFile, ObjectUtils.asMap(
                    "resource_type", "raw",
                    "use_filename", true,
                    "unique_filename", false,
                    "overwrite", true
            ));

            // Return the Cloudinary-hosted URL
            return uploadResult.get("secure_url").toString();

        } finally {
            // Always delete the temp file to avoid clutter
            tempFile.delete();
        }
    }
}
