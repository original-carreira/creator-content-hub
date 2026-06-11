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

    const exportRangesTxtButton =
        document.getElementById(
            "exportRangesTxtButton"
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

    if (exportRangesTxtButton) {

        exportRangesTxtButton.addEventListener(
            "click",
            async () => {

                const jobId =
                    workspaceStateApi
                        .workspaceState
                        .activeWorkspaceJobId;

                if (!jobId) {
                    return;
                }

                const ranges =
                    workspaceStateApi
                        .workspaceState
                        .selectionRanges;

                if (
                    !ranges ||
                    ranges.length === 0
                ) {
                    return;
                }

                const response =
                    await fetch(
                        `/jobs/${jobId}/export/ranges/txt`,
                        {
                            method: "POST",
                            headers: {
                                "Content-Type":
                                    "application/json"
                            },
                            body: JSON.stringify({
                                ranges
                            })
                        }
                    );

                if (!response.ok) {
                    return;
                }

                const blob =
                    await response.blob();

                const disposition =
                    response.headers.get(
                        "Content-Disposition"
                    );

                let filename =
                    "ranges-export.txt";

                if (disposition) {

                    const utf8Match =
                        disposition.match(
                            /filename\*="?UTF-8''([^";]+)"?/
                        );

                    if (utf8Match) {

                        filename =
                            decodeURIComponent(
                                utf8Match[1]
                            );
                    }
                    else {

                        const fallbackMatch =
                            disposition.match(
                                /filename="([^"]+)"/
                            );

                        if (fallbackMatch) {

                            filename =
                                fallbackMatch[1];
                        }
                    }
                }

                const url =
                    window.URL.createObjectURL(
                        blob
                    );

                const link =
                    document.createElement("a");

                link.href = url;

                link.download =
                    filename;

                document.body.appendChild(
                    link
                );

                link.click();

                document.body.removeChild(
                    link
                );

                window.URL.revokeObjectURL(
                    url
                );
            }
        );
    }
}

window.WorkspaceExportController = {

    bindWorkspaceExports
};