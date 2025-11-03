package com.hng.MasterChiefAiAgent.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hng.MasterChiefAiAgent.service.AIService;
import org.springframework.web.bind.annotation.*;
import org.json.JSONObject;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/a2a")
public class A2AController {

    private static final Logger logger = LoggerFactory.getLogger(A2AController.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final AIService aiService;
    private final Cloudinary cloudinary;
    private final ObjectMapper objectMapper;

    public A2AController(AIService aiService, Cloudinary cloudinary) {
        this.aiService = aiService;
        this.cloudinary = cloudinary;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/agent/prdAgent")
    public String handleA2ARequest(@RequestBody String requestBody) {
        JSONObject request = null;
        String requestId = UUID.randomUUID().toString();

        try {
            // Parse request
            request = new JSONObject(requestBody);
            requestId = request.optString("id", requestId);

            // Validate request structure
            if (!request.has("params")) {
                return buildErrorResponse(requestId, -32602, "Missing 'params' in request");
            }

            JSONObject params = request.getJSONObject("params");
            if (!params.has("message")) {
                return buildErrorResponse(requestId, -32602, "Missing 'message' in params");
            }

            JSONObject message = params.getJSONObject("message");
            if (!message.has("parts") || message.getJSONArray("parts").length() == 0) {
                return buildErrorResponse(requestId, -32602, "Missing or empty 'parts' in message");
            }

            String userPrompt = message.getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            if (userPrompt == null || userPrompt.trim().isEmpty()) {
                return buildErrorResponse(requestId, -32602, "Empty user prompt");
            }

            // Generate PDF
            String base64Pdf;
            try {
                base64Pdf = aiService.generatePRDPdfBase64(userPrompt);
                if (base64Pdf == null || base64Pdf.isEmpty()) {
                    return buildErrorResponse(requestId, -32001, "Failed to generate PDF: Empty response from AI service");
                }
            } catch (Exception e) {
                logger.error("Error generating PDF: {}", e.getMessage(), e);
                return buildErrorResponse(requestId, -32001, "Failed to generate PDF: " + e.getMessage());
            }

            // Decode PDF
            byte[] pdfBytes;
            try {
                pdfBytes = Base64.getDecoder().decode(base64Pdf);
            } catch (IllegalArgumentException e) {
                logger.error("Error decoding Base64 PDF: {}", e.getMessage(), e);
                return buildErrorResponse(requestId, -32002, "Invalid PDF format: Failed to decode Base64");
            }

            // Upload to Cloudinary with retry logic
            String fileUrl;
            try {
                fileUrl = uploadToCloudinaryWithRetry(pdfBytes);
            } catch (RateLimitException e) {
                logger.error("Rate limit exceeded: {}", e.getMessage(), e);
                return buildErrorResponse(requestId, -32003, "Rate limit exceeded. Please try again later.");
            } catch (CloudinaryUploadException e) {
                logger.error("Cloudinary upload failed: {}", e.getMessage(), e);
                return buildErrorResponse(requestId, -32004, "File upload failed: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error during upload: {}", e.getMessage(), e);
                return buildErrorResponse(requestId, -32004, "File upload failed: " + e.getMessage());
            }

            // Generate response
            return buildSuccessResponse(requestId, userPrompt, fileUrl);

        } catch (JSONException e) {
            logger.error("Invalid JSON in request: {}", e.getMessage(), e);
            return buildErrorResponse(requestId, -32700, "Invalid JSON: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error processing request: {}", e.getMessage(), e);
            return buildErrorResponse(requestId, -32603, "Internal error: " + e.getMessage());
        }
    }

    private String uploadToCloudinaryWithRetry(byte[] pdfBytes) throws Exception {
        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_RETRIES) {
            try {
                Map uploadResult = cloudinary.uploader().upload(
                        pdfBytes,
                        ObjectUtils.asMap(
                                "resource_type", "raw",
                                "public_id", "prd_" + UUID.randomUUID(),
                                "format", "pdf",
                                "type", "upload",
                                "access_type", "anonymous"
                        )
                );

                String fileUrl = (String) uploadResult.get("secure_url");
                if (fileUrl == null || fileUrl.isEmpty()) {
                    throw new CloudinaryUploadException("No URL returned from Cloudinary");
                }

                if (!fileUrl.endsWith(".pdf")) {
                    fileUrl += ".pdf";
                }

                logger.info("Successfully uploaded PDF to Cloudinary: {}", fileUrl);
                return fileUrl;

            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                // Check for rate limit
                if (errorMsg.contains("rate limit") || errorMsg.contains("429") ||
                        errorMsg.contains("too many requests")) {
                    retries++;
                    if (retries < MAX_RETRIES) {
                        logger.warn("Rate limit hit, retrying ({}/{})", retries, MAX_RETRIES);
                        Thread.sleep(RETRY_DELAY_MS * retries); // Exponential backoff
                        continue;
                    }
                    throw new RateLimitException("Cloudinary rate limit exceeded after " + MAX_RETRIES + " retries");
                }

                // Check for authentication errors
                if (errorMsg.contains("401") || errorMsg.contains("unauthorized") ||
                        errorMsg.contains("authentication")) {
                    throw new CloudinaryUploadException("Authentication failed: Check Cloudinary credentials");
                }

                // Check for quota/storage errors
                if (errorMsg.contains("quota") || errorMsg.contains("storage limit")) {
                    throw new CloudinaryUploadException("Storage quota exceeded");
                }

                // Check for file size errors
                if (errorMsg.contains("file size") || errorMsg.contains("too large")) {
                    throw new CloudinaryUploadException("File size exceeds limit");
                }

                // Retry on network errors
                if (errorMsg.contains("timeout") || errorMsg.contains("connection") ||
                        errorMsg.contains("network")) {
                    retries++;
                    if (retries < MAX_RETRIES) {
                        logger.warn("Network error, retrying ({}/{})", retries, MAX_RETRIES);
                        Thread.sleep(RETRY_DELAY_MS * retries);
                        continue;
                    }
                }

                // Other errors - don't retry
                throw new CloudinaryUploadException(e.getMessage());
            }
        }

        throw lastException != null ? lastException :
                new CloudinaryUploadException("Upload failed after " + MAX_RETRIES + " retries");
    }

    private String buildSuccessResponse(String requestId, String userPrompt, String fileUrl) {
        try {
            String taskId = "task-" + UUID.randomUUID();
            String contextId = "ctx-" + UUID.randomUUID();
            String messageId = "msg-" + UUID.randomUUID();
            String artifactId = "artifact-" + UUID.randomUUID();
            String userMessageId = "msg-" + UUID.randomUUID();

            // Build text part
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("kind", "text");
            textPart.put("text", "📄 Your PRD has been generated successfully! Click the link to download it:\n\n" + fileUrl);
            textPart.putNull("data");
            textPart.put("file_url", fileUrl);

            // Build parts array
            ArrayNode messageParts = objectMapper.createArrayNode();
            messageParts.add(textPart);

            // Build message object
            ObjectNode messageObj = objectMapper.createObjectNode();
            messageObj.put("kind", "message");
            messageObj.put("role", "agent");
            messageObj.set("parts", messageParts);
            messageObj.put("messageId", messageId);
            messageObj.put("taskId", taskId);
            messageObj.putNull("metadata");

            // Build status object
            ObjectNode statusObj = objectMapper.createObjectNode();
            statusObj.put("state", "completed");
            statusObj.put("timestamp", Instant.now().toString());
            statusObj.set("message", messageObj);

            // Build artifact parts array
            ArrayNode artifactParts = objectMapper.createArrayNode();
            artifactParts.add(textPart);

            // Build artifact object
            ObjectNode artifactObj = objectMapper.createObjectNode();
            artifactObj.put("artifactId", artifactId);
            artifactObj.put("name", "PRDDocument");
            artifactObj.set("parts", artifactParts);

            // Build artifacts array
            ArrayNode artifacts = objectMapper.createArrayNode();
            artifacts.add(artifactObj);

            // Build user history message
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

            // Build agent history message
            ObjectNode agentHistoryMessage = objectMapper.createObjectNode();
            agentHistoryMessage.put("kind", "message");
            agentHistoryMessage.put("role", "agent");
            agentHistoryMessage.set("parts", messageParts);
            agentHistoryMessage.put("messageId", messageId);
            agentHistoryMessage.put("taskId", taskId);
            agentHistoryMessage.putNull("metadata");

            // Build history array
            ArrayNode history = objectMapper.createArrayNode();
            history.add(userHistoryMessage);
            history.add(agentHistoryMessage);

            // Build result object
            ObjectNode result = objectMapper.createObjectNode();
            result.put("id", taskId);
            result.put("contextId", contextId);
            result.set("status", statusObj);
            result.set("artifacts", artifacts);
            result.set("history", history);
            result.put("kind", "task");

            // Build final response
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);
            response.set("result", result);
            response.putNull("error");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (Exception e) {
            logger.error("Error building success response: {}", e.getMessage(), e);
            return buildErrorResponse(requestId, -32603, "Error building response: " + e.getMessage());
        }
    }

    private String buildErrorResponse(String requestId, int code, String message) {
        try {
            ObjectNode errorObj = objectMapper.createObjectNode();
            errorObj.put("code", code);
            errorObj.put("message", message);

            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("id", requestId);
            errorResponse.putNull("result");
            errorResponse.set("error", errorObj);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorResponse);
        } catch (Exception e) {
            logger.error("Error building error response: {}", e.getMessage(), e);
            return "{\"jsonrpc\":\"2.0\",\"id\":\"" + requestId + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    // Custom exceptions
    private static class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
    }

    private static class CloudinaryUploadException extends Exception {
        public CloudinaryUploadException(String message) {
            super(message);
        }
    }
}