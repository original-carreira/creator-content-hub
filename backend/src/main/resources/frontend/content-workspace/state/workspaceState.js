const workspaceState = {

    activeWorkspaceJobId: null,

    assetCollection: {

        collectionId: null,

        version: 1,

        assets: []
    },

    originalTranscriptText: "",

    transcriptSearch: {

        searchTerm: "",

        selectedOccurrence: 0,

        totalOccurrences: 0,

        activeOccurrenceIndex: 0
    }
};

function setWorkspaceJobId(jobId) {

    workspaceState.activeWorkspaceJobId =
        jobId;
}

function setAssetCollection(assetCollection) {

    workspaceState.assetCollection =
        assetCollection;
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

    workspaceState.transcriptSearch.totalOccurrences =
        0;

    workspaceState.transcriptSearch.activeOccurrenceIndex =
        0;
}

window.ContentWorkspaceState = {

    workspaceState,

    setWorkspaceJobId,

    setAssetCollection,

    setOriginalTranscriptText,

    setTranscriptSearchTerm,

    setSelectedOccurrence,

    resetTranscriptSearchState
};