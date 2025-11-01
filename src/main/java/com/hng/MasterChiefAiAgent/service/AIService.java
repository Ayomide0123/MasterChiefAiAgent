package com.hng.MasterChiefAiAgent.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class AIService {

    private final Client client;

    public AIService() {
        // Initialize Gemini client (reads API key from environment variable automatically)
        this.client = new Client();
    }

    public byte[] generatePRDAsPDF(String prompt) {
        try {
            // Step 1: Ask Gemini to generate text
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.0-flash",
                    prompt,
                    null
            );

            String generatedText = response.text();

            // Convert generated text to a PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Product Requirements Document (PRD)").setBold().setFontSize(16));
            document.add(new Paragraph("\n"));
            document.add(new Paragraph(generatedText));

            document.close();

            // Step 3: Return PDF bytes
            return baos.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return ("Error generating PRD: " + e.getMessage()).getBytes();
        }
    }
}
