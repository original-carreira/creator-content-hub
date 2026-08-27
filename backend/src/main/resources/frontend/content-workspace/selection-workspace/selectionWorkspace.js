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

    function materializeSelection() {

        const selection =
            clipSelectionController
                .getSelection();

        return selectionCollectionController
            .addSelection(
                selection
            );
    }

    return {

        getSelection,

        getSelectionCollection,

        materializeSelection

    };
}

window.SelectionWorkspace = {

    createSelectionWorkspace
};
