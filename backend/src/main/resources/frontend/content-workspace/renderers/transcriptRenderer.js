function renderTranscriptWorkspace() {

    return `
        <div class="detail-block">

            <div
                style="
                    display:flex;
                    justify-content:space-between;
                    align-items:center;
                    margin-bottom:10px;
                "
            >
                <strong>Transcrição Completa</strong>

                <span
                    style="
                        font-size:12px;
                        color:#666;
                    "
                >
                    Workspace de Conteúdo
                </span>
            </div>

            <div
                id="transcriptionContainer"
                style="
                    border:1px solid #ddd;
                    border-radius:10px;
                    background:#fafafa;
                    padding:16px;
                    max-height:520px;
                    overflow-y:auto;
                "
            >
                <div
                    id="transcription"
                    style="
                        white-space:normal;
                        overflow-wrap:anywhere;
                        word-break:normal;
                        line-height:1.7;
                        font-size:14px;
                        font-family:inherit;
                    "
                ></div>
            </div>
        </div>
    `;
}

function escapeHtml(text) {

    return text
        .replace(/&/g,"&amp;")
        .replace(/</g,"&lt;")
        .replace(/>/g,"&gt;");
}

function renderTranscriptContent(
    text,
    searchTerm = "",
    activeOccurrenceIndex = -1
) {

    const transcriptionEl =
        document.getElementById(
            "transcription"
        );

    if (!transcriptionEl) {
        return;
    }

    if (!searchTerm) {

        transcriptionEl.innerText =
            text || "(vazio)";

        return;
    }

    const escapedText =
        escapeHtml(
            text || "(vazio)"
        );

    const escapedSearch =
        escapeHtml(
            searchTerm
        );

    const regex =
        new RegExp(
            `(${escapedSearch})`,
            "gi"
        );

    let occurrenceIndex = 0;

    const highlighted =
        escapedText.replace(
            regex,
            (match) => {

                const currentIndex =
                    occurrenceIndex++;

                const isActive =
                    currentIndex === activeOccurrenceIndex;

                const className =
                    isActive
                        ? "transcript-highlight-active"
                        : "transcript-highlight";

                return `
                    <mark
                        class="${className}"
                        data-occurrence-index="${currentIndex}"
                    >
                        ${match}
                    </mark>
                `;
            }
        );

    transcriptionEl.innerHTML =
        highlighted;

}

window.TranscriptRenderer = {

    renderTranscriptWorkspace,

    renderTranscriptContent
};