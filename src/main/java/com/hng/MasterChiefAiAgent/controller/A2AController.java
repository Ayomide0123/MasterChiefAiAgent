package com.hng.MasterChiefAiAgent.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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

    public A2AController(AIService aiService, Cloudinary cloudinary) {
        this.aiService = aiService;
        this.cloudinary = cloudinary;
    }

    @PostMapping("/agent/prdAgent")
    public String handleA2ARequest(@RequestBody String requestBody) {
        try {
            JSONObject request = new JSONObject(requestBody);
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

            // 🔹 Build Telex-compliant response JSON
            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            String taskId = "task-" + UUID.randomUUID();
            String contextId = "ctx-" + UUID.randomUUID();
            String messageId = "msg-" + UUID.randomUUID();
            String artifactId = "artifact-" + UUID.randomUUID();

            JSONObject filePart = new JSONObject()
                    .put("kind", "file")
                    .put("file_url", fileUrl)
                    .put("file_name", "Product_Requirement_Document.pdf")
                    .put("mime_type", "application/pdf");

            JSONObject textPart = new JSONObject()
                    .put("kind", "text")
                    .put("text", "📄 Your PRD has been generated successfully! Click below to download it:");

            JSONObject messageObj = new JSONObject()
                    .put("kind", "message")
                    .put("role", "agent")
                    .put("parts", new org.json.JSONArray().put(textPart).put(filePart))
                    .put("messageId", messageId)
                    .put("taskId", taskId);

            JSONObject statusObj = new JSONObject()
                    .put("state", "completed")
                    .put("timestamp", Instant.now().toString())
                    .put("message", messageObj);

            JSONObject artifactObj = new JSONObject()
                    .put("artifactId", artifactId)
                    .put("name", "PRDDocument")
                    .put("parts", new org.json.JSONArray().put(filePart));

            JSONObject result = new JSONObject()
                    .put("id", taskId)
                    .put("contextId", contextId)
                    .put("kind", "task")
                    .put("status", statusObj)
                    .put("artifacts", new org.json.JSONArray().put(artifactObj));

            response.put("result", result);

            return response.toString(2);

        } catch (Exception e) {
            return new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("error", new JSONObject()
                            .put("code", -32603)
                            .put("message", "Failed to process request: " + e.getMessage()))
                    .toString(2);
        }
    }
}
