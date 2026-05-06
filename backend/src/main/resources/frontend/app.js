
let activeJobId = null;
let jobsRefreshTimeout = null;
let pollingActive = true;
let ingestInterval = null;
let debounceTimer = null;
let lastQuery = "";
let currentMode = "idle"; // "search" | "jobs"

function onSearchInput(value) {
    clearTimeout(debounceTimer);

    debounceTimer = setTimeout(() => {
        // evita chamada desnecessária
        if (!value || value.trim().length === 0) {
            return;
        }

        search();
    }, 350);
}

async function search() {
    const statusEl = document.getElementById("status");
    const resultsEl = document.getElementById("results");
    const detailsEl = document.getElementById("details");

    const q = document.getElementById("searchInput").value;
    const inputEl = document.getElementById("searchInput");
    const searchBtn = document.querySelector(".btn-secondary");

    currentMode = "search";

    // parar polling SEMPRE
    if (jobsRefreshTimeout) {
        clearTimeout(jobsRefreshTimeout);
    }
    pollingActive = false;

    if (!q || q.trim().length === 0) {
        return;
    }

    lastQuery = q;

    if (searchBtn) searchBtn.disabled = true;

    statusEl.innerText = "Buscando...";
    resultsEl.innerHTML = "";

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
            statusEl.innerText = "";

            resultsEl.innerHTML = `
                <div class="empty-state">
                    <strong>Nenhum resultado encontrado</strong><br/>
                    <small>Tente termos diferentes</small>
                   </div>
            `;
            return;
        }

        statusEl.innerText = "";
        renderResults(data.data.items);

    } catch (err) {
        console.error("Erro na busca:", err);
        statusEl.innerText = "Erro ao buscar. Tente novamente.";
    }finally {
        if (searchBtn) searchBtn.disabled = false;
    }
}

function clearSearch() {
    const inputEl = document.getElementById("searchInput");
    const resultsEl = document.getElementById("results");
    const detailsEl = document.getElementById("details");
    const statusEl = document.getElementById("status");

    inputEl.value = "";
    resultsEl.innerHTML = "";
    detailsEl.innerHTML = "";
    statusEl.innerText = "";

    activeJobId = null;

    lastQuery = "";
}

function resetUI() {

    // ===== PARAR PROCESSOS =====
    if (jobsRefreshTimeout) {
        clearTimeout(jobsRefreshTimeout);
        jobsRefreshTimeout = null;
    }

    if (ingestInterval) {
        clearInterval(ingestInterval);
        ingestInterval = null;
    }

    if (debounceTimer) {
        clearTimeout(debounceTimer);
        debounceTimer = null;
    }

    pollingActive = false;

    // ===== RESET ESTADO GLOBAL =====
    activeJobId = null;
    lastQuery = "";
    currentMode = "idle";

    // ===== RESET INPUTS =====
    const searchInput = document.getElementById("searchInput");
    const urlInput = document.getElementById("urlInput");

    if (searchInput) searchInput.value = "";
    if (urlInput) urlInput.value = "";

    // ===== RESET SELECTS =====
    const statusFilter = document.getElementById("statusFilter");
    const sortOrder = document.getElementById("sortOrder");

    if (statusFilter) statusFilter.value = "";
    if (sortOrder) sortOrder.value = "desc";

    // ===== RESET CHECKBOXES =====
    const selectAll = document.getElementById("selectAll");
    if (selectAll) selectAll.checked = false;

    document.querySelectorAll(".select-item").forEach(cb => {
        cb.checked = false;
    });

    updateSelectedCount();

    // ===== RESET UI =====
    const resultsEl = document.getElementById("results");
    const detailsEl = document.getElementById("details");
    const statusEl = document.getElementById("status");
    const ingestStatusEl = document.getElementById("ingestStatus");

    if (resultsEl) resultsEl.innerHTML = "";
    if (detailsEl) detailsEl.innerHTML = "";
    if (statusEl) statusEl.innerText = "";
    if (ingestStatusEl) ingestStatusEl.innerText = "";

}

function clearUrlInput() {
    const urlInput = document.getElementById("urlInput");
    const ingestStatus = document.getElementById("ingestStatus");

    if (urlInput) {
        urlInput.value = "";
    }

    // opcional: limpar mensagem de ingest (sem afetar estado global)
    if (ingestStatus) {
        ingestStatus.innerText = "";
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

    // reset do select-all e contador
    const selectAll = document.getElementById("selectAll");
    if (selectAll) selectAll.checked = false;
    updateSelectedCount();

    items.forEach(item => {
        const div = document.createElement("div");
        div.className = "result-item";

        if (item.jobId === activeJobId) {
            div.style.background = "#e3f2fd";
        }

        div.onclick = () => {
            activeJobId = item.jobId;

            const detailsEl = document.getElementById("details");

            document.querySelectorAll(".result-item").forEach(el => {
                el.style.background = "";
            });

            div.style.background = "#e3f2fd";
            detailsEl.innerHTML = "Carregando detalhes...";

            div.style.pointerEvents = "none";

            loadDetails(item.jobId)
                .finally(() => {
                    div.style.pointerEvents = "auto";
                });
        };

        div.ondblclick = () => {
            window.open(`/job.html?jobId=${item.jobId}`, "_blank");
        };

        div.innerHTML = `
            <div style="display:flex; gap:10px; align-items:flex-start;">

                <!-- THUMBNAIL -->
                <div style="flex-shrink:0;">
                    <img 
                        src="${item.thumbnailUrl || ''}" 
                        alt="thumb"
                        style="width:120px; height:90px; object-fit:cover; border-radius:6px; background:#eee;"
                        onerror="this.style.display='none'"
                    />
                </div>

                <!-- CONTEÚDO -->
                <div style="flex:1;">

                    <div class="result-header">
                        <input type="checkbox"
                                class="select-item"
                                data-id="${item.jobId}" />
                    </div>

                    <div class="result-title">
                        ${item.title ? item.title : "(sem título)"}
                    </div>

                    <div class="result-snippet">
                        ${item.snippet ? item.snippet : "(sem snippet)"}
                    </div>

                    <div class="result-meta">
                        <span class="meta-status">${getStatusLabel(item.status)}</span>
                        <span class="meta-date">${new Date(item.createdAt).toLocaleString()}</span>
                        ${item.finalScore ? `<span class="meta-score">Score: ${item.finalScore.toFixed(2)}</span>` : ""}
                    </div>

                </div>
            </div>
        `;

        const checkbox = div.querySelector(".select-item");

        checkbox.addEventListener("click", (event) => {
            event.stopPropagation();
            updateSelectedCount();
            syncSelectAll();
        });


        resultsEl.appendChild(div);
    });
}

function getSelectedJobIds() {
    return Array.from(document.querySelectorAll(".select-item:checked"))
        .map(el => el.dataset.id);
}

async function deleteSelected() {
    const ids = getSelectedJobIds();

    if (ids.length === 0) {
        alert("Selecione ao menos um item");
        return;
    }

    if (!confirm("Tem certeza que deseja deletar os itens selecionados?")) return;

    await fetch("/jobs/delete", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({ jobIds: ids })
    });

    loadJobs();
}

async function deleteAll() {
    if (!confirm("Tem certeza que deseja deletar TODOS os jobs?")) return;

    await fetch("/jobs/delete", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({ jobIds: [] }) // backend pode interpretar como ALL futuramente
    });

    loadJobs();
}

async function loadDetails(jobId) {
    const detailsEl = document.getElementById("details");

    detailsEl.innerHTML = "Carregando detalhes...";

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

    const createdAt = new Date(job.createdAt).toLocaleString();

    let processingTime = "-";
    if (job.startedAt && job.finishedAt) {
        const duration = (job.finishedAt - job.startedAt) / 1000 / 60;
        processingTime = duration.toFixed(2) + " min";
    }

    detailsEl.innerHTML = `
        <div class="detail-block">
            <strong>Status:</strong> ${job.status}
        </div>

        <div class="detail-block">
            <strong>Criado em:</strong> ${createdAt}
        </div>

        <div class="detail-block">
            <strong>Tempo de processamento:</strong> ${processingTime}
        </div>

        <div class="detail-block">
            <strong>Título:</strong> ${job.title || "(sem título)"}
        </div>

        <div class="detail-block">
            <strong>Resumo:</strong>
            <pre id="job-summary"></pre>
        </div>
    `;

    // 👇 conteúdo seguro (SEM warning)
    document.getElementById("job-summary").innerText =
        job.summary || "(vazio)";
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

    currentMode = "jobs";
    pollingActive = true;

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

        if (hasProcessing && pollingActive && currentMode === "jobs") {
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

function toggleSelectAll(master) {
    const checkboxes = document.querySelectorAll(".select-item");

    checkboxes.forEach(cb => {
        cb.checked = master.checked;
    });

    updateSelectedCount();
}

function updateSelectedCount() {
    const count = document.querySelectorAll(".select-item:checked").length;
    const el = document.getElementById("selectedCount");

    if (el) {
        el.innerText = `${count} selecionado(s)`;
    }
}

function syncSelectAll() {
    const all = document.querySelectorAll(".select-item");
    const checked = document.querySelectorAll(".select-item:checked");

    const selectAll = document.getElementById("selectAll");

    if (!selectAll) return;

    selectAll.checked = all.length > 0 && all.length === checked.length;
}