const workspaceState = {

    activeWorkspaceJobId: null,

    assetCollection: {

        collectionId: null,

        version: 1,

        assets: []
    },

    transcript: null,

    activeSegmentIndex: null,

    pendingRange: {

        startIndex: null
    },

    selectionRanges: [],

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

function setTranscript(transcript) {

    workspaceState.transcript =
        transcript || null;
}

function setActiveSegmentIndex(index) {

    workspaceState.activeSegmentIndex =
        index;
}

function setPendingRangeStart(
    startIndex
) {

    workspaceState.pendingRange.startIndex =
        startIndex;
}

function addSelectionRange(
    startIndex,
    endIndex
) {

    workspaceState.selectionRanges.push({

        startIndex,

        endIndex
    });
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

    setTranscript,

    setActiveSegmentIndex,

    setPendingRangeStart,

    addSelectionRange,

    setTranscriptSearchTerm,

    setSelectedOccurrence,

    resetTranscriptSearchState
};