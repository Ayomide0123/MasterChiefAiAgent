# MasterChief AI Agent - PRD Generator

A Spring Boot AI agent that generates Product Requirements Documents (PRDs) using Google's Gemini AI and returns them as downloadable PDFs via Cloudinary hosting.

## Features

- AI-powered PRD generation using Google Gemini 2.0 Flash
- Automatic PDF document creation with iText
- Cloudinary integration for file hosting
- Agent-to-Agent (A2A) protocol support
- RESTful API endpoint

---

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 17** or higher
- **Maven 3.6+** or Gradle
- **Git** (for cloning the repository)

---

## Dependencies

This project uses the following main dependencies:

```xml
<!-- Spring Boot Starter Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Google Generative AI SDK -->
<dependency>
    <groupId>com.google.genai</groupId>
    <artifactId>google-generative-ai</artifactId>
    <version>latest</version>
</dependency>

<!-- iText PDF (for PDF generation) -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>7.2.5</version>
    <type>pom</type>
</dependency>

<!-- Cloudinary SDK -->
<dependency>
    <groupId>com.cloudinary</groupId>
    <artifactId>cloudinary-http44</artifactId>
    <version>1.36.0</version>
</dependency>

<!-- JSON Processing -->
<dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20230227</version>
</dependency>

<!-- Jackson (included with Spring Boot) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## Environment Variables

Create a `.env` file in the project root or set the following environment variables:

### Required Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `GOOGLE_API_KEY` | Your Google Gemini API key | `AIza...` |
| `CLOUDINARY_URL` | Your Cloudinary connection URL | `cloudinary://API_KEY:API_SECRET@CLOUD_NAME` |

---

### How to Obtain API Keys

#### Google Gemini API Key
1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Sign in with your Google account
3. Click "Create API Key"
4. Copy the generated key
5. Set as environment variable: `export GOOGLE_API_KEY=your_key_here`

#### Cloudinary URL
1. Sign up at [Cloudinary](https://cloudinary.com/)
2. Go to your Dashboard
3. Copy the "API Environment variable" (starts with `cloudinary://`)
4. Set as environment variable: `export CLOUDINARY_URL=your_cloudinary_url`

---

## Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/Ayomide0123/MasterChiefAiAgent.git
cd MasterChiefAiAgent
```

### 2. Set Environment Variables

**On Linux/Mac:**
```bash
export GOOGLE_API_KEY=your_google_api_key
export CLOUDINARY_URL=cloudinary://api_key:api_secret@cloud_name
```

**On Windows (Command Prompt):**
```cmd
set GOOGLE_API_KEY=your_google_api_key
set CLOUDINARY_URL=cloudinary://api_key:api_secret@cloud_name
```

**On Windows (PowerShell):**
```powershell
$env:GOOGLE_API_KEY="your_google_api_key"
$env:CLOUDINARY_URL="cloudinary://api_key:api_secret@cloud_name"
```

### 3. Configure application.properties

Create or update `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# Cloudinary Configuration
CLOUDINARY_URL=${CLOUDINARY_URL}

# Google AI Configuration (accessed via environment variable)
# The Google Client SDK will automatically read GOOGLE_API_KEY from environment
```

### 4. Install Dependencies

**Using Maven:**
```bash
mvn clean install
```

**Using Gradle:**
```bash
./gradlew build
```
---

## Running the Application

### Run Locally with Maven

```bash
mvn spring-boot:run
```

### Run Locally with Gradle

```bash
./gradlew bootRun
```

### Run the JAR file

```bash
# Build the JAR
mvn clean package

# Run it
java -jar target/MasterChiefAiAgent-0.0.1-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

---

## API Usage

### Endpoint

```
POST http://localhost:8080/a2a/agent/prdAgent
```

### Request Format (A2A Protocol)

```json
{
  "jsonrpc": "2.0",
  "id": "request-123",
  "params": {
    "message": {
      "parts": [
        {
          "text": "Create a PRD for a mobile fitness tracking app"
        }
      ]
    }
  }
}
```

### Response Format

```json
{
  "jsonrpc": "2.0",
  "id": "request-123",
  "result": {
    "id": "task-uuid",
    "contextId": "ctx-uuid",
    "status": {
      "state": "completed",
      "timestamp": "2025-11-03T10:30:00Z",
      "message": {
        "kind": "message",
        "role": "agent",
        "parts": [
          {
            "kind": "text",
            "text": "📄 Your PRD has been generated successfully! Click below to download it:"
          },
          {
            "kind": "file",
            "file_url": "https://res.cloudinary.com/.../Product_Requirement_Document.pdf",
            "file_name": "Product_Requirement_Document.pdf",
            "mime_type": "application/pdf"
          }
        ]
      }
    },
    "artifacts": [...],
    "history": [...]
  }
}
```

### Example cURL Request

```bash
curl -X POST http://localhost:8080/a2a/agent/prdAgent \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1",
    "params": {
      "message": {
        "parts": [
          {
            "text": "Generate a PRD for an e-commerce platform"
          }
        ]
      }
    }
  }'
```
---

## Project Structure

```
src/main/java/com/hng/MasterChiefAiAgent/
├── config/
│   └── CloudinaryConfig.java          # Cloudinary bean configuration
├── controller/
│   └── A2AController.java             # REST API endpoint
├── service/
│   └── AIService.java                 # Gemini AI & PDF generation
└── utils/
    └── CloudinaryUploadService.java   # PDF upload utility
```
---

### Testing

Run tests with:
```bash
mvn test
```

---

## Author

**Oyetimehin Ayomide**
* 📧 [oyetimehin31@gmail.com](mailto:oyetimehin31@gmail.com)
* 💻 Backend Stack: Java / Spring Boot