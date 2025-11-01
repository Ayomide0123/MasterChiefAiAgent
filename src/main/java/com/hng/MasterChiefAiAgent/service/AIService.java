package com.hng.MasterChiefAiAgent.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

@Service
public class AIService {

    private final Client client;

    public AIService() {
        // Initialize Gemini client with your API key from environment variable
        this.client = new Client();
    }

    public String generatePRD(String prompt) {
        try {
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.0-flash",
                    prompt,
                    null
            );

            return response.text();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating PRD: " + e.getMessage();
        }
    }
}