function createClipSelectionController(
    workspaceStateApi
) {

    function setClipStartTime(
        startTime
    ) {

        workspaceStateApi
            .setClipSelectionStart(
                startTime
            );
    }

    function setClipEndTime(
        endTime
    ) {

        workspaceStateApi
            .setClipSelectionEnd(
                endTime
            );
    }

    function setSelection(
        startTime,
        endTime
    ) {

        workspaceStateApi
            .setClipSelection(
                startTime,
                endTime
            );
    }

    function clearSelection() {

        workspaceStateApi
            .clearClipSelection();
    }

    function getSelection() {

        return workspaceStateApi
            .getClipSelection();
    }

    function hasSelection() {

        const selection =
            getSelection();

        return (

            selection.startTime !== null &&

            selection.endTime !== null

        );
    }

    return {

        setClipStartTime,

        setClipEndTime,

        setSelection,

        clearSelection,

        getSelection,

        hasSelection
    };
}

window.ClipSelectionController = {

    createClipSelectionController
};