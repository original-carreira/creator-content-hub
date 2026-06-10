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
                id="selectedSegmentContext"
                style="
                    border:1px solid #ddd;
                    border-radius:10px;
                    background:#fafafa;
                    padding:16px;
                    margin-bottom:12px;
                "
            >
                <div
                    style="
                        display:flex;
                        justify-content:space-between;
                        align-items:center;
                        margin-bottom:10px;
                    "
                >
                    <strong>Segmento Selecionado</strong>

                    <span
                        style="
                            font-size:12px;
                            color:#666;
                        "
                    >
                        Contexto do Segmento
                    </span>
                </div>

                <div
                    id="selectedSegmentContent"
                    style="
                        color:#666;
                        font-size:14px;
                    "
                >
                    Nenhum segmento selecionado
                </div>
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

function formatTimestamp(seconds) {

    const totalSeconds =
        Math.floor(
            Number(seconds) || 0
        );

    const minutes =
        Math.floor(
            totalSeconds / 60
        );

    const remainingSeconds =
        totalSeconds % 60;

    return `${String(minutes).padStart(2, "0")}:${String(
        remainingSeconds
    ).padStart(2, "0")}`;
}

function renderSelectedSegmentContext(
    segment
) {

    const container =
        document.getElementById(
            "selectedSegmentContent"
        );

    if (!container) {
        return;
    }

    if (!segment) {

        container.innerHTML =
            "Nenhum segmento selecionado";

        return;
    }

    const start =
        Number(segment.start) || 0;

    const end =
        Number(segment.end) || 0;

    const duration =
        Math.max(
            0,
            end - start
        );

    container.innerHTML = `
        <div
            style="
                display:flex;
                gap:20px;
                margin-bottom:10px;
                font-size:13px;
            "
        >
            <div>
                <strong>Início:</strong>
                ${formatTimestamp(start)}
            </div>

            <div>
                <strong>Fim:</strong>
                ${formatTimestamp(end)}
            </div>

            <div>
                <strong>Duração:</strong>
                ${formatTimestamp(duration)}
            </div>
        </div>

        <div
            style="
                line-height:1.6;
                white-space:pre-wrap;
                word-break:break-word;
            "
        >
            ${escapeHtml(segment.text || "")}
        </div>
    `;
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

function renderTranscriptSegments(
    segments,
    searchTerm = "",
    activeOccurrenceIndex = -1
) {

    let occurrenceIndex = 0;

    const escapedSearch =
        escapeHtml(searchTerm);

    const transcriptionEl =
        document.getElementById(
            "transcription"
        );

    if (!transcriptionEl) {
        return;
    }

    if (
        !segments ||
        segments.length === 0
    ) {
        transcriptionEl.innerText =
            "(vazio)";

        return;
    }

    transcriptionEl.innerHTML =
        segments
            .map(
                (segment, index) => `
                    <div
                        style="
                            display:flex;
                            align-items:flex-start;
                            gap:10px;
                            margin-bottom:8px;
                            border-radius:8px;
                            transition:background-color 0.2s ease;
                        "
                        data-segment-index="${index}"
                    >

                        <div
                            style="
                                min-width:45px;
                                color:#666;
                                font-size:12px;
                                font-weight:bold;
                                flex-shrink:0;
                            "
                        >
                            ${formatTimestamp(segment.start)}
                        </div>

                        <div
                            style="
                                flex:1;
                                line-height:1.5;
                            "
                        >
                            ${(() => {

                                const escapedSegmentText =
                                    escapeHtml(
                                    segment.text || ""
                                    );

                                if (!searchTerm) {
                                    return escapedSegmentText;
                                }

                                const regex =
                                    new RegExp(
                                        `(${escapedSearch})`,
                                        "gi"
                                    );

                                return escapedSegmentText.replace(
                                    regex,
                                    (match) => {

                                        const currentIndex =
                                            occurrenceIndex++;
                                        
                                        const isActive =
                                            currentIndex ===
                                            activeOccurrenceIndex;

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

                            })()}
                        </div>

                    </div>
                `
            )
            .join("");
}

window.TranscriptRenderer = {

    renderTranscriptWorkspace,

    renderTranscriptContent,

    renderTranscriptSegments,

    renderSelectedSegmentContext
};