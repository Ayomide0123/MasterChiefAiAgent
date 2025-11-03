package com.hng.MasterChiefAiAgent.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hng.MasterChiefAiAgent.service.AIService;
import org.springframework.web.bind.annotation.*;
import org.json.JSONObject;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/a2a")
public class A2AController {

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
        try {
            request = new JSONObject(requestBody);
            String id = request.optString("id", UUID.randomUUID().toString());

            JSONObject params = request.getJSONObject("params");
            JSONObject message = params.getJSONObject("message");
            String userPrompt = message.getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            // 🔹 Generate PDF as Base64
            String base64Pdf = aiService.generatePRDPdfBase64(userPrompt);
            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

            // 🔹 Upload PDF to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", "prd_" + UUID.randomUUID(),
                            "format", "pdf"
                    )
            );

            // 🔹 Retrieve URL and ensure .pdf extension
            String fileUrl = ((String) uploadResult.get("secure_url")).replace("/upload/", "/raw/upload/");
            if (!fileUrl.endsWith(".pdf")) {
                fileUrl += ".pdf";
            }

            // 🔹 Generate IDs
            String taskId = "task-" + UUID.randomUUID();
            String contextId = "ctx-" + UUID.randomUUID();
            String messageId = "msg-" + UUID.randomUUID();
            String artifactId = "artifact-" + UUID.randomUUID();
            String userMessageId = "msg-" + UUID.randomUUID();

            // 🔹 Build text part (kind → text → data → file_url)
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("kind", "text");
            textPart.put("text", "📄 Your PRD has been generated successfully! Click below to download it:" + fileUrl);
            textPart.putNull("data");
            textPart.put("file_url", fileUrl);

            // 🔹 Build file part (kind → file_url → file_name → mime_type → data)
//            ObjectNode filePart = objectMapper.createObjectNode();
//            filePart.put("kind", "file");
//            filePart.put("file_url", fileUrl);
//            filePart.put("file_name", "Product_Requirement_Document.pdf");
//            filePart.put("mime_type", "application/pdf");
//            filePart.putNull("data");

            // 🔹 Build parts array
            ArrayNode messageParts = objectMapper.createArrayNode();
            messageParts.add(textPart);
//            messageParts.add(filePart);

            // 🔹 Build message object (kind → role → parts → messageId → taskId → metadata)
            ObjectNode messageObj = objectMapper.createObjectNode();
            messageObj.put("kind", "message");
            messageObj.put("role", "agent");
            messageObj.set("parts", messageParts);
            messageObj.put("messageId", messageId);
            messageObj.put("taskId", taskId);
            messageObj.putNull("metadata");

            // 🔹 Build status object (state → timestamp → message)
            ObjectNode statusObj = objectMapper.createObjectNode();
            statusObj.put("state", "completed");
            statusObj.put("timestamp", Instant.now().toString());
            statusObj.set("message", messageObj);

            // 🔹 Build artifact parts array
            ArrayNode artifactParts = objectMapper.createArrayNode();
              artifactParts.add(textPart);
//            artifactParts.add(filePart);

            // 🔹 Build artifact object (artifactId → name → parts)
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

            // 🔹 Build result object (id → contextId → status → artifacts → history → kind)
            ObjectNode result = objectMapper.createObjectNode();
            result.put("id", taskId);
            result.put("contextId", contextId);
            result.set("status", statusObj);
            result.set("artifacts", artifacts);
            result.set("history", history);
            result.put("kind", "task");

            // 🔹 Build final response (jsonrpc → id → result → error)
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.set("result", result);
            response.putNull("error");

            // 🔹 Return pretty-printed JSON
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);

        } catch (Exception e) {
            try {
                ObjectNode errorObj = objectMapper.createObjectNode();
                errorObj.put("code", -32603);
                errorObj.put("message", "Failed to process request: " + e.getMessage());

                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.put("id", request != null ? request.optString("id", UUID.randomUUID().toString()) : UUID.randomUUID().toString());
                errorResponse.putNull("result");
                errorResponse.set("error", errorObj);

                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
            }
        }
    }
}