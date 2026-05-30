function bindAssetActions(
    workspaceStateApi
) {

    document.addEventListener(
        "click",
        (event) => {

            const button =
                event.target.closest(
                    "[data-action-id]"
                );

            if (!button) {
                return;
            }

            const assetId =
                button.dataset.assetId;

            const actionId =
                button.dataset.actionId;

            if (
                assetId === "mp3" &&
                actionId === "download"
            ) {

                const jobId =
                    workspaceStateApi
                        .workspaceState
                        .activeWorkspaceJobId;

                if (!jobId) {
                    return;
                }

                window.location.href =
                    `/jobs/${jobId}/assets/mp3/download`;
            }
        }
    );
}

window.AssetActionController = {

    bindAssetActions
};