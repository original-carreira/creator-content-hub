const workspaceState = {

    activeWorkspaceJobId: null,

    originalTranscriptText: "",

    transcriptSearch: {

        searchTerm: "",

        selectedOccurrence: 0
    }
};

function setWorkspaceJobId(jobId) {

    workspaceState.activeWorkspaceJobId =
        jobId;
}

function setOriginalTranscriptText(text) {

    workspaceState.originalTranscriptText =
        text || "";
}

function setTranscriptSearchTerm(searchTerm) {

    workspaceState.transcriptSearch.searchTerm =
        searchTerm || "";
}

function setSelectedOccurrence(index) {

    workspaceState.transcriptSearch.selectedOccurrence =
        index || 0;
}

function resetTranscriptSearchState() {

    workspaceState.transcriptSearch.searchTerm =
        "";

    workspaceState.transcriptSearch.selectedOccurrence =
        0;
}

window.ContentWorkspaceState = {

    workspaceState,

    setWorkspaceJobId,

    setOriginalTranscriptText,

    setTranscriptSearchTerm,

    setSelectedOccurrence,

    resetTranscriptSearchState
};