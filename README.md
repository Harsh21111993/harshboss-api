# Harsh-Boss API (REST + Agent + MCP Client)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-blue)](https://spring.io/projects/spring-ai)
[![Google Gemini](https://img.shields.io/badge/Gemini-2.5--flash-orange)](https://ai.google.dev/)

Standalone **REST API + Spring AI agent** for the Harsh-Boss AI Productivity Workspace.
This project is an **MCP client** — it consumes tools from the Harsh-Boss MCP server.

## Quick start

```bash
# Prerequisites:
#   1. PostgreSQL running (atlas_db)
#   2. Harsh-Boss MCP server running on http://localhost:8081

# 1. Start PostgreSQL
docker compose up -d

# 2. Set Gemini API key
export GEMINI_API_KEY="your-gemini-api-key"

# 3. Run
mvn clean spring-boot:run
# → http://localhost:8080
```

## What it does

- **REST API** — emails, calendar, approvals, dashboard, OAuth2 sync
- **Spring AI ChatClient agent** — conversational AI that calls MCP tools autonomously
- **Advanced advisors** — security (injection + PII), memory (JDBC), semantic cache, LLMOps tracing
- **OAuth2** — connects Google Gmail, syncs real emails
- **Semantic search** — PGVector embeddings for "emails about the Q3 budget"
- **MCP client** — consumes tools from the Harsh-Boss MCP server (:8081)

## Key endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/dashboard/stats` | Dashboard stat cards |
| POST | `/api/dashboard/daily-brief` | AI daily briefing |
| GET | `/api/emails?includeSpam=true` | List emails |
| POST | `/api/emails/{id}/analyze` | AI triage one email |
| POST | `/api/emails/analyze-all` | AI triage all emails |
| POST | `/api/emails/search` | Semantic search (PGVector) |
| GET | `/api/calendar/events` | Unified calendar |
| POST | `/api/calendar/propose` | Propose meeting (conflict detection + auto-email) |
| GET | `/api/approvals` | Pending + resolved approvals |
| POST | `/api/approvals/{id}/decide` | Approve/decline + notify email |
| POST | `/api/agent/chat` | **Ask Harsh-Boss** — conversational AI agent |
| GET | `/api/oauth/providers` | OAuth2 connection status |
| GET | `/api/oauth/connect/google` | Start Google OAuth2 flow |
| POST | `/api/sync/emails?provider=google` | Sync real emails from Gmail |

## Configuration

`src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/atlas_db
    username: atlas
    password: atlas

  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY}
        chat:
          options:
            model: gemini-2.5-flash
    mcp:
      client:
        sse:
          connections:
            atlas-mcp-server:
              url: http://localhost:8081   # ← Harsh-Boss MCP server URL

atlas:
  oauth:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      redirect-uri: http://localhost:8080/api/oauth/callback/google
```

## Environment variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `GEMINI_API_KEY` | Yes | — | Google Gemini API key ([get one](https://aistudio.google.com/apikey)) |
| `GOOGLE_CLIENT_ID` | For OAuth2 | (in yml) | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | For OAuth2 | (in yml) | Google OAuth2 client secret |
| `ATLAS_MCP_SERVER_URL` | No | `http://localhost:8081` | MCP server URL |

## Dependencies on other projects

This project **connects to** the Harsh-Boss MCP server (port 8081) for tool calls.
The MCP server must be running for the agent's tools to work.

If the MCP server is down, the REST API still works — only the agent's tool
calls will fail (gracefully).

## Tech stack

- **Spring Boot** 4.1.0
- **Spring AI** 2.0.0 GA (Google Gemini + PGVector + MCP client + chat memory)
- **PostgreSQL** 16 + Flyway
- **Micrometer** + Prometheus + OpenTelemetry
- **Java** 21

## Git

```bash
git init
git add .
git commit -m "Initial commit: Harsh-Boss API"
git remote add origin <your-repo-url>
git push -u origin main
```
