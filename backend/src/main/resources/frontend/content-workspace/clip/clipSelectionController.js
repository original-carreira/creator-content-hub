function createClipSelectionController(
    workspaceStateApi
) {

    let currentSelectionIdentity =
        createSelectionIdentity();

    let currentSelectionIsMaterialized =
        false;

    const currentSelection =
        workspaceStateApi
            .getClipSelection();

    Object.defineProperty(
        currentSelection,
        "selectionIdentity",
        {

            configurable: true,

            enumerable: false,

            get() {

                return currentSelectionIdentity;

            }

        }
    );

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

        if (
            getSelection().startTime !==
            startTime
        ) {

            beginNewSelectionAfterMaterialization();

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

        if (
            selection.endTime !==
            endTime
        ) {

            beginNewSelectionAfterMaterialization();

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

        const selection =
            getSelection();

        if (
            selection.startTime !== startTime ||
            selection.endTime !== endTime
        ) {

            beginNewSelectionAfterMaterialization();

        }

        workspaceStateApi
            .setClipSelection(
                startTime,
                endTime
            );

        return true;
    }

    function clearSelection() {

        currentSelectionIdentity =
            createSelectionIdentity();

        currentSelectionIsMaterialized =
            false;

        workspaceStateApi
            .clearClipSelection();

        return true;
    }

    function markSelectionMaterialized(
        selectionIdentity
    ) {

        if (
            selectionIdentity !==
            currentSelectionIdentity
        ) {
            return false;
        }

        currentSelectionIsMaterialized =
            true;

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

    function beginNewSelectionAfterMaterialization() {

        if (
            !currentSelectionIsMaterialized
        ) {
            return;
        }

        currentSelectionIdentity =
            createSelectionIdentity();

        currentSelectionIsMaterialized =
            false;

    }

    function createSelectionIdentity() {

        return Symbol(
            "current-selection"
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

        hasActiveSelection,

        markSelectionMaterialized

    };
}

window.ClipSelectionController = {

    createClipSelectionController
};
