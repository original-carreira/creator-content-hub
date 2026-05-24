// ========================================
// RUNTIME STATE STORES
// ========================================

const runtimeConnections = new Map();

const runtimeConnectionLocks = new Map();

const runtimeReconnectTimers = new Map();

const runtimeSessions = new Map();

const runtimeListeners = new Map();

const runtimeReconnectStates = new Map();

const runtimeStateStore = new Map();

const runtimeStreamsByJob = new Map();

const runtimeFallbackIntervals = new Map();

// ========================================
// CONSTANTS
// ========================================

const TERMINAL_STATUSES = new Set([
    "DONE",
    "FAILED",
    "CANCELED"
]);

// ========================================
// CANONICAL STATE
// ========================================

function createCanonicalRuntimeState(jobId) {

    return {
        jobId,
        version: 0,
        status: null,
        stage: null,
        progress: 0,
        timeline: [],
        retryState: null,
        workerState: null,
        isTerminal: false,
        lastUpdate: Date.now(),
        lastEventId: null,
        hydrationVersion: 0
    };
}

// ========================================
// TERMINAL CHECK
// ========================================

function isTerminalRuntime(runtimeState) {

    if (!runtimeState) {
        return false;
    }

    return TERMINAL_STATUSES.has(
        runtimeState.status
    );
}

// ========================================
// MUTATION ENGINE
// ========================================

function applyRuntimeMutation(
    jobId,
    eventName,
    payload = {},
    source = "unknown"
) {

    if (!jobId) {
        return null;
    }

    let runtimeState =
        runtimeStateStore.get(jobId);

    if (!runtimeState) {

        runtimeState =
            createCanonicalRuntimeState(jobId);

        runtimeStateStore.set(
            jobId,
            runtimeState
        );

        console.log(
            "[RUNTIME] canonical state created",
            { jobId }
        );
    }

// ========================================
// TERMINAL IMMUTABILITY
// ========================================

    if (runtimeState.isTerminal) {

        console.log(
            "[terminal_runtime_preserved]",
            {
                jobId,
                source,
                eventName
            }
        );

        return runtimeState;
    }

// ========================================
// STATUS
// ========================================

    if (payload.status) {

        runtimeState.status =
            payload.status;
    }

// ========================================
// TERMINAL LOCK
// ========================================

    if (
        payload.status === "DONE" ||
        payload.status === "FAILED" ||
        payload.status === "CANCELED" ||
        eventName === "job_completed" ||
        eventName === "job_failed" ||
        eventName === "dlq_transition"
    ) {

        runtimeState.isTerminal = true;

        runtimeState.progress = 100;

        console.log(
            "[runtime_terminal_locked]",
            {
                jobId,
                status: payload.status,
                eventName
            }
        );
    }

// ========================================
// STAGE
// ========================================

    if (payload.stage) {

        runtimeState.stage =
            payload.stage;
    }

// ========================================
// MONOTONIC PROGRESS
// ========================================

    if (
        typeof payload.progress ===
        "number"
    ) {

        runtimeState.progress =
            Math.max(
                runtimeState.progress || 0,
                payload.progress
            );
    }

// ========================================
// TIMELINE APPEND-ONLY
// ========================================

    runtimeState.timeline.push({
        event: eventName,
        payload,
        timestamp: Date.now(),
        source
    });

// proteção memória
    if (runtimeState.timeline.length > 50) {

        runtimeState.timeline.shift();
    }

// ========================================
// METADATA
// ========================================

    runtimeState.version++;

    runtimeState.lastUpdate =
        Date.now();

    console.log(
        "[runtime_snapshot_updated]",
        {
            jobId,
            version: runtimeState.version,
            source,
            eventName,
            status: runtimeState.status,
            stage: runtimeState.stage,
            progress: runtimeState.progress,
            timeline:
            runtimeState.timeline.length
        }
    );

    return runtimeState;
}

// ========================================
// GLOBAL EXPORT
// ========================================

window.RuntimeState = {

    runtimeConnections,
    runtimeConnectionLocks,
    runtimeReconnectTimers,
    runtimeSessions,
    runtimeListeners,
    runtimeReconnectStates,
    runtimeStateStore,
    runtimeStreamsByJob,
    runtimeFallbackIntervals,

    TERMINAL_STATUSES,

    createCanonicalRuntimeState,
    applyRuntimeMutation,
    isTerminalRuntime
};