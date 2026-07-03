const workspaceState = {

    activeWorkspaceJobId: null,

    assets: [],

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

    clipSelection: {

        startTime: null,

        endTime: null
    },

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

function setAssets(assets) {

    workspaceState.assets =
        assets || [];
}

function getVideoAsset() {

    return workspaceState.assets.find(
        asset =>
            asset.assetType === "VIDEO"
    ) || null;
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

function removeSelectionRange(index) {

    if (
        index < 0 ||
        index >= workspaceState.selectionRanges.length
    ) {
        return;
    }

    workspaceState.selectionRanges.splice(
        index,
        1
    );
}

function clearSelectionRanges() {

    workspaceState.selectionRanges.length = 0;
}

function setClipSelectionStart(
    startTime
) {

    workspaceState.clipSelection.startTime =
        startTime;
}

function setClipSelectionEnd(
    endTime
) {

    workspaceState.clipSelection.endTime =
        endTime;
}

function setClipSelection(
    startTime,
    endTime
) {

    workspaceState.clipSelection.startTime =
        startTime;

    workspaceState.clipSelection.endTime =
        endTime;
}

function clearClipSelection() {

    workspaceState.clipSelection.startTime =
        null;

    workspaceState.clipSelection.endTime =
        null;
}

function getClipSelection() {

    return workspaceState.clipSelection;
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

    setAssets,

    getVideoAsset,

    setAssetCollection,

    setTranscript,

    setActiveSegmentIndex,

    setPendingRangeStart,

    addSelectionRange,

    removeSelectionRange,

    clearSelectionRanges,

    setClipSelectionStart,

    setClipSelectionEnd,

    setClipSelection,

    clearClipSelection,

    getClipSelection,

    setTranscriptSearchTerm,

    setSelectedOccurrence,

    resetTranscriptSearchState
};