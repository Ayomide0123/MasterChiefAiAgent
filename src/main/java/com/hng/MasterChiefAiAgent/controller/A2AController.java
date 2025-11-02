package com.hng.MasterChiefAiAgent.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hng.MasterChiefAiAgent.service.AIService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/a2a")
public class A2AController {

    private final AIService aiService;
    private final Cloudinary cloudinary;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public A2AController(AIService aiService, Cloudinary cloudinary) {
        this.aiService = aiService;
        this.cloudinary = cloudinary;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/agent/prdAgent")
    public String handleA2ARequest(@RequestBody String requestBody) {
        JSONObject request = null;
        try {
            request = new JSONObject(requestBody);
            String id = request.optString("id", UUID.randomUUID().toString());

            JSONObject params = request.getJSONObject("params");
            JSONObject message = params.getJSONObject("message");
            JSONObject configuration = params.optJSONObject("configuration");

            boolean isBlocking = configuration != null && configuration.optBoolean("blocking", true);

            // 🔹 Extract user prompt from nested structure
            String userPrompt = extractUserPrompt(message);

            if (userPrompt.isEmpty()) {
                throw new IllegalArgumentException("No valid user prompt found in request");
            }

            String taskId = "task-" + UUID.randomUUID();
            String contextId = "ctx-" + UUID.randomUUID();
            String messageId = "msg-" + UUID.randomUUID();

            // 🔹 If non-blocking, return "in_progress" and process asynchronously
            if (!isBlocking) {
                // Return immediate acknowledgment
                ObjectNode response = buildInProgressResponse(id, taskId, contextId, messageId);

                // Process asynchronously
                JSONObject finalConfiguration = configuration;
                String finalUserPrompt = userPrompt;
                String finalTaskId = taskId;
                String finalContextId = contextId;

                CompletableFuture.runAsync(() -> {
                    try {
                        processAndSendResult(finalUserPrompt, finalTaskId, finalContextId, finalConfiguration);
                    } catch (Exception e) {
                        // Log error and send error notification
                        sendErrorNotification(finalConfiguration, finalTaskId, e);
                    }
                });

                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            }

            // 🔹 Blocking mode - process and return immediately
            return processAndReturnResult(id, userPrompt, taskId, contextId, messageId);

        } catch (Exception e) {
            return buildErrorResponse(
                    request != null ? request.optString("id", UUID.randomUUID().toString()) : UUID.randomUUID().toString(),
                    e
            );
        }
    }

    private String extractUserPrompt(JSONObject message) {
        JSONArray parts = message.getJSONArray("parts");

        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);

            // Check direct text
            if (part.getString("kind").equals("text") &&
                    part.has("text") &&
                    !part.getString("text").isEmpty()) {
                return part.getString("text");
            }

            // Check nested data array
            if (part.getString("kind").equals("data") && part.has("data")) {
                JSONArray dataArray = part.getJSONArray("data");
                for (int j = dataArray.length() - 1; j >= 0; j--) {
                    JSONObject dataItem = dataArray.getJSONObject(j);
                    if (dataItem.getString("kind").equals("text") &&
                            dataItem.has("text")) {
                        String text = dataItem.getString("text")
                                .replaceAll("<[^>]*>", "")
                                .trim();
                        if (!text.isEmpty()) {
                            return text;
                        }
                    }
                }
            }
        }

        return "";
    }

    private ObjectNode buildInProgressResponse(String id, String taskId, String contextId, String messageId) {
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("kind", "text");
        textPart.put("text", "🔄 Generating your PRD document...");
        textPart.putNull("data");
        textPart.putNull("file_url");

        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(textPart);

        ObjectNode messageObj = objectMapper.createObjectNode();
        messageObj.put("kind", "message");
        messageObj.put("role", "agent");
        messageObj.set("parts", parts);
        messageObj.put("messageId", messageId);
        messageObj.put("taskId", taskId);
        messageObj.putNull("metadata");

        ObjectNode statusObj = objectMapper.createObjectNode();
        statusObj.put("state", "in_progress");
        statusObj.put("timestamp", Instant.now().toString());
        statusObj.set("message", messageObj);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", taskId);
        result.put("contextId", contextId);
        result.set("status", statusObj);
        result.set("artifacts", objectMapper.createArrayNode());
        result.set("history", objectMapper.createArrayNode());
        result.put("kind", "task");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        response.putNull("error");

        return response;
    }

    private String processAndReturnResult(String id, String userPrompt, String taskId, String contextId, String messageId) throws Exception {
        // Generate PDF
        String base64Pdf = aiService.generatePRDPdfBase64(userPrompt);
        byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

        // Upload to Cloudinary
        Map uploadResult = cloudinary.uploader().upload(
                pdfBytes,
                ObjectUtils.asMap(
                        "resource_type", "raw",
                        "public_id", "prd_" + UUID.randomUUID(),
                        "format", "pdf"
                )
        );

        String fileUrl = ((String) uploadResult.get("secure_url")).replace("/upload/", "/raw/upload/");
        if (!fileUrl.endsWith(".pdf")) {
            fileUrl += ".pdf";
        }

        // Build completed response
        String artifactId = "artifact-" + UUID.randomUUID();
        String userMessageId = "msg-" + UUID.randomUUID();

        // 🔹 Build text part
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("kind", "text");
        textPart.put("text", "📄 Your PRD has been generated successfully! Click below to download it:");
        textPart.putNull("data");
        textPart.putNull("file_url");

        // 🔹 Build file part
        ObjectNode filePart = objectMapper.createObjectNode();
        filePart.put("kind", "file");
        filePart.put("file_url", fileUrl);
        filePart.put("file_name", "Product_Requirement_Document.pdf");
        filePart.put("mime_type", "application/pdf");
        filePart.putNull("data");

        // 🔹 Build message parts array
        ArrayNode messageParts = objectMapper.createArrayNode();
        messageParts.add(textPart);
        messageParts.add(filePart);

        // 🔹 Build message object
        ObjectNode messageObj = objectMapper.createObjectNode();
        messageObj.put("kind", "message");
        messageObj.put("role", "agent");
        messageObj.set("parts", messageParts);
        messageObj.put("messageId", messageId);
        messageObj.put("taskId", taskId);
        messageObj.putNull("metadata");

        // 🔹 Build status object
        ObjectNode statusObj = objectMapper.createObjectNode();
        statusObj.put("state", "completed");
        statusObj.put("timestamp", Instant.now().toString());
        statusObj.set("message", messageObj);

        // 🔹 Build artifact parts array
        ArrayNode artifactParts = objectMapper.createArrayNode();
        artifactParts.add(filePart);

        // 🔹 Build artifact object
        ObjectNode artifactObj = objectMapper.createObjectNode();
        artifactObj.put("artifactId", artifactId);
        artifactObj.put("name", "PRDDocument");
        artifactObj.set("parts", artifactParts);

        // 🔹 Build artifacts array
        ArrayNode artifacts = objectMapper.createArrayNode();
        artifacts.add(artifactObj);

        // 🔹 Build user history message
        ObjectNode userTextPart = objectMapper.createObjectNode();
        userTextPart.put("kind", "text");
        userTextPart.put("text", userPrompt);
        userTextPart.putNull("data");
        userTextPart.putNull("file_url");

        ArrayNode userParts = objectMapper.createArrayNode();
        userParts.add(userTextPart);

        ObjectNode userHistoryMessage = objectMapper.createObjectNode();
        userHistoryMessage.put("kind", "message");
        userHistoryMessage.put("role", "user");
        userHistoryMessage.set("parts", userParts);
        userHistoryMessage.put("messageId", userMessageId);
        userHistoryMessage.putNull("taskId");
        userHistoryMessage.putNull("metadata");

        // 🔹 Build agent history message
        ObjectNode agentHistoryMessage = objectMapper.createObjectNode();
        agentHistoryMessage.put("kind", "message");
        agentHistoryMessage.put("role", "agent");
        agentHistoryMessage.set("parts", messageParts);
        agentHistoryMessage.put("messageId", messageId);
        agentHistoryMessage.put("taskId", taskId);
        agentHistoryMessage.putNull("metadata");

        // 🔹 Build history array
        ArrayNode history = objectMapper.createArrayNode();
        history.add(userHistoryMessage);
        history.add(agentHistoryMessage);

        // 🔹 Build result object
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", taskId);
        result.put("contextId", contextId);
        result.set("status", statusObj);
        result.set("artifacts", artifacts);
        result.set("history", history);
        result.put("kind", "task");

        // 🔹 Build final response
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        response.putNull("error");

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    }

    private void processAndSendResult(String userPrompt, String taskId, String contextId, JSONObject configuration) {
        try {
            // Generate PDF
            String base64Pdf = aiService.generatePRDPdfBase64(userPrompt);
            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

            // Upload to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", "prd_" + UUID.randomUUID(),
                            "format", "pdf"
                    )
            );

            String fileUrl = ((String) uploadResult.get("secure_url")).replace("/upload/", "/raw/upload/");
            if (!fileUrl.endsWith(".pdf")) {
                fileUrl += ".pdf";
            }

            // Build completed response
            ObjectNode completedResponse = buildCompletedResponse(taskId, contextId, fileUrl, userPrompt);

            // Send to webhook
            JSONObject pushConfig = configuration.getJSONObject("pushNotificationConfig");
            String webhookUrl = pushConfig.getString("url");
            String token = pushConfig.getString("token");

            sendWebhookNotification(webhookUrl, token, completedResponse);
        } catch (Exception e) {
            System.err.println("Error processing and sending result: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ObjectNode buildCompletedResponse(String taskId, String contextId, String fileUrl, String userPrompt) {
        String messageId = "msg-" + UUID.randomUUID();
        String artifactId = "artifact-" + UUID.randomUUID();
        String userMessageId = "msg-" + UUID.randomUUID();

        // 🔹 Build text part
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("kind", "text");
        textPart.put("text", "📄 Your PRD has been generated successfully! Click below to download it:");
        textPart.putNull("data");
        textPart.putNull("file_url");

        // 🔹 Build file part
        ObjectNode filePart = objectMapper.createObjectNode();
        filePart.put("kind", "file");
        filePart.put("file_url", fileUrl);
        filePart.put("file_name", "Product_Requirement_Document.pdf");
        filePart.put("mime_type", "application/pdf");
        filePart.putNull("data");

        // 🔹 Build message parts array
        ArrayNode messageParts = objectMapper.createArrayNode();
        messageParts.add(textPart);
        messageParts.add(filePart);

        // 🔹 Build message object
        ObjectNode messageObj = objectMapper.createObjectNode();
        messageObj.put("kind", "message");
        messageObj.put("role", "agent");
        messageObj.set("parts", messageParts);
        messageObj.put("messageId", messageId);
        messageObj.put("taskId", taskId);
        messageObj.putNull("metadata");

        // 🔹 Build status object
        ObjectNode statusObj = objectMapper.createObjectNode();
        statusObj.put("state", "completed");
        statusObj.put("timestamp", Instant.now().toString());
        statusObj.set("message", messageObj);

        // 🔹 Build artifact parts array
        ArrayNode artifactParts = objectMapper.createArrayNode();
        artifactParts.add(filePart);

        // 🔹 Build artifact object
        ObjectNode artifactObj = objectMapper.createObjectNode();
        artifactObj.put("artifactId", artifactId);
        artifactObj.put("name", "PRDDocument");
        artifactObj.set("parts", artifactParts);

        // 🔹 Build artifacts array
        ArrayNode artifacts = objectMapper.createArrayNode();
        artifacts.add(artifactObj);

        // 🔹 Build user history message
        ObjectNode userTextPart = objectMapper.createObjectNode();
        userTextPart.put("kind", "text");
        userTextPart.put("text", userPrompt);
        userTextPart.putNull("data");
        userTextPart.putNull("file_url");

        ArrayNode userParts = objectMapper.createArrayNode();
        userParts.add(userTextPart);

        ObjectNode userHistoryMessage = objectMapper.createObjectNode();
        userHistoryMessage.put("kind", "message");
        userHistoryMessage.put("role", "user");
        userHistoryMessage.set("parts", userParts);
        userHistoryMessage.put("messageId", userMessageId);
        userHistoryMessage.putNull("taskId");
        userHistoryMessage.putNull("metadata");

        // 🔹 Build agent history message
        ObjectNode agentHistoryMessage = objectMapper.createObjectNode();
        agentHistoryMessage.put("kind", "message");
        agentHistoryMessage.put("role", "agent");
        agentHistoryMessage.set("parts", messageParts);
        agentHistoryMessage.put("messageId", messageId);
        agentHistoryMessage.put("taskId", taskId);
        agentHistoryMessage.putNull("metadata");

        // 🔹 Build history array
        ArrayNode history = objectMapper.createArrayNode();
        history.add(userHistoryMessage);
        history.add(agentHistoryMessage);

        // 🔹 Build result object
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", taskId);
        result.put("contextId", contextId);
        result.set("status", statusObj);
        result.set("artifacts", artifacts);
        result.set("history", history);
        result.put("kind", "task");

        // 🔹 Build final response (for webhook, no top-level wrapper needed)
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", taskId);
        response.set("result", result);
        response.putNull("error");

        return response;
    }

    private void sendWebhookNotification(String url, String token, ObjectNode payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload),
                    headers
            );

            restTemplate.postForEntity(url, request, String.class);
            System.out.println("Webhook notification sent successfully to: " + url);
        } catch (Exception e) {
            System.err.println("Failed to send webhook notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendErrorNotification(JSONObject configuration, String taskId, Exception error) {
        try {
            JSONObject pushConfig = configuration.getJSONObject("pushNotificationConfig");
            String webhookUrl = pushConfig.getString("url");
            String token = pushConfig.getString("token");

            ObjectNode errorResponse = buildErrorResponseForWebhook(taskId, error);
            sendWebhookNotification(webhookUrl, token, errorResponse);
        } catch (Exception e) {
            System.err.println("Failed to send error notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ObjectNode buildErrorResponseForWebhook(String taskId, Exception error) {
        ObjectNode errorObj = objectMapper.createObjectNode();
        errorObj.put("code", -32603);
        errorObj.put("message", "Failed to process request: " + error.getMessage());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", taskId);
        response.putNull("result");
        response.set("error", errorObj);

        return response;
    }

    private String buildErrorResponse(String id, Exception error) {
        try {
            ObjectNode errorObj = objectMapper.createObjectNode();
            errorObj.put("code", -32603);
            errorObj.put("message", "Failed to process request: " + error.getMessage());

            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.putNull("result");
            response.set("error", errorObj);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }
}