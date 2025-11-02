package com.hng.MasterChiefAiAgent.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hng.MasterChiefAiAgent.service.AIService;
import org.springframework.web.bind.annotation.*;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.file.Paths;
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

            // Extract essentials
            String id = request.optString("id", UUID.randomUUID().toString());
            JSONObject params = request.getJSONObject("params");
            JSONObject message = params.getJSONObject("message");
            String userPrompt = message.getJSONArray("parts").getJSONObject(0).getString("text");

            // Generate PRD PDF as Base64
            String base64Pdf = aiService.generatePRDPdfBase64(userPrompt);

            // Decode Base64 to bytes
            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);


            // Upload to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "resource_type", "raw",  // important for non-image files
                            "public_id", "prd_" + UUID.randomUUID()
                    )
            );

            // Get secure URL
            String fileUrl = (String) uploadResult.get("secure_url");




            // Generate unique filename
            String filename = "prd-" + UUID.randomUUID() + ".pdf";

//            String filePath = Paths.get("src/main/resources/static/files", filename).toString();
//
//            // Save PDF to static folder
//            try (FileOutputStream fos = new FileOutputStream(filePath)) {
//                fos.write(pdfBytes);
//            }
//
//            // The public file URL (adjust domain if deployed)
//            String fileUrl = "http://localhost:8080/files/" + filename;

            // Build JSON-RPC response for Telex
            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            JSONObject result = new JSONObject();
            String taskId = "task-" + UUID.randomUUID();
            String contextId = "ctx-" + UUID.randomUUID();
            result.put("id", taskId);
            result.put("contextId", contextId);

            // ----- STATUS -----
            JSONObject status = new JSONObject();
            status.put("state", "completed");
            status.put("timestamp", Instant.now().toString());

            JSONObject agentMessage = new JSONObject();
            agentMessage.put("messageId", "msg-" + UUID.randomUUID());
            agentMessage.put("role", "agent");
            agentMessage.put("kind", "message");
            agentMessage.put("taskId", taskId);

            org.json.JSONArray messageParts = new org.json.JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("kind", "text");
            textPart.put("text", "Here is your PRD document. You can download it below 👇");

            JSONObject filePart = new JSONObject();
            filePart.put("kind", "file");
            filePart.put("file_url", fileUrl);

            messageParts.put(textPart);
            messageParts.put(filePart);
            agentMessage.put("parts", messageParts);

            status.put("message", agentMessage);
            result.put("status", status);

            // ----- ARTIFACTS -----
            org.json.JSONArray artifacts = new org.json.JSONArray();

            JSONObject artifactText = new JSONObject();
            artifactText.put("artifactId", "artifact-" + UUID.randomUUID());
            artifactText.put("name", "prdAgentResponse");
            artifactText.put("parts", new org.json.JSONArray()
                    .put(new JSONObject()
                            .put("kind", "text")
                            .put("text", "The PRD has been generated successfully.")));

            JSONObject artifactFile = new JSONObject();
            artifactFile.put("artifactId", "artifact-" + UUID.randomUUID());
            artifactFile.put("name", "PRDDocument");
            artifactFile.put("parts", new org.json.JSONArray()
                    .put(new JSONObject()
                            .put("kind", "file")
                            .put("file_url", fileUrl)));

            artifacts.put(artifactText);
            artifacts.put(artifactFile);

            result.put("artifacts", artifacts);
            result.put("history", new org.json.JSONArray());
            result.put("kind", "task");

            response.put("result", result);

            return response.toString(2);

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
