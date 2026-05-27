function bindTranscriptSearch(
    workspaceStateApi
) {

    const input =
        document.getElementById(
            "transcriptSearchInput"
        );

    if (!input) {
        return;
    }

    input.addEventListener(
        "input",
        (event) => {

            const value =
                event.target.value.trim();

            workspaceStateApi
                .setTranscriptSearchTerm(
                    value
                );

            window.TranscriptRenderer
                .renderTranscriptContent(
                    workspaceStateApi
                        .workspaceState
                        .originalTranscriptText,

                    workspaceStateApi
                        .workspaceState
                        .transcriptSearch
                        .searchTerm
                );
        }
    );
}

window.TranscriptSearchController = {

    bindTranscriptSearch
};