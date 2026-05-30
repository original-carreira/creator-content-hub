function buildActions(asset) {

    const actions = [];

    if (asset.downloadable) {
        actions.push({
            actionId: "download",
            label: "Download"
        });
    }

    if (asset.playable) {
        actions.push({
            actionId: "play",
            label: "Play"
        });
    }

    if (asset.editable) {
        actions.push({
            actionId: "edit",
            label: "Edit"
        });
    }

    if (asset.viewable) {
        actions.push({
            actionId: "view",
            label: "View"
        });
    }

    return actions;
}

window.AssetActionFactory = {

    buildActions
};