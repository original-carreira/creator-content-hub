function createSelectionCollectionController() {

    /*
     * ============================================================
     * Estado Privado
     * ============================================================
     */

    const selections = [];

    let activeSelectionId = null;

    let nextSelectionId = 1;

    /*
     * ============================================================
     * API Pública
     * ============================================================
     */

    function addSelection(
        selection
    ) {

        if (
            !isValidSelection(
                selection
            )
        ) {
            return false;
        }

        selections.push({

            id: createSelectionId(),

            startTime:
                selection.startTime,

            endTime:
                selection.endTime

        });

        return true;
    }

    function removeSelection(
        selectionId
    ) {

        const selectionIndex =
            selections.findIndex(
                selection =>
                    selection.id === selectionId
            );

        if (
            selectionIndex === -1
        ) {
            return false;
        }

        selections.splice(
            selectionIndex,
            1
        );

        if (
            activeSelectionId === selectionId
        ) {
            activeSelectionId = null;
        }

        return true;
    }

    function getSelections() {
        return selections;
    }

    function getSelection(
        selectionId
    ) {
        return selections.find(
            selection =>
                selection.id === selectionId
        ) || null;
    }

    function hasSelection(
        selectionId
    ) {

        return (
            getSelection(
                selectionId
            ) !== null
        );
    }

    function hasSelections() {

        return selections.length > 0;
    }

    function getActiveSelection() {

        if (
            activeSelectionId === null
        ) {
            return null;
        }

        return getSelection(
            activeSelectionId
        );
    }

    function setActiveSelection(
        selectionId
    ) {

        if (
            !hasSelection(
                selectionId
            )
        ) {
            return false;
        }

        activeSelectionId = selectionId;

        return true;
    }

    /*
     * ============================================================
     * Regras Privadas
     * ============================================================
     */

    function createSelectionId() {

        const selectionId =
            `selection-${nextSelectionId}`;

        nextSelectionId += 1;

        return selectionId;
    }

    function isValidSelection(
        selection
    ) {

        if (
            !selection ||
            typeof selection !== "object"
        ) {
            return false;
        }

        if (
            !isValidTime(
                selection.startTime
            )
        ) {
            return false;
        }

        if (
            !isValidTime(
                selection.endTime
            )
        ) {
            return false;
        }

        return (
            selection.endTime >
            selection.startTime
        );
    }

    function isValidTime(
        time
    ) {

        return (

            typeof time ===
            "number" &&

            Number.isFinite(
                time
            ) &&

            time >= 0

        );
    }

    return {

        addSelection,

        removeSelection,

        getSelections,

        getSelection,

        hasSelection,

        hasSelections,

        getActiveSelection,

        setActiveSelection

    };
}

window.SelectionCollectionController = {

    createSelectionCollectionController
};
