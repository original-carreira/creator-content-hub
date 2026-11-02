function createSelectionCollectionController(
    options = {}
) {

    const onChange =
        options?.onChange ?? null;

    /*
     * ============================================================
     * Estado Privado
     * ============================================================
     */

    const selections = [];

    const materializedSelectionSourceCounts =
        new Map();

    const materializedSelectionSourceByItem =
        new WeakMap();

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

        const materializedSelection = {

            id: createSelectionId(),

            startTime:
                selection.startTime,

            endTime:
                selection.endTime

        };

        selections.push(
            materializedSelection
        );

        if (
            selection.selectionIdentity !== undefined &&
            selection.selectionIdentity !== null
        ) {

            const sourceIdentity =
                selection.selectionIdentity;

            const sourceCount =
                materializedSelectionSourceCounts
                    .get(sourceIdentity) || 0;

            materializedSelectionSourceCounts
                .set(
                    sourceIdentity,
                    sourceCount + 1
                );

            materializedSelectionSourceByItem
                .set(
                    materializedSelection,
                    sourceIdentity
                );

        }

        notifyChange();

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

        const [removedSelection] =
            selections.splice(
                selectionIndex,
                1
            );

        decrementMaterializedSourceCount(
            removedSelection
        );

        if (
            activeSelectionId === selectionId
        ) {
            activeSelectionId = null;
        }

        notifyChange();

        return true;
    }

    function removeAllSelections() {

        if (selections.length === 0) {
            return false;
        }

        selections.forEach(
            decrementMaterializedSourceCount
        );

        selections.length = 0;
        activeSelectionId = null;

        notifyChange();

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

    function updateSelection(
        selectionId,
        startTime,
        endTime
    ) {

        const selection =
            getSelection(
                selectionId
            );

        if (
            !selection ||
            !isValidSelection({
                startTime,
                endTime
            })
        ) {
            return false;
        }

        selection.startTime =
            startTime;

        selection.endTime =
            endTime;

        notifyChange();

        return true;
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

    function containsSelectionSource(
        selection
    ) {

        return (
            selection !== null &&
            typeof selection === "object" &&
            selection.selectionIdentity !== undefined &&
            selection.selectionIdentity !== null &&
            materializedSelectionSourceCounts.has(
                selection.selectionIdentity
            )
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

    function notifyChange() {

        if (typeof onChange === "function") {
            onChange();
        }

    }

    function decrementMaterializedSourceCount(
        materializedSelection
    ) {

        const sourceIdentity =
            materializedSelectionSourceByItem
                .get(materializedSelection);

        if (
            sourceIdentity === undefined
        ) {
            return;
        }

        const sourceCount =
            materializedSelectionSourceCounts
                .get(sourceIdentity) || 0;

        if (sourceCount <= 1) {

            materializedSelectionSourceCounts
                .delete(sourceIdentity);

        } else {

            materializedSelectionSourceCounts
                .set(
                    sourceIdentity,
                    sourceCount - 1
                );

        }

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

        removeAllSelections,

        getSelections,

        getSelection,

        updateSelection,

        hasSelection,

        containsSelectionSource,

        hasSelections,

        getActiveSelection,

        setActiveSelection

    };
}

window.SelectionCollectionController = {

    createSelectionCollectionController
};
