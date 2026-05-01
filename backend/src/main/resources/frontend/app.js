
let activeJobId = null;
let jobsRefreshTimeout = null;
let pollingActive = true;
let ingestInterval = null;


async function search() {
    const statusEl = document.getElementById("status");
    const resultsEl = document.getElementById("results");
    const detailsEl = document.getElementById("details");

    const q = document.getElementById("searchInput").value;

    resultsEl.innerHTML = "";
    detailsEl.innerHTML = "";
    statusEl.innerText = "Buscando...";

    try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);

        const res = await fetch(`/jobs/search?q=${encodeURIComponent(q)}`, {
            signal: controller.signal
        });

        clearTimeout(timeout);

        if (!res.ok) throw new Error("Erro na requisição");

        const data = await res.json();

        if (!data?.data?.items || data.data.items.length === 0) {
            statusEl.innerText = "Nenhum resultado encontrado";
            return;
        }

        statusEl.innerText = "";
        renderResults(data.data.items);

    } catch (err) {
        console.error("Erro na busca:", err);
        statusEl.innerText = "Erro ao buscar dados";
    }
}

function getStatusLabel(status) {
    if (!status) return "—";

    const normalized = status.toString().trim().toUpperCase();

    switch (normalized) {
        case "DONE":
            return "✔ Concluído";
        case "PROCESSING":
            return "⏳ Processando";
        case "FAILED":
            return "❌ Falhou";
        default:
            return normalized;
    }
}

function renderResults(items) {
    const resultsEl = document.getElementById("results");
    resultsEl.innerHTML = "";

    items.forEach(item => {
        const div = document.createElement("div");
        div.className = "result-item";

        if (item.jobId === activeJobId) {
            div.style.background = "#e3f2fd";
        }

        div.onclick = () => {
            activeJobId = item.jobId;
            loadDetails(item.jobId);

            // 🔥 re-render correto (sem perder referência)
            document.querySelectorAll(".result-item").forEach(el => {
                el.style.background = "";
            });
            div.style.background = "#e3f2fd";
        };

        div.innerHTML = `
        <div class="result-snippet">
            ${item.snippet || "(sem snippet)"}
        </div>
        <div class="result-meta">
            <span>${getStatusLabel(item.status)}</span>
            <span>${new Date(item.createdAt).toLocaleString()}</span>
        </div>
        `;

        resultsEl.appendChild(div);
    });
}

async function loadDetails(jobId) {
    const detailsEl = document.getElementById("details");
    const statusEl = document.getElementById("status");

    detailsEl.innerHTML = "Carregando detalhes...";
    statusEl.innerText = "";

    try {
        const res = await fetch(`/jobs/${jobId}`);
        const data = await res.json();

        if (!res.ok || !data.success) {
            throw new Error("Erro ao carregar detalhes");
        }

        renderDetails(data.data);

    } catch (err) {
        console.error("Erro ao carregar detalhes:", err);
        detailsEl.innerHTML = "Erro ao carregar detalhes";
    }
}

function renderDetails(job) {
    const detailsEl = document.getElementById("details");

    detailsEl.innerHTML = `
    <h2>Detalhes do Job</h2>

    <div class="detail-block">
      <strong>Status:</strong> ${getStatusLabel(job.status)}
    </div>

    <div class="detail-block">
      <strong>Criado em:</strong> ${new Date(job.createdAt).toLocaleString()}
    </div>

    <div class="detail-block">
      <strong>Finalizado em:</strong> ${
        job.finishedAt ? new Date(job.finishedAt).toLocaleString() : "—"
    }
    </div>

    <div class="detail-block">
      <strong>Transcription:</strong>
      <pre>${job.transcription || "(vazio)"}</pre>
    </div>

    <div class="detail-block">
      <strong>Summary:</strong>
      <pre>${job.summary || "(vazio)"}</pre>
    </div>
    `;
}

async function ingest() {
    const urlInput = document.getElementById("urlInput");
    const statusEl = document.getElementById("ingestStatus");
    const button = document.getElementById("ingestBtn");

    const url = urlInput.value.trim();

    if (!url) {
        statusEl.innerText = "Informe uma URL válida";
        return;
    }

    button.disabled = true;
    urlInput.disabled = true;
    statusEl.innerText = "Enviando para processamento...";

    try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 8000);

        const res = await fetch("/ingest/youtube", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ url }),
            signal: controller.signal
        });

        clearTimeout(timeout);

        if (!res.ok) throw new Error("Erro ao iniciar ingestão");

        const data = await res.json();

        if (!data?.data?.jobId) {
            throw new Error("Resposta inválida do servidor");
        }

        const jobId = data.data.jobId;

        activeJobId = jobId;

        statusEl.innerText = `Job criado: ${jobId}`;
        urlInput.value = "";

        pollStatus(jobId);

    } catch (err) {
        console.error("Erro no ingest:", err);

        if (err.name === "AbortError") {
            statusEl.innerText = "Timeout ao iniciar ingestão";
        } else {
            statusEl.innerText = "Erro ao processar URL";
        }
    } finally {
        button.disabled = false;
        urlInput.disabled = false;
    }
}

function pollStatus(jobId) {
    const statusEl = document.getElementById("ingestStatus");

    let isRunning = true;
    let attempts = 0;
    const maxAttempts = 120;

    const start = Date.now();
    const maxDuration = 6 * 60 * 1000; // 6 minutos

    // limpa qualquer intervalo anterior
    if (ingestInterval) {
        clearInterval(ingestInterval);
    }

    ingestInterval = setInterval(async () => {
        if (!isRunning) return;

        if (Date.now() - start > maxDuration) {
            clearInterval(ingestInterval);
            statusEl.innerText = "Processamento demorando mais que o esperado... (ainda em execução)";
            return;
        }

        attempts++;

        if (attempts > maxAttempts) {
            clearInterval(ingestInterval);
            statusEl.innerText = "Processamento demorando mais que o esperado... (ainda em execução)";
            return;
        }

        try {
            const res = await fetch(`/ingest/${jobId}`);
            const data = await res.json();

            if (!res.ok || !data?.data) {
                throw new Error("Erro ao consultar status");
            }

            const job = data.data;
            const elapsedSec = Math.floor((Date.now() - start) / 1000);

            updateIngestUI(job, elapsedSec);

            if (job.status === "DONE" || job.status === "FAILED") {
                isRunning = false;
                clearInterval(ingestInterval);
            }

        } catch (err) {
            console.error("Erro no polling:", err);
            statusEl.innerText = "Erro ao acompanhar processamento";
            isRunning = false;
            clearInterval(ingestInterval);
        }
    }, 3000);
}

function updateIngestUI(job, elapsedSec = null) {
    const statusEl = document.getElementById("ingestStatus");
    const detailsEl = document.getElementById("details");
    const searchInput = document.getElementById("searchInput");

    if (job.status === "PROCESSING") {
        let stage = "Inicializando...";
        let icon = "⏳";

        if (job.transcription && !job.summary) {
            stage = "Gerando resumo...";
            icon = "🧠";
        } else if (!job.transcription) {
            stage = "Transcrevendo áudio...";
            icon = "🎧";
        }

        const timeInfo = elapsedSec !== null ? ` (${elapsedSec}s)` : "";

        statusEl.innerText = `${icon} ${stage}${timeInfo}`;
        return;
    }

    if (job.status === "FAILED") {
        statusEl.innerText = "❌ Falha no processamento";
        return;
    }

    if (job.status === "DONE") {
        statusEl.innerText = "✔ Processamento concluído";

        renderDetails(job);
        loadJobs();

        let query = "";

        if (job.summary) {
            query = job.summary.substring(0, 80);
            searchInput.value = query;
        }

        // evitar duplicação de botão
        if (!document.getElementById("searchGeneratedBtn")) {
            const button = document.createElement("button");
            button.id = "searchGeneratedBtn";
            button.innerText = "Buscar conteúdo gerado";

            button.onclick = () => {
                searchInput.value = query;
                search();
            };

            detailsEl.appendChild(button);
        }
    }
}

async function loadJobs() {
    const statusEl = document.getElementById("status");
    const resultsEl = document.getElementById("results");
    const detailsEl = document.getElementById("details");

    resultsEl.innerHTML = "";
    detailsEl.innerHTML = "";
    statusEl.innerText = "Carregando jobs...";

    try {
        // leitura segura dos filtros
        const statusElFilter = document.getElementById("statusFilter");
        const sortEl = document.getElementById("sortOrder");

        const status = statusElFilter ? statusElFilter.value : "";
        const sort = sortEl ? sortEl.value : "";

        // montagem da URL
        let url = `/jobs?limit=20`;

        if (status) {
            url += `&status=${status}`;
        }

        url += `&sort=createdAt`;

        if (sort) {
            url += `&order=${sort}`;
        }

        const res = await fetch(url);

        if (!res.ok) {
            throw new Error("Erro ao carregar jobs");
        }

        const data = await res.json();

        if (!data?.data?.items || data.data.items.length === 0) {
            statusEl.innerText = "Nenhum job encontrado";
            return;
        }

        statusEl.innerText = "";
        renderResults(data.data.items);

        // 🔁 auto refresh inteligente (controlado)
        const hasProcessing = data.data.items.some(i => i.status === "PROCESSING");

        if (hasProcessing && pollingActive) {
            if (jobsRefreshTimeout) {
                clearTimeout(jobsRefreshTimeout);
            }

            jobsRefreshTimeout = setTimeout(() => loadJobs(), 4000);
        }

    } catch (err) {
        console.error("Erro ao carregar jobs:", err);
        statusEl.innerText = "Erro ao carregar jobs";
    }
}


async function stopPolling() {
    if (activeJobId) {
        try {
            await fetch(`/jobs/${activeJobId}/cancel`, {
                method: "POST"
            });
        } catch (e) {
            console.error("Erro ao cancelar job:", e);
        }
    }

    pollingActive = false;

    if (jobsRefreshTimeout) {
        clearTimeout(jobsRefreshTimeout);
    }

    if (ingestInterval) {
        clearInterval(ingestInterval);
    }

    document.getElementById("status").innerText = "Atualização pausada";
    document.getElementById("ingestStatus").innerText = "Processamento pausado";
}