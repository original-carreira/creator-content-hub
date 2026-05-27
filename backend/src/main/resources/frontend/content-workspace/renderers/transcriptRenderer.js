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
                        white-space:pre-wrap;
                        word-break:break-word;
                        line-height:1.6;
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
    searchTerm = ""
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

    const highlighted =
        escapedText.replace(
            regex,
            `
            <mark
                style="
                    background:#ffeb3b;
                    color:inherit;
                "
            >
                $1
            </mark>
            `
        );

    transcriptionEl.innerHTML =
        highlighted;

    const firstMatch =
        transcriptionEl.querySelector(
            "mark"
        );

    if (firstMatch) {

        firstMatch.scrollIntoView({
            behavior:"smooth",
            block:"center"
        });
    }
}

window.TranscriptRenderer = {

    renderTranscriptWorkspace,

    renderTranscriptContent
};