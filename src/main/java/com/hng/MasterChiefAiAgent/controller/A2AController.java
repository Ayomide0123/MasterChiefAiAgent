package com.hng.MasterChiefAiAgent.controller;

import com.hng.MasterChiefAiAgent.service.AIService;
import org.springframework.web.bind.annotation.*;
import org.json.JSONObject;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/a2a")
public class A2AController {

    private final AIService aiService;

    public A2AController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/agent/prdAgent")
    public String handleA2ARequest(@RequestBody String requestBody) {
        try {
            JSONObject request = new JSONObject(requestBody);

            // Extract key request data
            String id = request.getString("id");
            JSONObject params = request.getJSONObject("params");
            JSONObject message = params.getJSONObject("message");
            String userPrompt = message.getJSONArray("parts").getJSONObject(0).getString("text");

            // Generate PDF as Base64
            String base64Pdf = aiService.generatePRDPdfBase64(userPrompt);

            // Build response
            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            JSONObject result = new JSONObject();
            result.put("id", "task-" + UUID.randomUUID());
            result.put("contextId", UUID.randomUUID().toString());

            // ----- STATUS -----
            JSONObject status = new JSONObject();
            status.put("state", "completed");
            status.put("timestamp", Instant.now().toString());

            JSONObject agentMessage = new JSONObject();
            agentMessage.put("messageId", "msg-" + UUID.randomUUID());
            agentMessage.put("role", "agent");
            agentMessage.put("kind", "message");

            JSONObject textPart = new JSONObject();
            textPart.put("kind", "text");
            textPart.put("text", "Your PRD document has been generated successfully.");

            agentMessage.put("parts", new org.json.JSONArray().put(textPart));
            status.put("message", agentMessage);
            result.put("status", status);

            // ----- ARTIFACT 1: Text summary -----
            JSONObject artifactText = new JSONObject();
            artifactText.put("artifactId", "artifact-" + UUID.randomUUID());
            artifactText.put("name", "prdAgentResponse");

            JSONObject textArtifactPart = new JSONObject();
            textArtifactPart.put("kind", "text");
            textArtifactPart.put("text", "Your PRD document has been generated successfully and is attached as Base64 data.");

            artifactText.put("parts", new org.json.JSONArray().put(textArtifactPart));

            // ----- ARTIFACT 2: PDF (Base64 data) -----
            JSONObject artifactData = new JSONObject();
            artifactData.put("artifactId", "artifact-" + UUID.randomUUID());
            artifactData.put("name", "PRDDocument");

            JSONObject dataPart = new JSONObject();
            dataPart.put("kind", "data");
            dataPart.put("data", base64Pdf);

            artifactData.put("parts", new org.json.JSONArray().put(dataPart));

            // ----- Add both artifacts -----
            result.put("artifacts", new org.json.JSONArray()
                    .put(artifactText)
                    .put(artifactData)
            );

            // Optional: maintain a history array (even if empty)
            result.put("history", new org.json.JSONArray());

            // Attach to response
            response.put("result", result);

            return response.toString(2); // pretty print

        } catch (Exception e) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("error", new JSONObject()
                    .put("code", -32603)
                    .put("message", "Failed to process request: " + e.getMessage()));
            return errorResponse.toString(2);
        }
    }
}
