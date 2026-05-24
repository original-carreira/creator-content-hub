/*
 * RuntimeSessionManager
 *
 * RESPONSIBILITIES:
 * - runtime session lifecycle
 * - runtime teardown lifecycle
 * - reconnect cleanup lifecycle
 * - session invalidation
 *
 * GLOBAL DEPENDENCIES:
 * - runtimeSessions
 * - runtimeConnections
 * - runtimeReconnectTimers
 * - runtimeFallbackIntervals
 * - runtimeListeners
 * - runtimeReconnectStates
 * - runtimeConnectionLocks
 * - activeRuntimeStream
 * - activeJobId
 *
 * IMPORTANT:
 * - transport lifecycle still belongs to app.js
 * - EventSource orchestration still belongs to app.js
 * - renderer lifecycle must NOT be moved here
 */

var getRuntimeSession =
    window.RuntimeState.getRuntimeSession;

function createRuntimeSession(jobId) {

    let session = runtimeSessions.get(jobId);

    if (session) {

        console.warn(
            "[RUNTIME_SESSION] reuse existing session",
            {
                jobId,
                ownerId: session.ownerId,
                generation: session.generation
            }
        );

        return session;
    }

    session = {
        jobId,
        ownerId: crypto.randomUUID(),

        generation: 0,

        createdAt: Date.now(),

        activeConnectionGeneration: null,

        eventSource: null,

        reconnectTimer: null,
        fallbackInterval: null,

        reconnectState: {
            attempts: 0,
            closedManually: false,
            terminallyClosed: false
        },

        handlers: null
    };

    runtimeSessions.set(jobId, session);

    console.log(
        "[RUNTIME_SESSION] created",
        { jobId }
    );

    return session;
}

function destroyRuntimeSession(jobId) {

    const session =
        runtimeSessions.get(jobId);

    if (!session) {
        return;
    }

    console.log(
        "[RUNTIME_SESSION] destroy",
        { jobId }
    );

    runtimeSessions.delete(jobId);
}

function cleanupRuntimeSession(jobId) {

    const session =
        getRuntimeSession(jobId);

    if (!session) {

        console.warn(
            "[RUNTIME CLEANUP] missing session",
            { jobId }
        );
    }

    console.log(
        "[RUNTIME CLEANUP] start",
        jobId
    );

    // SSE CONNECTION
    const eventSource =
        session?.eventSource ||
        runtimeConnections.get(jobId);

    if (eventSource) {

        eventSource.onopen = null;
        eventSource.onerror = null;

        eventSource.close();
    }

    runtimeConnections.delete(jobId);

    if (session) {

        session.eventSource = null;
    }

    if (session) {

        session.generation++;

        session.activeConnectionGeneration = null;

        console.warn(
            "[RUNTIME SESSION] generation invalidated",
            {
                jobId,
                ownerId: session.ownerId,
                generation: session.generation
            }
        );

        console.log(
            "[RUNTIME SESSION INVALIDATED]",
            {
                jobId,
                ownerId: session.ownerId,
                generation: session.generation
            }
        );
    }

    // RECONNECT TIMER
    const reconnectTimer =
        session?.reconnectTimer ||
        runtimeReconnectTimers.get(jobId);

    if (reconnectTimer) {

        clearTimeout(reconnectTimer);

        runtimeReconnectTimers.delete(jobId);
    }

    // FALLBACK INTERVAL
    const fallbackInterval =
        runtimeFallbackIntervals.get(jobId);

    if (fallbackInterval) {

        clearInterval(fallbackInterval);

        runtimeFallbackIntervals.delete(jobId);
    }

    // LISTENERS
    runtimeListeners.delete(jobId);

    // STREAM REGISTRY
    //runtimeStreamsByJob.delete(jobId);
    // preserve runtime ownership registry
    // during graceful/terminal cleanup

    console.log(
        "[RUNTIME CLEANUP] stream registry preserved",
        jobId
    );

    // RECONNECT STATE
    const reconnectState =
        runtimeReconnectStates.get(jobId);

    if (reconnectState) {

        reconnectState.terminallyClosed = true;
    }

    console.log(
        "[RUNTIME CLEANUP] reconnect lifecycle preserved",
        jobId
    );

    //runtimeReconnectStates.delete(jobId);
    // preserve reconnect lifecycle metadata
    console.log(
        "[RUNTIME CLEANUP] reconnect state preserved",
        jobId
    );

    // ACTIVE STREAM
    if (
        activeRuntimeStream &&
        activeJobId === jobId
    ) {

        activeRuntimeStream = null;
    }

    // ACTIVE JOB
    // terminal ownership preservation
    // NÃO limpar activeJobId durante cleanup normal

    // LOCAL STORAGE
    // terminal runtime preservation
    // NÃO remover activeRuntimeJobId
    // durante cleanup terminal/graceful

    console.log(
        "[RUNTIME CLEANUP] terminal runtime preserved",
        {
            jobId,
            activeJobId
        }
    );

    runtimeConnectionLocks.delete(
        jobId
    );

    window.RuntimeSessionManager.destroyRuntimeSession(jobId)

    console.log(
        "[RUNTIME CLEANUP] completed",
        jobId
    );
}

window.RuntimeSessionManager = {
    createRuntimeSession,
    destroyRuntimeSession,
    cleanupRuntimeSession
};