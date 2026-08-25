function createSelectionWorkspace(
    dependencies
) {

    const {
        clipSelectionController
    } = dependencies;

    function getSelection() {

        return clipSelectionController
            .getSelection();
    }

    return {

        getSelection

    };
}

window.SelectionWorkspace = {

    createSelectionWorkspace
};
