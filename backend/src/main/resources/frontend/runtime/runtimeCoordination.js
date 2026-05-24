function rerenderRuntimeJob(jobId) {
    rerenderRuntimePanel(jobId);
}

function clearReconnectTimer(session) {
    if (session?.reconnectTimer) {
        clearTimeout(
            session.reconnectTimer
        );
    }
}

function registerReconnectTimer(session, jobId, timer, runtimeReconnectTimers) {
    session.reconnectTimer =
        timer;

    runtimeReconnectTimers.set(
        jobId,
        timer
    );
}

function scheduleReconnectTimer(delay, reconnectCallback) {
    return setTimeout(
        reconnectCallback,
        delay
    );
}

function executeReconnect(reconnectState, reconnectCallback, jobId, shouldSkipReconnect) {
    if (
        shouldSkipReconnect(
            reconnectState
        )
    ) {
        console.log(
            "[SSE RECONNECT ABORTED]",
            { jobId }
        );

        return;
    }

    reconnectCallback();
}

function orchestrateReconnect({
                                  reconnectState,
                                  maxReconnectAttempts,
                                  baseReconnectDelay,
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
                              }) {

    reconnectState.attempts++;

    if (
        shouldAbortReconnect(
            reconnectState.attempts,
            maxReconnectAttempts
        )
    ) {

        console.error(
            "[SSE] reconnect limit reached:",
            jobId
        );

        return;
    }

    const delay =
        calculateReconnectDelay(
            baseReconnectDelay,
            reconnectState.attempts
        );

    clearReconnectTimer(
        session
    );

    const timer =
        scheduleReconnectTimer(
            delay,
            () => executeReconnect(
                reconnectState,
                connect,
                jobId,
                shouldSkipReconnect
            )
        );

    registerReconnectTimer(
        session,
        jobId,
        timer,
        runtimeReconnectTimers
    );
}

window.RuntimeCoordination = {
    rerenderRuntimeJob,
    clearReconnectTimer,
    registerReconnectTimer,
    scheduleReconnectTimer,
    executeReconnect,
    orchestrateReconnect
};