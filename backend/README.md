Backend Kotlin (Ktor)

Responsável por:
- TranscriptionService
- YouTubeClient
- PostProcessor
- ExportModule

Arquitetura em camadas.

# 🎬 Creator Content Hub (Backend)

Backend Kotlin para processamento, transformação e exportação de conteúdo multimídia a partir de vídeos (YouTube), com foco em pipeline escalável e arquitetura limpa.

---

## 🧠 Visão do Projeto

O objetivo do sistema é evoluir para um **pipeline completo de mídia**, permitindo:

- Ingestão de vídeos (YouTube)
- Extração de áudio
- Transcrição automatizada
- Edição de conteúdo
- Exportação em múltiplos formatos (TXT, SRT, DOCX, vídeo)

---

## 🚀 Estado Atual

### ✔ Implementado

- API HTTP com Ktor
- Arquitetura Hexagonal (Ports & Adapters)
- Processamento de texto
- Exportação TXT com download real
- Integração externa (adapter Python)
- Observabilidade completa:
    - Request ID (MDC)
    - Logging estruturado
    - Métricas (latência + contagem)
    - Endpoint `/metrics`

### ⚠ Em andamento

- Pipeline de ingestão de mídia (YouTube)
- Processamento assíncrono de jobs

### 🔜 Planejado

- Transcrição automática (Whisper / Python)
- Exportação SRT (legendas)
- Exportação DOCX
- Corte de vídeo (FFmpeg)
- Interface de edição (futuro)

---

## 🏗️ Arquitetura

O sistema segue **Arquitetura Hexagonal**:



### Princípios

- UseCase = orquestrador
- Ports = contratos
- Adapters = integrações externas
- Controllers = camada HTTP
- Domain = lógica pura (quando aplicável)

---

## 🔁 Fluxo do Sistema (Atual)
[INPUT TEXTO]
↓
/process
↓
processamento
↓
/export/txt
↓
download TXT


---

## 🎯 Fluxo Alvo (Pipeline de Mídia)
YouTube URL
↓
[INGESTÃO]
↓
áudio/vídeo
↓
[TRANSCRIÇÃO]
↓
texto
↓
[EDIÇÃO]
↓
[EXPORTAÇÃO]
↓
download


---

## 📡 Endpoints

### 🔹 Processamento de texto
POST /process

**Request**
'''json'''
{
"text": "Hello world"
}

Response

{
"success": true,
"data": {
"result": "Hello world"
}
}

Exportação TXT (download)
POST /export/txt

Request

{
"text": "Hello world"
}

Response
Arquivo .txt via download:

Content-Type: text/plain
Content-Disposition: attachment
🔹 Métricas
GET /metrics

Response

{
"totalRequests": 10,
"routes": {
"/process": 8
},
"python": {
"calls": 8,
"errors": 1,
"timeouts": 0
}
}

Execução
Requisitos
JDK 21
Gradle
Kotlin 1.9+
yt-dlp (futuro)
FFmpeg (futuro)
Rodar o projeto
./gradlew run

Servidor:

http://localhost:8080
📁 Estrutura de Pastas
com.creatorcontenthub
├── controller
├── application
│     ├── dto
│     ├── usecase
│     └── port
├── domain
└── infrastructure
├── adapter
├── http
└── exception
📊 Observabilidade

O sistema possui:

Logging estruturado
Correlation ID (requestId)
Métricas de latência e volume
Endpoint /metrics
🧠 Diretrizes de Desenvolvimento
Manter arquitetura hexagonal
Evitar overengineering
Não introduzir lógica fora da camada correta
Priorizar simplicidade e clareza
Evolução incremental do pipeline
⚠️ Limitações Atuais
Sem persistência (in-memory)
Sem fila de jobs
Sem ingestão real de mídia (em implementação)
Sem UI
🚀 Próximos Passos
Ingestão de vídeo (YouTube)
Transcrição automatizada
Exportação SRT
Pipeline completo (input → output)
Corte de vídeo (FFmpeg)
📌 Objetivo Final

Transformar o sistema em:

🎯 Um pipeline completo de processamento de mídia, com exportação multi-formato e arquitetura escalável.