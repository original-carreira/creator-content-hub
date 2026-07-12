function createClipSelectionController(
    workspaceStateApi
) {

    /*
     * ============================================================
     * API Pública
     * ============================================================
     */

    function setClipStartTime(
        startTime
    ) {

        if (
            !canDefineStartTime(
                startTime
            )
        ) {
            return false;
        }

        workspaceStateApi
            .setClipSelectionStart(
                startTime
            );

        return true;
    }

    function setClipEndTime(
        endTime
    ) {

        const selection =
            getSelection();

        if (
            !canDefineEndTime(
                selection,
                endTime
            )
        ) {
            return false;
        }

        workspaceStateApi
            .setClipSelectionEnd(
                endTime
            );

        return true;
    }

    function setSelection(
        startTime,
        endTime
    ) {

        if (
            !canDefineSelection(
                startTime,
                endTime
            )
        ) {
            return false;
        }

        workspaceStateApi
            .setClipSelection(
                startTime,
                endTime
            );

        return true;
    }

    function clearSelection() {

        workspaceStateApi
            .clearClipSelection();

        return true;
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

    function hasActiveSelection() {

        const selection =
            getSelection();

        return (

            selection.startTime !== null

        );

    }

    /*
     * ============================================================
     * Regras Privadas
     * ============================================================
     */

    function canDefineStartTime(
        startTime
    ) {

        return isValidStartTime(
            startTime
        );
    }

    function canDefineEndTime(
        selection,
        endTime
    ) {

        if (
            !isValidEndTime(
                endTime
            )
        ) {
            return false;
        }

        if (
            selection.startTime === null
        ) {
            return false;
        }

        return (
            endTime >
            selection.startTime
        );
    }

    function canDefineSelection(
        startTime,
        endTime
    ) {

        if (
            !isValidStartTime(
                startTime
            )
        ) {
            return false;
        }

        if (
            !isValidEndTime(
                endTime
            )
        ) {
            return false;
        }

        return (
            endTime >
            startTime
        );
    }

    function isValidStartTime(
        startTime
    ) {

        return (

            typeof startTime ===
            "number" &&

            Number.isFinite(
                startTime
            ) &&

            startTime >= 0

        );
    }

    function isValidEndTime(
        endTime
    ) {

        return (

            typeof endTime ===
            "number" &&

            Number.isFinite(
                endTime
            ) &&

            endTime >= 0

        );
    }

    return {

        setClipStartTime,

        setClipEndTime,

        setSelection,

        clearSelection,

        getSelection,

        hasSelection,

        hasActiveSelection

    };
}

window.ClipSelectionController = {

    createClipSelectionController
};