const runtimeStateApi =
    window.RuntimeState;

const jobsApi =
    window.JobsApi;

const runtimeRenderer =
    window.RuntimeRenderer;

const runtimeHydration =
    window.RuntimeHydration;

const runtimeLifecycle =
    window.RuntimeLifecycle;

const runtimeOwnership =
    window.RuntimeOwnership;

const runtimeCoordination =
    window.RuntimeCoordination;

const runtimeSessionManager =
    window.RuntimeSessionManager;

function validateSubsystemBootstrap() {

    const requiredSubsystems = {

        RuntimeState:
        runtimeStateApi,

        RuntimeRenderer:
        runtimeRenderer,

        RuntimeLifecycle:
        runtimeLifecycle,

        RuntimeOwnership:
        runtimeOwnership,

        RuntimeCoordination:
        runtimeCoordination,

        RuntimeSessionManager:
        runtimeSessionManager,

        RuntimeHydration:
        runtimeHydration,

        JobsApi:
        jobsApi
    };

    const missingSubsystems =
        Object.entries(
            requiredSubsystems
        ).filter(
            ([, value]) => !value
        );

    if (
        missingSubsystems.length > 0
    ) {

        console.error(
            "[BOOTSTRAP VALIDATION FAILED]",
            {
                missingSubsystems:
                    missingSubsystems.map(
                        ([name]) => name
                    )
            }
        );

        throw new Error(
            "Frontend subsystem bootstrap failed"
        );
    }

    console.log(
        "[BOOTSTRAP VALIDATION SUCCESS]"
    );
}

let activeJobId = null;
let activeDetailsJobId = null;
let activeRuntimeStream = null;
let jobsRefreshTimeout = null;
let pollingActive = true;
let ingestInterval = null;
let debounceTimer = null;
let lastQuery = "";
let currentMode = "idle"; // "search" | "jobs"

// ========================================
// SSE RUNTIME MANAGER
// ========================================

const MAX_RUNTIME_RECONNECT_ATTEMPTS = 10;

const BASE_RECONNECT_DELAY = 2000;

function ensureRuntimeConnection(jobId) {

    const session =
        getRuntimeSession(jobId);

    if (
        session?.eventSource &&
        session.eventSource.readyState !== EventSource.CLOSED
    ) {

        console.log(
            "[RUNTIME CONNECTION] reuse",
            { jobId }
        );

        return session.eventSource;
    }

    return null;
}

function getRuntimeSession(jobId) {
    return runtimeSessions.get(jobId);
}

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

function switchActiveRuntimeStream(jobId, handlers = {}) {

    const existingStream =
        runtimeStreamsByJob.get(jobId);

    console.log(
        "[RUNTIME STREAM CONNECT REQUEST]",
        {
            jobId,

            activeJobId,

            hasExistingStream:
                runtimeStreamsByJob.has(jobId),

            hasRuntimeConnection:
                runtimeConnections.has(jobId),

            hasSession:
                runtimeSessions.has(jobId),

            stack:
            new Error().stack
        }
    );

    if (existingStream) {

        console.log(
            "[SSE] existing stream reused",
            jobId
        );

        activeRuntimeStream =
            existingStream;

        activeJobId = jobId;

        orchestrateRuntimePanelRerender(jobId);

        return;
    }

    // encerra stream global anterior
    if (activeRuntimeStream) {

        console.log(
            "[RUNTIME STREAM] closing previous active stream"
        );

        if (activeJobId) {

            runtimeStreamsByJob.delete(
                activeJobId
            );
        }

        activeRuntimeStream.close();

        activeRuntimeStream = null;
    }

    // cria nova stream exclusiva
    activeRuntimeStream =
        connectJobRuntimeStream(
            jobId,
            handlers
        );

    runtimeStreamsByJob.set(
        jobId,
        activeRuntimeStream
    );

    return activeRuntimeStream;
}

function clearLegacyRuntimeTimelineVisuals() {

    const timeline =
        document.getElementById(
            "runtime-timeline"
        );

    if (timeline) {

        timeline.innerHTML = "";

        timeline.scrollTop = 0;
    }
}

function resetLegacyRuntimeProgressVisuals() {

    runtimeRenderer.updateDownloadProgress(0);
}

// ========================================
// STATE-DRIVEN RENDERERS
// ========================================

function orchestrateRuntimePanelRerender(jobId) {

    if (!jobId) {
        return;
    }

    // ownership hardening
    if (jobId !== activeJobId) {

        console.log(
            "[RUNTIME UI] rerender skipped inactive job",
            {
                jobId,
                activeJobId
            }
        );

        return;
    }

    const runtimeState =
        runtimeStateStore.get(jobId);

    if (!runtimeState) {

        console.log(
            "[RUNTIME UI] missing runtime state",
            {
                jobId
            }
        );

        return;
    }

    if (isTerminalRuntime(runtimeState)) {

        runtimeRenderer.renderTerminalRuntime?.(runtimeState);

        return;
    }

    runtimeRenderer.rerenderRuntimeVisuals(
        runtimeState
    );

    console.log(
        "[RUNTIME UI] targeted rerender",
        {
            jobId,
            timeline:
                runtimeState.timeline?.length || 0,
            progress:
            runtimeState.progress,
            stage:
            runtimeState.stage
        }
    );
}

function restoreRuntimeVisualState(runtimeState) {

    if (!runtimeState) {
        return;
    }

    console.log(
        "[RUNTIME HYDRATION RESTORE]",
        {
            jobId: runtimeState.jobId,
            stage: runtimeState.stage,
            progress: runtimeState.progress,
            timeline:
                runtimeState.timeline?.length || 0,
            terminal:
            runtimeState.isTerminal
        }
    );

    orchestrateRuntimePanelRerender(
        runtimeState.jobId
    );
}

function connectJobRuntimeStream(jobId, handlers = {}) {

    console.log(
        "[SSE CONNECT ATTEMPT]",
        {
            jobId,
            existingConnection:
                runtimeConnections.has(jobId),
            activeJobId,
            existingStream:
                runtimeStreamsByJob.has(jobId)
        }
    );

    if (!jobId) {
        return;
    }

    const session =
        window.RuntimeSessionManager.createRuntimeSession(jobId);

    const reconnectState =
        session.reconnectState;

    if (!runtimeStateStore.has(jobId)) {
        runtimeStateStore.set(
            jobId,
            createCanonicalRuntimeState(jobId)
        );
        console.log(
            "[RUNTIME] canonical bootstrap initialized",
            { jobId }
        );
    }

    runtimeListeners.delete(jobId);

    runtimeListeners.set(jobId, handlers);

    function connect() {

        const connectionGeneration =
            session.generation;

        session.activeConnectionGeneration =
            connectionGeneration;

        console.log(
            "[RUNTIME OWNERSHIP SNAPSHOT]",
            {
                jobId,

                activeJobId,

                hasRuntimeConnection:
                    runtimeConnections.has(jobId),

                hasRuntimeStream:
                    runtimeStreamsByJob.has(jobId),

                hasSession:
                    runtimeSessions.has(jobId),

                sessionGeneration:
                session?.generation,

                hasEventSource:
                    !!session?.eventSource,

                eventSourceReadyState:
                session?.eventSource?.readyState,

                reconnectAttempts:
                session?.reconnectState?.attempts,

                terminallyClosed:
                session?.reconnectState?.terminallyClosed
            }
        );

        if (
            session.generation !==
            connectionGeneration
        ) {

            console.log(
                "[SSE CONNECT ABORTED] stale generation",
                {
                    jobId,
                    expectedGeneration:
                    connectionGeneration,
                    currentGeneration:
                    session.generation,
                    ownerId:
                    session.ownerId
                }
            );

            return;
        }

        if (reconnectState.closedManually) {
            return;
        }

        if (
            runtimeConnectionLocks.get(jobId)
        ) {

            console.log(
                "[SSE CONNECT BLOCKED] connection lock active",
                jobId
            );

            return;
        }

        const reusableConnection =
            ensureRuntimeConnection(jobId);

        if (reusableConnection) {

            console.log(
                "[RUNTIME CONNECTION] transport reused",
                { jobId }
            );

            session.handlers =
                handlers;

            runtimeListeners.set(
                jobId,
                handlers
            );

            runtimeConnections.set(
                jobId,
                reusableConnection
            );

            return;
        }

        if (
            runtimeConnections.has(jobId)
        ) {

            console.log(
                "[SSE CONNECT BLOCKED] already connected",
                jobId
            );

            return;
        }

        runtimeConnectionLocks.set(
            jobId,
            true
        );

        const eventSource =
            new EventSource(`/jobs/${jobId}/events`);

        session.eventSource =
            eventSource;

        session.handlers =
            handlers;

        runtimeConnections.set(jobId, eventSource);

        eventSource.onopen = () => {

            reconnectState.attempts = 0;

            console.log(
                "[SSE] connected:",
                jobId
            );

            runtimeConnectionLocks.delete(
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

            if (session) {

                session.eventSource = null;
            }

            if (
                shouldSkipReconnect(
                    reconnectState
                )
            ) {

                if (
                    reconnectState.terminallyClosed
                ) {

                    console.log(
                        "[SSE] reconnect skipped (terminal stream):",
                        jobId
                    );
                }

                return;
            }

            orchestrateReconnect({
                reconnectState,
                maxReconnectAttempts:
                MAX_RUNTIME_RECONNECT_ATTEMPTS,
                baseReconnectDelay:
                BASE_RECONNECT_DELAY,
                session,
                jobId,
                runtimeReconnectTimers,
                shouldAbortReconnect,
                calculateReconnectDelay,
                clearReconnectTimer,
                scheduleReconnectTimer,
                registerReconnectTimer,
                executeReconnect,
                connect,
                shouldSkipReconnect
            });
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
                                "[SSE STREAM COMPLETED]",
                                jobId
                            );

                            window.RuntimeSessionManager.cleanupRuntimeSession(jobId)

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

                        // TERMINAL MUTATION BLOCK
                        if (
                            runtimeState &&
                            runtimeState.isTerminal
                        ){

                            console.log(
                                "[TERMINAL MUTATION BLOCKED]",
                                {
                                    jobId,
                                    eventName
                                }
                            );

                            return;
                        }

                        const updatedRuntimeState =
                            runtimeStateApi.applyRuntimeMutation(
                                jobId,
                                eventName,
                                payload,
                                "sse_primary"
                            );

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

                const data =
                    await fetchJobDetails(jobId);

                if (!data?.data) {
                    return;
                }

                const job = data.data;

                if (
                    !isActiveRuntimeJob(
                        job.id,
                        activeJobId
                    )
                ) {

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

                if (
                    !isActiveDetailsJob(
                        job.id,
                        activeDetailsJobId
                    )
                ) {

                    console.log(
                        "[FALLBACK IGNORE] details ownership mismatch",
                        job.id,
                        activeDetailsJobId
                    );

                    return;
                }

                runtimeRenderer.hydrateJobSummary(job);

                // ========================================
                // HYDRATE TRANSCRIPTION STATUS
                // ========================================

                const runtimeState =
                    runtimeStateStore.get(job.id);

                if (
                    runtimeState &&
                    job.status === "PROCESSING"
                ) {

                    // TERMINAL IMMUTABILITY
                    if (runtimeState.isTerminal) {

                        console.log(
                            "[FALLBACK TERMINAL IGNORE]",
                            job.id
                        );

                        return;
                    }

                    runtimeHydration.hydrateProcessingStageState(
                        runtimeState,
                        job
                    );

                    console.log(
                        "[FALLBACK STORE UPDATE APPLIED]",
                        {
                            jobId: job.id,
                            stage: runtimeState.stage
                        }
                    );

                    orchestrateRuntimePanelRerender(job.id);
                }

                // ========================================
                // TERMINAL JOB
                // ========================================

                if (
                    runtimeLifecycle.shouldCleanupTerminalJob(job)
                ) {

                    window.RuntimeSessionManager.cleanupRuntimeSession(jobId)
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

    runtimeFallbackIntervals.set(
        jobId,
        fallbackSyncInterval
    );

    return {
        close() {

            reconnectState.closedManually = true;

            clearInterval(
                fallbackSyncInterval
            );

            window.RuntimeSessionManager.cleanupRuntimeSession(jobId)
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

        const data = await searchJobs(q);

        clearTimeout(timeout);

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

function resetLegacyRuntimeVisualState() {

    resetLegacyRuntimeProgressVisuals();
    clearLegacyRuntimeTimelineVisuals();

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

function getRuntimeStageLabel(
    stage,
    status = null
) {

    if (!stage && status === "DONE") {
        return "✔ Processamento concluído";
    }

    if (!stage && status === "FAILED") {
        return "❌ Falha no processamento";
    }

    if (!stage && status === "CANCELED") {
        return "⛔ Processamento cancelado";
    }

    switch (
        String(stage || "")
            .trim()
            .toUpperCase()
        ) {

        case "CREATED":
            return "⏳ Inicializando processamento";

        case "DOWNLOADED":
            return "⬇ Download concluído";

        case "TRANSCRIBING":
            return "🎧 Transcrevendo áudio";

        case "TRANSCRIBED":
            return "✔ Transcrição concluída";

        case "SUMMARIZING":
            return "🧠 Gerando resumo";

        case "SUMMARIZED":
            return "✔ Resumo concluído";

        case "COMPLETED":
            return "✔ Processamento concluído";

        default:
            return stage || "Processando...";
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

            // ========================================
            // ACTIVE RUNTIME STREAM OWNERSHIP
            // ========================================
            switchActiveRuntimeStream(
                item.jobId,
                {
                    onEvent(eventName, payload) {

                        const runtimeState =
                            runtimeStateStore.get(
                                payload.jobId
                            );

                        if (
                            runtimeState &&
                            isTerminalRuntime(runtimeState)
                        ) {

                            console.log(
                                "[SSE TERMINAL IGNORE]",
                                payload.jobId,
                                runtimeState.status
                            );

                            return;
                        }

                        if (!payload || payload.jobId !== activeJobId ) {

                            console.log(
                                "[JOB SWITCH IGNORE]",
                                payload?.jobId,
                                activeJobId
                            );

                            return;
                        }

                        if (!runtimeState) {
                            return;
                        }

                        orchestrateRuntimePanelRerender(
                            payload.jobId
                        );
                    }
                }
            );

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

    await deleteJobs(ids);

    loadJobs();
}

async function deleteAll() {
    if (!confirm("Tem certeza que deseja deletar TODOS os jobs?")) return;

    await deleteJobs([]);

    loadJobs();
}

async function loadDetails(jobId) {
    const detailsEl = document.getElementById("details");

    detailsEl.innerHTML = "Carregando detalhes...";

    try {
        const data = await fetchJobDetails(jobId);

        if (jobId !== activeJobId) {

            console.log(
                "[DETAIL LOAD IGNORE] stale async response",
                {
                    requestedJobId: jobId,
                    activeJobId
                }
            );

            // evita overwrite visual tardio
            return;
        }

        renderLegacyDetailsShellBridge(data.data);

    } catch (err) {
        console.error("Erro ao carregar detalhes:", err);
        detailsEl.innerHTML = "Erro ao carregar detalhes";
    }
}

function mountLegacyRuntimeDetailsShell(
    detailsEl,
    job
) {
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
}

function renderLegacyDetailsShellBridge(job) {

    const detailsEl = document.getElementById("details");

    const createdAt = new Date(job.createdAt).toLocaleString();

    let processingTime = "-";

    if (job.startedAt && job.finishedAt) {
        const duration = (job.finishedAt - job.startedAt) / 1000 / 60;
        processingTime = duration.toFixed(2) + " min";
    }

    const runtimeState =
        runtimeStateStore.get(job.id);

    const existingRuntimePanel =
        document.getElementById(
            "runtime-stage"
        );

    const isSameActiveJob =
        activeDetailsJobId === job.id;

    if (
        existingRuntimePanel &&
        isSameActiveJob
    ) {

        console.log(
            "[RENDER DETAILS] destructive rerender skipped",
            {
                jobId: job.id
            }
        );

        const summaryEl =
            document.getElementById(
                "job-summary"
            );

        if (summaryEl) {

            summaryEl.innerText =
                job.summary || "(vazio)";
        }

        orchestrateRuntimePanelRerender(job.id);

        return;
    }

    activeDetailsJobId = job.id;

    mountLegacyRuntimeDetailsShell(
        details,
        job
    );

    // 👇 conteúdo seguro (SEM warning)
    document.getElementById("job-summary").innerText =
        job.summary || "(vazio)";

    // ========================================
    // STATE-DRIVEN RUNTIME RESTORE
    // ========================================

    // evita reset destrutivo quando já existe
    // estado realtime preservado no store
    if (runtimeState) {

        console.log(
            "[RUNTIME UI] restore from store",
            {
                jobId: job.id,
                timelineSize:
                    runtimeState.timeline?.length || 0,
                progress:
                runtimeState.progress,
                stage:
                runtimeState.stage
            }
        );

        restoreRuntimeVisualState(runtimeState);

    } else {

        console.log(
            "[RUNTIME UI] initialize empty runtime",
            {
                jobId: job.id
            }
        );

        resetLegacyRuntimeVisualState();
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

        const data = await ingestYoutube(url);

        clearTimeout(timeout);

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

        switchActiveRuntimeStream(jobId, {

            onEvent(eventName, payload) {

                const runtimeState =
                    runtimeStateStore.get(
                        payload.jobId
                    );

                if (
                    runtimeState &&
                    isTerminalRuntime(runtimeState)
                ) {

                    console.log(
                        "[SSE TERMINAL IGNORE]",
                        payload.jobId,
                        runtimeState.status
                    );

                    return;
                }

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

                    renderLegacyDetailsShellBridge({
                        id: payload.jobId,
                        status: payload.status,
                        stage: payload.stage,
                        transcription: "",
                        summary: "",
                        createdAt: Date.now()
                    });
                }

                // ========================================
                // STORE-FIRST RUNTIME RENDER
                // ========================================

                if (runtimeState) {

                    // limite memória
                    if (
                        runtimeState.timeline.length > 50
                    ) {

                        runtimeState.timeline.shift();
                    }

                    console.log(
                        "[RUNTIME STORE UPDATED]",
                        {
                            jobId: payload.jobId,
                            event: eventName,
                            stage: runtimeState.stage,
                            progress:
                            runtimeState.progress,
                            timeline:
                            runtimeState.timeline.length
                        }
                    );

                    orchestrateRuntimePanelRerender(
                        payload.jobId
                    );
                }

                // ========================================
                // SSE-FIRST RUNTIME UI
                // ========================================

                // evita conflito com polling legacy
                if (
                    eventName === "job_completed" ||
                    eventName === "job_failed"
                ) {

                    runtimeState.status =
                        eventName === "job_completed"
                            ? "DONE"
                            : "FAILED";

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

function applyTerminalRuntimeState(
    runtimeState,
    job
) {

    runtimeState.status =
        job.status;

    runtimeState.isTerminal = true;

    applyRuntimeProgressState(
        runtimeState,
        100
    );
}

function applyRuntimeProgressState(
    runtimeState,
    progress
) {

    runtimeState.progress =
        progress;
}

function applyRuntimeStageState(
    runtimeState,
    stage
) {

    runtimeState.stage =
        stage;
}

function updateIngestUI(job, elapsedSec = null) {

    if (!job) {
        return;
    }

    // ownership hardening
    if (
        activeJobId &&
        job.id !== activeJobId
    ) {

        console.log(
            "[UI OWNERSHIP] ignored stale panel update",
            {
                incomingJobId: job.id,
                activeJobId
            }
        );

        return;
    }

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

            renderLegacyDetailsShellBridge(job);

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

            const isTerminalStatus =
                job.status === "DONE" ||
                job.status === "FAILED" ||
                job.status === "CANCELED";

            const runtimeState =
                runtimeStateStore.get(job.id);

            if (runtimeState) {

                // TERMINAL IMMUTABILITY
                if (runtimeState.isTerminal) {

                    console.log(
                        "[UPDATE INGEST UI] terminal runtime preserved",
                        job.id
                    );

                    orchestrateRuntimePanelRerender(job.id);

                    return;
                }

                if (isTerminalStatus) {

                    applyTerminalRuntimeState(
                        runtimeState,
                        job
                    );

                } else {

                    applyRuntimeStageState(
                        runtimeState,
                        runtimeState.stage ||
                        "CREATED"
                    );
                }

                console.log(
                    "[UPDATE INGEST UI] store update applied",
                    {
                        jobId: job.id,
                        status: runtimeState.status,
                        stage: runtimeState.stage
                    }
                );

                orchestrateRuntimePanelRerender(job.id);
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

        const data = await jobsApi.fetchJobs({
            status,
            sort,
            limit: 20
        });

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

            await fetch(
                `/jobs/${activeJobId}/cancel`,
                {
                    method: "POST"
                }
            );

            // ========================================
            // TERMINAL RUNTIME CLEANUP
            // ========================================

            if (activeRuntimeStream) {

                runtimeStreamsByJob.delete(
                    activeJobId
                );

                activeRuntimeStream.close();

                activeRuntimeStream = null;
            }

            activeJobId = null;

        } catch (e) {

            console.error(
                "Erro ao cancelar job:",
                e
            );
        }
    }

    pollingActive = false;

    if (jobsRefreshTimeout) {
        clearTimeout(jobsRefreshTimeout);
    }

    if (ingestInterval) {
        clearTimeout(ingestInterval);
    }

    document.getElementById("status").innerText =
        "Atualização pausada";

    document.getElementById("ingestStatus").innerText =
        "Processamento pausado";
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

function restoreRecoveredRuntimeSession(
    savedJobId,
    job
) {

    activeJobId = savedJobId;

    renderLegacyDetailsShellBridge(job);
}

function restoreRecoveredRuntimeStream(
    savedJobId
) {

    if (
        runtimeConnections.has(savedJobId)
    ) {

        console.log(
            "[RUNTIME RECOVERY] connection already active"
        );

        return;
    }

    switchActiveRuntimeStream(
        savedJobId,
        {
            onEvent(eventName, payload) {

                const runtimeState =
                    runtimeStateStore.get(
                        payload.jobId
                    );

                if (
                    runtimeState &&
                    isTerminalRuntime(runtimeState)
                ) {

                    console.log(
                        "[SSE TERMINAL IGNORE]",
                        payload.jobId,
                        runtimeState.status
                    );

                    return;
                }

                if (!payload || payload.jobId !== activeJobId ) {

                    console.log(
                        "[RECOVERY SSE IGNORE] inactive payload",
                        payload?.jobId,
                        activeJobId
                    );

                    return;
                }

                if (!runtimeState) {

                    console.warn(
                        "[RECOVERY SSE] missing runtime state",
                        payload.jobId
                    );

                    return;
                }

                console.log(
                    "[RECOVERY STORE UPDATED]",
                    {
                        jobId: payload.jobId,
                        event: eventName,
                        stage: runtimeState.stage,
                        progress:
                        runtimeState.progress,
                        timeline:
                        runtimeState.timeline.length
                    }
                );

                orchestrateRuntimePanelRerender(
                    payload.jobId
                );
            }
        }
    );
}

async function bootstrapRuntimeRecovery() {

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

        restoreRecoveredRuntimeSession(
            savedJobId,
            job
        );

        restoreRecoveredRuntimeStream(
            savedJobId
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

window.addEventListener(
    "load",
    async () => {

        validateSubsystemBootstrap();

        await bootstrapRuntimeRecovery();
    }
);