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

    @PostMapping
    public String handleA2ARequest(@RequestBody String requestBody) {
        try {
            JSONObject request = new JSONObject(requestBody);

            // Extract fields from A2A request
            String id = request.getString("id");
            JSONObject params = request.getJSONObject("params");
            JSONObject message = params.getJSONObject("message");
            String userPrompt = message
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            // Generate PDF (as Base64)
            String base64Pdf = aiService.generatePRDPdfBase64(userPrompt);

            // Build A2A response
            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            JSONObject result = new JSONObject();
            result.put("id", message.getString("taskId"));
            result.put("contextId", UUID.randomUUID().toString());

            JSONObject status = new JSONObject();
            status.put("state", "completed");
            status.put("timestamp", Instant.now().toString());

            JSONObject agentMessage = new JSONObject();
            agentMessage.put("messageId", "msg-" + UUID.randomUUID());
            agentMessage.put("role", "agent");

            JSONObject textPart = new JSONObject();
            textPart.put("kind", "text");
            textPart.put("text", "Your PRD document has been generated successfully.");

            agentMessage.put("parts", new org.json.JSONArray().put(textPart));
            agentMessage.put("kind", "message");
            status.put("message", agentMessage);

            // Add artifacts (Base64-encoded PDF)
            JSONObject artifact = new JSONObject();
            artifact.put("artifactId", UUID.randomUUID().toString());
            artifact.put("name", "prdDocument");

            JSONObject dataPart = new JSONObject();
            dataPart.put("kind", "data");
            dataPart.put("data", base64Pdf);

            artifact.put("parts", new org.json.JSONArray().put(dataPart));

            result.put("status", status);
            result.put("artifacts", new org.json.JSONArray().put(artifact));
            result.put("kind", "task");

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
