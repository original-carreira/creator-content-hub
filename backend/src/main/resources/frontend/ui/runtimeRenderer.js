/*
 * RuntimeRenderer
 *
 * RESPONSIBILITIES:
 * - runtime UI rendering
 * - timeline rendering
 * - runtime progress rendering
 * - runtime stage rendering
 * - terminal runtime visual preservation
 * - visual hydration
 *
 * GLOBAL DEPENDENCIES:
 * - runtimeStateStore
 * - activeJobId
 * - DOM runtime containers
 *
 * IMPORTANT:
 * - renderer must NOT control reconnect lifecycle
 * - renderer must NOT create EventSource
 * - renderer must NOT perform fetch/network operations
 * - renderer must NOT orchestrate runtime ownership
 * - renderer must NOT mutate transport lifecycle
 *
 * CURRENT ARCHITECTURAL NOTE:
 * - renderer is intentionally runtime-aware
 * - renderer currently depends on runtimeStateStore
 * - renderer currently depends on activeJobId
 * - renderer is the most dependency-sensitive module
 *   for future ES Module migration
 */


function updateDownloadProgress(progress) {

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

function appendRuntimeEvent(eventName,payload) {

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

function rerenderRuntimeProgress(runtimeState) {

    if (
        !runtimeState ||
        typeof runtimeState.progress !==
        "number"
    ) {
        return;
    }

    updateDownloadProgress(
        runtimeState.progress
    );

    console.log(
        "[RUNTIME UI] progress rerender",
        {
            progress:
            runtimeState.progress
        }
    );
}

function rerenderRuntimeStage(runtimeState) {

    if (!runtimeState) {
        return;
    }

    const runtimeStage =
        document.getElementById(
            "runtime-stage"
        );

    if (!runtimeStage) {
        return;
    }

    const nextStage =
        getRuntimeStageLabel(
            runtimeState.stage,
            runtimeState.status
        );

    // evita repaint desnecessário
    if (
        runtimeStage.innerText !==
        nextStage
    ) {

        runtimeStage.innerText =
            nextStage;

        console.log(
            "[RUNTIME UI] stage rerender",
            {
                stage: nextStage
            }
        );
    }
}

function rerenderRuntimeTimeline(runtimeState) {

    if (
        !runtimeState ||
        !Array.isArray(
            runtimeState.timeline
        )
    ) {
        return;
    }

    const timeline =
        document.getElementById(
            "runtime-timeline"
        );

    if (!timeline) {
        return;
    }

    const incomingSignature =
        JSON.stringify(
            runtimeState.timeline.map(item => ({
                event: item.event,
                timestamp: item.timestamp
            }))
        );

    const currentSignature =
        timeline.dataset.signature || "";

    if (
        currentSignature ===
        incomingSignature
    ) {

        console.log(
            "[TIMELINE RERENDER SKIPPED] identical signature"
        );

        return;
    }

    timeline.dataset.signature =
        incomingSignature;

    timeline.innerHTML = "";

    runtimeState.timeline
        .slice()
        .reverse()
        .forEach(item => {

            appendRuntimeEvent(
                item.event,
                item.payload
            );
        });

    console.log(
        "[RUNTIME UI] timeline rerender",
        {
            items:
            runtimeState.timeline.length
        }
    );
}

function rerenderRuntimePanel(jobId) {

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

        console.log(
            "[TERMINAL RUNTIME]",
            runtimeState.jobId,
            runtimeState.status
        );

        // preserva último stage terminal
        const runtimeStage =
            document.getElementById(
                "runtime-stage"
            );

        if (
            runtimeState.stage &&
            runtimeStage
        ) {

            runtimeStage.innerText =
                getRuntimeStageLabel(
                    runtimeState.stage,
                    runtimeState.status
                );
        }

        // preserva progress final
        if (
            typeof runtimeState.progress ===
            "number"
        ) {

            updateDownloadProgress(
                runtimeState.progress
            );
        }

        // renderiza timeline persistida
        rerenderRuntimeTimeline(
            runtimeState
        );

        return;
    }

    rerenderRuntimeStage(
        runtimeState
    );

    rerenderRuntimeProgress(
        runtimeState
    );

    rerenderRuntimeTimeline(
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

function rerenderRuntimeVisuals(runtimeState) {

    rerenderRuntimeStage(
        runtimeState
    );

    rerenderRuntimeProgress(
        runtimeState
    );

    rerenderRuntimeTimeline(
        runtimeState
    );
}

function hydrateJobSummary(job){
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
}


window.RuntimeRenderer = {
    updateDownloadProgress,
    appendRuntimeEvent,
    rerenderRuntimeProgress,
    rerenderRuntimeStage,
    rerenderRuntimeTimeline,
    renderTerminalRuntime,
    rerenderRuntimeVisuals,
    hydrateJobSummary
};