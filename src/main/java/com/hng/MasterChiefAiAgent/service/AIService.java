package com.hng.MasterChiefAiAgent.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
public class AIService {

    private final Client client;

    public AIService() {
        this.client = new Client();
    }

    public String generatePRDPdfBase64(String prompt) {
        try {
            // Generate text from Gemini
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.0-flash",
                    prompt,
                    null
            );

            String generatedText = response.text();

            // Create PDF in memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Product Requirements Document (PRD)")
                    .setBold()
                    .setFontSize(16));
            document.add(new Paragraph("\n"));
            document.add(new Paragraph(generatedText));

            document.close();

            // Convert PDF to Base64
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return Base64.getEncoder().encodeToString(
                    ("Error generating PRD: " + e.getMessage()).getBytes()
            );
        }
    }
}
