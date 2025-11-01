package com.hng.MasterChiefAiAgent.controller;

import com.hng.MasterChiefAiAgent.service.AIService;
import lombok.Value;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class A2AController {

    private final AIService aiService;

    public A2AController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate-prd")
    public ResponseEntity<String> generatePRD(@RequestBody String prompt) {
        String prdText = aiService.generatePRD(prompt);
        return ResponseEntity.ok(prdText);
    }
}