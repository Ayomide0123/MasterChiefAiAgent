package com.hng.MasterChiefAiAgent.controller;

import com.hng.MasterChiefAiAgent.service.AIService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class A2AController {

    private final AIService aiService;

    public A2AController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate-prd")
    public ResponseEntity<byte[]> generatePRD(@RequestBody String prompt) {
        byte[] pdfBytes = aiService.generatePRDAsPDF(prompt);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=prd.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
