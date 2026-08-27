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

    return {

        getSelection,

        getSelectionCollection

    };
}

window.SelectionWorkspace = {

    createSelectionWorkspace
};
