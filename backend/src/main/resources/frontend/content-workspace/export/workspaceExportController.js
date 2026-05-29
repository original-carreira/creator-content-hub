function bindWorkspaceExports(
    workspaceStateApi
) {

    const exportTxtButton =
        document.getElementById(
            "exportTxtButton"
        );

    const exportDocxButton =
        document.getElementById(
            "exportDocxButton"
        );

    if (
        !exportTxtButton ||
        !exportDocxButton
    ) {
        return;
    }

    exportTxtButton.addEventListener(
        "click",
        () => {

            const jobId =
                workspaceStateApi
                    .workspaceState
                    .activeWorkspaceJobId;

            if (!jobId) {
                return;
            }

            window.location.href =
                `/jobs/${jobId}/export/txt`;
        }
    );

    exportDocxButton.addEventListener(
        "click",
        () => {

            const jobId =
                workspaceStateApi
                    .workspaceState
                    .activeWorkspaceJobId;

            if (!jobId) {
                return;
            }

            window.location.href =
                `/jobs/${jobId}/export/docx`;
        }
    );
}

window.WorkspaceExportController = {

    bindWorkspaceExports
};