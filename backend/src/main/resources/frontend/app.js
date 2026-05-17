
let activeJobId = null;
let jobsRefreshTimeout = null;
let pollingActive = true;
let ingestInterval = null;
let debounceTimer = null;
let lastQuery = "";
let currentMode = "idle"; // "search" | "jobs"

// ========================================
// SSE RUNTIME MANAGER
// ========================================

const runtimeConnections = new Map();

const runtimeReconnectTimers = new Map();

const runtimeListeners = new Map();

const runtimeReconnectStates = new Map();

const runtimeStateStore = new Map();

const MAX_RUNTIME_RECONNECT_ATTEMPTS = 10;

const BASE_RECONNECT_DELAY = 2000;

// ========================================
// SSE CONNECTION MANAGEMENT
// ========================================

function cleanupRuntimeConnection(jobId) {

    const existingConnection = runtimeConnections.get(jobId);

    if (existingConnection) {
        existingConnection.onopen = null;
        existingConnection.onerror = null;
        existingConnection.close();
        runtimeConnections.delete(jobId);
    }

    const reconnectTimer = runtimeReconnectTimers.get(jobId);

    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        runtimeReconnectTimers.delete(jobId);
    }

    runtimeListeners.delete(jobId);
    runtimeReconnectStates.delete(jobId);
}

function clearRuntimeTimeline() {

    const timeline =
        document.getElementById(
            "runtime-timeline"
        );

    if (timeline) {

        timeline.innerHTML = "";

        timeline.scrollTop = 0;
    }
}

function resetRuntimeProgressUI() {

    updateDownloadProgress(0);
}

function appendRuntimeEvent(
    eventName,
    payload
) {

    const timeline =
        document.getElementById(
            "runtime-timeline"
        );

    if (!timeline) {
        return;
    }

    const item =
        document.createElement("div");

    item.style.padding = "8px";
    item.style.borderRadius = "8px";
    item.style.background = "#f5f5f5";
    item.style.fontSize = "12px";
    item.style.borderLeft =
        "4px solid #1976d2";

    const time =
        new Date(
            payload.timestamp || Date.now()
        ).toLocaleTimeString();

    item.innerHTML = `
        <div style="font-weight:bold;">
            ${eventName}
        </div>

        <div style="margin-top:4px;">
            ${payload.message || "-"}
        </div>

        <div style="
            margin-top:4px;
            color:#666;
            font-size:11px;
        ">
            ${time}
        </div>
    `;

    timeline.prepend(item);

    while (timeline.children.length > 40) {

        timeline.removeChild(
            timeline.lastChild
        );
    }
}

function updateDownloadProgress(
    progress
) {

    const progressBar =
        document.getElementById(
            "runtime-progress-bar"
        );

    const progressLabel =
        document.getElementById(
            "runtime-progress-label"
        );

    if (
        !progressBar ||
        !progressLabel
    ) {
        return;
    }

    const normalized =
        Math.max(
            0,
            Math.min(100, progress)
        );

    progressBar.style.width =
        `${normalized}%`;

    progressLabel.innerText =
        `${normalized.toFixed(1)}%`;
}

function restoreRuntimeState(
    runtimeState
) {
    clearRuntimeTimeline();

    if (!runtimeState) {
        return;
    }

    if (
        typeof runtimeState.progress ===
        "number"
    ) {

        updateDownloadProgress(
            runtimeState.progress
        );
    }

    if (runtimeState.stage) {

        const runtimeStage =
            document.getElementById(
                "runtime-stage"
            );

        if (runtimeStage) {

            runtimeStage.innerText =
                runtimeState.stage;
        }
    }

    if (
        Array.isArray(
            runtimeState.timeline
        )
    ) {

        runtimeState.timeline.forEach(
            item => {

                appendRuntimeEvent(
                    item.event,
                    item.payload
                );
            }
        );
    }
}

function connectJobRuntimeStream(jobId, handlers = {}) {

    if (!jobId) {
        return;
    }

    // evita múltiplas conexões do mesmo job
    cleanupRuntimeConnection(jobId);

    let reconnectState =
        runtimeReconnectStates.get(jobId);

    if (!reconnectState) {

        reconnectState = {
            attempts: 0,
            closedManually: false,
            terminallyClosed: false
        };

        runtimeReconnectStates.set(
            jobId,
            reconnectState
        );
    };

    if (!runtimeStateStore.has(jobId)) {

        runtimeStateStore.set(
            jobId,
            {
                timeline: [],
                progress: 0,
                stage: null,
                summary: null
            }
        );
    }

    runtimeListeners.delete(jobId);

    runtimeListeners.set(jobId, handlers);

    function connect() {

        if (reconnectState.closedManually) {
            return;
        }

        const eventSource =
            new EventSource(`/jobs/${jobId}/events`);

        runtimeConnections.set(jobId, eventSource);

        eventSource.onopen = () => {

            reconnectState.attempts = 0;

            console.log(
                "[SSE] connected:",
                jobId
            );
        };

        eventSource.onerror = () => {

            console.warn(
                "[SSE] disconnected:",
                jobId
            );

            eventSource.close();

            runtimeConnections.delete(jobId);

            if (reconnectState.closedManually) {
                return;
            }

            if (reconnectState.terminallyClosed) {

                console.log(
                    "[SSE] reconnect skipped (terminal stream):",
                    jobId
                );

                return;
            }

            reconnectState.attempts++;

            if (
                reconnectState.attempts >
                MAX_RUNTIME_RECONNECT_ATTEMPTS
            ) {

                console.error(
                    "[SSE] reconnect limit reached:",
                    jobId
                );

                return;
            }

            const delay =
                BASE_RECONNECT_DELAY *
                reconnectState.attempts;

            const timer = setTimeout(() => {
                connect();
            }, delay);

            runtimeReconnectTimers.set(
                jobId,
                timer
            );
        };

        [
            "stage_changed",
            "stage_started",
            "download_progress",
            "retry_scheduled",
            "heartbeat_timeout",
            "orphan_recovered",
            "dlq_transition",
            "job_completed",
            "job_failed",
            "stream_completed"
        ].forEach(eventName => {

            eventSource.addEventListener(
                eventName,
                (event) => {

                    console.log(
                        "[SSE RAW EVENT]",
                        {
                            eventName,
                            rawData: event.data
                        }
                    );

                    try {

                        console.log(
                            "[SSE RAW EVENT]",
                            {
                                eventName,
                                rawData: event.data
                            }
                        );

                        // payload inexistente
                        if (
                            typeof event.data !== "string"
                        ) {

                            console.log(
                                "[SSE IGNORE] non-string payload"
                            );

                            return;
                        }

                        const raw =
                            event.data.trim();

                        // payload vazio
                        if (
                            raw.length === 0
                        ) {

                            console.log(
                                "[SSE IGNORE] empty payload"
                            );

                            return;
                        }

                        // stream_completed pode chegar vazio
                        if (
                            eventName === "stream_completed"
                        ) {

                            console.log(
                                "[SSE STREAM COMPLETED]"
                            );

                            reconnectState.terminallyClosed = true;
                            reconnectState.closedManually = true;

                            console.log(
                                "[SSE] terminal stream completed:",
                                jobId
                            );

                            const existingConnection =
                                runtimeConnections.get(jobId);

                            if (existingConnection) {

                                existingConnection.onopen = null;
                                existingConnection.onerror = null;

                                existingConnection.close();
                            }

                            runtimeConnections.delete(jobId);

                            return;
                        }

                        // sanity check
                        if (
                            !raw.startsWith("{")
                        ) {

                            console.warn(
                                "[SSE IGNORE] invalid json payload",
                                raw
                            );

                            return;
                        }

                        const payload =
                            JSON.parse(raw);

                        const runtimeState =
                            runtimeStateStore.get(jobId);

                        if (runtimeState) {

                            runtimeState.timeline.push({
                                event: eventName,
                                payload,
                                timestamp: Date.now()
                            });

                            // limitar memória
                            if (
                                runtimeState.timeline.length > 50
                            ) {
                                runtimeState.timeline.shift();
                            }
                        }

                        if (runtimeState) {

                            if (payload.stage) {
                                runtimeState.stage =
                                    payload.stage;
                            }

                            if (
                                typeof payload.progress ===
                                "number"
                            ) {

                                runtimeState.progress =
                                    payload.progress;
                            }
                        }

                        console.log(
                            "[SSE PARSED]",
                            payload
                        );

                        const listener =
                            runtimeListeners.get(jobId);

                        if (
                            listener &&
                            typeof listener.onEvent === "function"
                        ) {

                            listener.onEvent(
                                eventName,
                                payload
                            );
                        }

                    } catch (err) {

                        console.error(
                            "[SSE] invalid payload:",
                            err,
                            event.data
                        );
                    }
                }
            );
        });
    }

    connect();

    // ========================================
    // FALLBACK STATE SYNC
    // ========================================

    const fallbackSyncInterval = setInterval(
        async () => {

            // stream encerrado
            if (
                reconnectState.closedManually ||
                reconnectState.terminallyClosed
            ) {

                clearInterval(fallbackSyncInterval);
                return;
            }

            try {

                const res =
                    await fetch(`/jobs/${jobId}`);

                if (!res.ok) {
                    return;
                }

                const data = await res.json();

                if (!data?.success || !data?.data) {
                    return;
                }

                const job = data.data;

                if (job.id !== activeJobId) {

                    console.log(
                        "[FALLBACK IGNORE] inactive job",
                        job.id,
                        activeJobId
                    );

                    return;
                }

                // ========================================
                // HYDRATE SUMMARY
                // ========================================

                const summaryEl =
                    document.getElementById(
                        "job-summary"
                    );

                if (
                    summaryEl &&
                    job.summary
                ) {

                    summaryEl.innerText =
                        job.summary;
                }

                // ========================================
                // HYDRATE TRANSCRIPTION STATUS
                // ========================================

                const runtimeStage =
                    document.getElementById(
                        "runtime-stage"
                    );

                if (
                    runtimeStage &&
                    job.status === "PROCESSING"
                ) {

                    if (
                        job.transcription &&
                        !job.summary
                    ) {

                        runtimeStage.innerText =
                            "SUMMARIZING";

                    } else if (
                        !job.transcription
                    ) {

                        runtimeStage.innerText =
                            "TRANSCRIBING";
                    }
                }

                // ========================================
                // TERMINAL JOB
                // ========================================

                if (
                    job.status === "DONE" ||
                    job.status === "FAILED"
                ) {

                    clearInterval(
                        fallbackSyncInterval
                    );
                }

            } catch (err) {

                console.warn(
                    "[FALLBACK SYNC ERROR]",
                    err
                );
            }

        },
        8000
    );

    return {
        close() {

            reconnectState.closedManually = true;

            clearInterval(
                fallbackSyncInterval
            );

            cleanupRuntimeConnection(jobId);
        }
    };
}

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
        clearTimeout(ingestInterval);
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

function resetRuntimeUI() {

    resetRuntimeProgressUI();

    clearRuntimeTimeline();

    const runtimeStage =
        document.getElementById(
            "runtime-stage"
        );

    if (runtimeStage) {

        runtimeStage.innerText =
            "aguardando runtime...";
    }
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

        if (activeJobId) {

            const activeCard =
                document.querySelector(
                    `.select-item[data-id="${activeJobId}"]`
                );

            if (activeCard) {

                activeCard.closest(".result-item")
                    .style.background = "#e3f2fd";
            }
        }
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

    const runtimeState =
        runtimeStateStore.get(job.id);

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
        
        <div class="detail-block">
            <strong>Stage Atual:</strong>

            <div
                id="runtime-stage"
                style="
                    margin-top:8px;
                    padding:10px;
                    border-radius:8px;
                    background:#f5f5f5;
                    font-weight:bold;
                "
            >
                aguardando runtime...
            </div>
        </div>

        <div class="detail-block">

            <strong>Download Progress:</strong>

            <div
                style="
                    width:100%;
                    height:18px;
                    background:#e0e0e0;
                    border-radius:999px;
                    overflow:hidden;
                    margin-top:8px;
                "
            >
                <div
                    id="runtime-progress-bar"
                    style="
                        width:0%;
                        height:100%;
                        background:#1976d2;
                        transition:width 180ms linear;
                    "
                ></div>
            </div>

            <div
                id="runtime-progress-label"
                style="
                    margin-top:6px;
                    font-size:12px;
                    color:#666;
                "
            >
                0%
            </div>
        </div>

        <div class="detail-block">

            <strong>Timeline Runtime:</strong>

            <div
                id="runtime-timeline"
                style="
                    margin-top:10px;
                    display:flex;
                    flex-direction:column;
                    gap:8px;
                    max-height:240px;
                    overflow-y:auto;
                    padding-right:6px;
                "
            ></div>
        </div>
    `;

    // 👇 conteúdo seguro (SEM warning)
    document.getElementById("job-summary").innerText =
        job.summary || "(vazio)";

    resetRuntimeUI();

    if (runtimeState) {

        restoreRuntimeState(runtimeState);
    }

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

        loadJobs();

        activeJobId = jobId;

        localStorage.setItem(
            "activeRuntimeJobId",
            jobId
        );

        statusEl.innerText = `Job criado: ${jobId}`;
        urlInput.value = "";

        // ========================================
        // START REALTIME SSE
        // ========================================

        connectJobRuntimeStream(jobId, {

            onEvent(eventName, payload) {

                if (!payload || payload.jobId !== activeJobId) {

                    console.log(
                        "[SSE IGNORE] inactive job event",
                        payload?.jobId,
                        activeJobId
                    );

                    return;
                }

                console.log(
                    "[RUNTIME EVENT]",
                    eventName,
                    payload
                );

                // garante render realtime do painel ativo
                if (
                    !document.getElementById("runtime-stage")
                ) {

                    renderDetails({
                        id: payload.jobId,
                        status: payload.status,
                        stage: payload.stage,
                        transcription: "",
                        summary: "",
                        createdAt: Date.now()
                    });
                }

                // ========================================
                // DETAILS RUNTIME UI
                // ========================================

                const runtimeStage =
                    document.getElementById("runtime-stage");

                if (runtimeStage) {

                    runtimeStage.innerText =
                        `${payload.stage || "-"}`;
                }

                const progressBar =
                    document.getElementById(
                        "runtime-progress-bar"
                    );

                const progressLabel =
                    document.getElementById(
                        "runtime-progress-label"
                    );

                if (
                    progressBar &&
                    progressLabel &&
                    typeof payload.progress === "number"
                ) {

                    const progress =
                        Math.max(
                            0,
                            Math.min(100, payload.progress)
                        );

                    progressBar.style.width =
                        `${progress}%`;

                    progressLabel.innerText =
                        `${progress.toFixed(1)}%`;
                }

                // ========================================
                // TIMELINE
                // ========================================

                const timeline =
                    document.getElementById(
                        "runtime-timeline"
                    );

                if (timeline) {

                    const item =
                        document.createElement("div");

                    item.style.padding = "8px";
                    item.style.borderRadius = "8px";
                    item.style.background = "#f5f5f5";
                    item.style.fontSize = "12px";
                    item.style.borderLeft =
                        "4px solid #1976d2";

                    const time =
                        new Date(
                            payload.timestamp || Date.now()
                        ).toLocaleTimeString();

                    item.innerHTML = `
                        <div style="font-weight:bold;">
                            ${eventName}
                        </div>

                        <div style="margin-top:4px;">
                            ${payload.message || "-"}
                        </div>

                        <div style="
                            margin-top:4px;
                            color:#666;
                            font-size:11px;
                        ">
                            ${time}
                        </div>
                    `;

                    timeline.prepend(item);

                    // mantém timeline controlada
                    while (timeline.children.length > 40) {
                        timeline.removeChild(
                            timeline.lastChild
                        );
                    }
                }

                // ========================================
                // SSE-FIRST RUNTIME UI
                // ========================================

                // evita conflito com polling legacy
                if (
                    eventName === "job_completed" ||
                    eventName === "job_failed"
                ) {

                    runtimeStateStore.delete(
                        payload.jobId
                    );

                    updateIngestUI({
                        status:
                            eventName === "job_completed"
                                ? "DONE"
                                : "FAILED",

                        summary: "",

                        transcription: ""
                    });
                }
            }
        });

        // SSE-FIRST MODE
        // polling legacy desativado temporariamente
        console.log(
            "[INGEST] SSE realtime mode ativo"
        );

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
    pollingActive = false;
    let attempts = 0;
    const maxAttempts = 120;

    const start = Date.now();
    const maxDuration = 6 * 60 * 1000;

    // limpa polling anterior
    if (ingestInterval) {
        clearTimeout(ingestInterval);
        ingestInterval = null;
    }

    async function executePoll() {

        if (!isRunning) {
            return;
        }

        if (Date.now() - start > maxDuration) {
            statusEl.innerText =
                "Processamento demorando mais que o esperado... (ainda em execução)";
            return;
        }

        attempts++;

        if (attempts > maxAttempts) {
            statusEl.innerText =
                "Processamento demorando mais que o esperado... (ainda em execução)";
            return;
        }

        try {

            const res = await fetch(`/ingest/${jobId}`);
            const data = await res.json();

            if (!res.ok || !data?.data) {
                throw new Error("Erro ao consultar status");
            }

            const job = data.data;

            const elapsedSec =
                Math.floor((Date.now() - start) / 1000);

            updateIngestUI(job, elapsedSec);

            if (
                job.status === "DONE" ||
                job.status === "FAILED"
            ) {
                isRunning = false;
                pollingActive = true;
                return;
            }

            ingestInterval = setTimeout(
                executePoll,
                3000
            );

        } catch (err) {

            console.error("Erro no polling:", err);

            statusEl.innerText =
                "Erro ao acompanhar processamento";

            isRunning = false;
        }
    }

    executePoll();
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

        const timeInfo =
            elapsedSec !== null
                ? ` (${elapsedSec}s)`
                : "";

        const nextText = `${icon} ${stage}${timeInfo}`;

        if (statusEl.innerText !== nextText) {
            statusEl.innerText = nextText;
        }
        return;
    }

    if (job.status === "FAILED") {
        statusEl.innerText = "❌ Falha no processamento";
        return;
    }

    if (job.status === "DONE") {
        statusEl.innerText = "✔ Processamento concluído";

        const runtimePanelActive =
            document.getElementById("runtime-stage");

        if (!runtimePanelActive) {

            renderDetails(job);

        } else {

            // atualiza apenas resumo final
            const summaryEl =
                document.getElementById("job-summary");

            if (summaryEl) {

                summaryEl.innerText =
                    job.summary || "(vazio)";
            }

            const runtimeStage =
                document.getElementById("runtime-stage");

            if (runtimeStage) {

                runtimeStage.innerText = "COMPLETED";
            }
        }

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

    if (!jobsRefreshTimeout) {
        resultsEl.innerHTML = "";
        detailsEl.innerHTML = "";
        statusEl.innerText = "Carregando jobs...";
    }

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
        clearTimeout(ingestInterval);
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

window.addEventListener(
    "load",
    async () => {

        const savedJobId =
            localStorage.getItem(
                "activeRuntimeJobId"
            );

        if (!savedJobId) {
            return;
        }

        try {

            const res =
                await fetch(`/jobs/${savedJobId}`);

            if (!res.ok) {
                return;
            }

            const data = await res.json();

            if (!data?.success || !data?.data) {
                return;
            }

            const job = data.data;

            // job finalizado
            if (
                job.status === "DONE" ||
                job.status === "FAILED"
            ) {

                localStorage.removeItem(
                    "activeRuntimeJobId"
                );

                return;
            }

            activeJobId = savedJobId;

            renderDetails(job);

            if (
                runtimeConnections.has(savedJobId)
            ) {

                console.log(
                    "[RUNTIME RECOVERY] connection already active"
                );

                return;
            }

            connectJobRuntimeStream(
                savedJobId,
                {
                    onEvent(eventName, payload) {

                        if (
                            !payload ||
                            payload.jobId !== activeJobId
                        ) {
                            return;
                        }

                        const runtimeStage =
                            document.getElementById(
                                "runtime-stage"
                            );

                        if (
                            runtimeStage &&
                            payload.stage
                        ) {

                            runtimeStage.innerText =
                                payload.stage;
                        }

                        if (
                            typeof payload.progress ===
                            "number"
                        ) {

                            updateDownloadProgress(
                                payload.progress
                            );
                        }

                        appendRuntimeEvent(
                            eventName,
                            payload
                        );
                    }
                }
            );

            console.log(
                "[RUNTIME RECOVERY] restored:",
                savedJobId
            );

        } catch (err) {

            console.error(
                "[RUNTIME RECOVERY ERROR]",
                err
            );
        }
    }
);