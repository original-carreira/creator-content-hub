function shouldCleanupTerminalJob(job){

    return (
        job.status === "DONE" ||
        job.status === "FAILED"
    );
}

function shouldAbortReconnect(reconnectAttempts, maxReconnectAttempts) {
    return (
        reconnectAttempts >
        maxReconnectAttempts
    );
}

function calculateReconnectDelay(baseReconnectDelay, reconnectAttempts) {
    return (
        baseReconnectDelay *
        reconnectAttempts
    );
}

function shouldSkipReconnect(reconnectState) {
    return (
        reconnectState.closedManually ||
        reconnectState.terminallyClosed
    );
}

window.RuntimeLifecycle = {
    shouldCleanupTerminalJob,
    shouldAbortReconnect,
    calculateReconnectDelay,
    shouldSkipReconnect
};