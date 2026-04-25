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

## ADR-002: Gestão de Portas e Conflitos de Binding

**Data:** 24/04/2026
**Status:** Decidido

### Contexto
Durante o bootstrap do ambiente híbrido (Kotlin local + Docker), ocorreu um erro `java.net.BindException: Address already in use`. O Backend (8081) e o Adminer (8081) entraram em conflito, impedindo a inicialização do servidor Ktor.

### Decisão
Estabelecer um mapa de portas estático e exclusivo para cada serviço, separando portas de desenvolvimento (backend) de portas de ferramentas (infra).

### Consequência
- O Backend passa a ocupar a porta **8080** (padrão Ktor/Docker).
- O Adminer é movido para a porta **8085**, liberando a faixa 8081-8084 para futuros serviços.
- A configuração do Ktor deve ser delegada ao `application.conf` via `EngineMain`.

## ADR-003 — Profiles de Execução (dev / docker / prod)

**Data**: 24/04/2026
**Status**: Decidido

### Contexto

Após a consolidação da infraestrutura (PostgreSQL, HikariCP, Docker, health checks e governança de portas — ADR-002), surgiu a necessidade de padronizar a execução do sistema em diferentes ambientes:

Desenvolvimento local (IntelliJ)
Execução via Docker
Produção (futuro)

Problemas identificados:

Configurações divergentes entre ambientes
Uso inconsistente de variáveis de ambiente
Conflitos de host (ex: localhost vs postgres-db)
Necessidade de alterar configuração manualmente para rodar

### Problema

Sem uma estratégia clara de profiles, o sistema apresenta:

comportamento inconsistente entre ambientes
risco de erro humano na configuração
dificuldade de manutenção e evolução
✅ Decisão

Adotar profiles explícitos de execução, controlados por variável de ambiente:

APP_ENV=dev | docker | prod

Cada profile define apenas diferenças de configuração, mantendo uma base comum.

### Estrutura de Configuração

Arquivos utilizados:

application.conf          → base comum
application-dev.conf      → ambiente local
application-docker.conf   → ambiente Docker
application-prod.conf     → produção
📦 application.conf (BASE)
ktor {
deployment {
port = ${?PORT}
port = 8080
}

application {
modules = [ com.creatorcontenthub.ApplicationKt.module ]
}
}

app {
env = ${?APP_ENV}
env = "dev"
}
💻 application-dev.conf
include "application.conf"

database {
url = "jdbc:postgresql://localhost:5432/postgres"
user = "user"
password = "password"
}

ktor.deployment.port = 8081
🐳 application-docker.conf
include "application.conf"

database {
url = "jdbc:postgresql://postgres-db:5432/postgres"
user = "user"
password = "password"
}

ktor.deployment.port = 8080
🚀 application-prod.conf
include "application.conf"

database {
url = ${?DB_URL}
user = ${?DB_USER}
password = ${?DB_PASSWORD}
}

ktor.deployment.port = ${?PORT}
🔄 Seleção do Profile

Definida via variável de ambiente:

Local (IntelliJ)
APP_ENV=dev
Docker
environment:
APP_ENV: docker
Produção
APP_ENV=prod
📏 Regras
application.conf é a base única
Profiles apenas sobrescrevem configurações (override)
Não utilizar lógica condicional de ambiente no código
Variáveis sensíveis devem ser fornecidas via ENV (produção)
Não acessar System.getenv diretamente para configuração (usar Ktor config)

### Anti-patterns proibidos
Hardcode de URL de banco no código
if (env == "docker") em UseCases ou domínio
múltiplas fontes de verdade para configuração
duplicação de config entre arquivos

### Validação
DEV
APP_ENV=dev
→ conecta em localhost
→ porta 8081
DOCKER
APP_ENV=docker
→ conecta em postgres-db
→ porta 8080
PROD
APP_ENV=prod
→ usa variáveis de ambiente
→ sem fallback silencioso

### Consequências
✔ Positivas
Configuração previsível
Separação clara de ambientes
Execução consistente (local vs Docker)
Base pronta para CI/CD
Redução de erros operacionais
⚠️ Negativas
Mais arquivos de configuração
Necessidade de disciplina na manutenção

### Conclusão

A introdução de profiles resolve inconsistências entre ambientes e estabelece uma base sólida para evolução do sistema, garantindo:

execução previsível, configurável e pronta para produção