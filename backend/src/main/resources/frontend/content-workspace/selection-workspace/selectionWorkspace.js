function createSelectionWorkspace(
    dependencies
) {

    const {
        clipSelectionController,
        selectionCollectionController
    } = dependencies;

    function getSelection() {

        return clipSelectionController
            .getSelection();
    }

    function getSelectionCollection() {

        return selectionCollectionController;
    }

    function getMaterializedSelection(
        selectionId
    ) {

        return selectionCollectionController
            .getSelection(
                selectionId
            );
    }

    function updateMaterializedSelection(
        selectionId,
        startTime,
        endTime
    ) {

        return selectionCollectionController
            .updateSelection(
                selectionId,
                startTime,
                endTime
            );
    }

    function removeMaterializedSelection(
        selectionId
    ) {

        return selectionCollectionController
            .removeSelection(
                selectionId
            );

    }

    function removeAllMaterializedSelections() {

        return selectionCollectionController
            .removeAllSelections();

    }

    function materializeSelection() {

        const selection =
            clipSelectionController
                .getSelection();

        const selectionIdentity =
            selection.selectionIdentity;

        const materialized =
            selectionCollectionController
            .addSelection(
                selection
            );

        if (materialized) {

            clipSelectionController
                .markSelectionMaterialized(
                    selectionIdentity
                );

        }

        return materialized;
    }

    return {

        getSelection,

        getSelectionCollection,

        getMaterializedSelection,

        updateMaterializedSelection,

        removeMaterializedSelection,

        removeAllMaterializedSelections,

        materializeSelection

    };
}

window.SelectionWorkspace = {

    createSelectionWorkspace
};
