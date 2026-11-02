function renderVideoWorkspace(
    assets
) {

    const videoAsset =
        assets?.find(
            asset =>
                asset.assetType === "VIDEO"
        );

    if (!videoAsset) {
        return "";
    }

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
                <strong>Video Workspace</strong>

                <span
                    style="
                        font-size:12px;
                        color:#666;
                    "
                >
                    Primary Asset
                </span>
            </div>

            <div
                style="
                    margin-bottom:16px;
                "
            >
                <video
                    controls
                    preload="metadata"
                    style="
                        width:100%;
                        max-height:600px;
                        background:#000;
                        border-radius:8px;
                    "
                >
                    <source
                        src="/jobs/${
        new URLSearchParams(
            window.location.search
        ).get("jobId")
    }/assets/${videoAsset.assetId}/stream"
                    />

                    Seu navegador não suporta vídeo HTML5.
                </video>
            </div>
            
            ${window.TimelineWorkspace.renderTimeline({
                videoAsset,
                videoElement: null
            })}
            
            <div
                style="
                    margin-bottom:16px;
                    border:1px solid #ddd;
                    border-radius:10px;
                    background:#fafafa;
                    padding:16px;
                "
            >

                <div
                    style="
                        font-size:12px;
                        color:#666;
                        margin-bottom:8px;
                    "
                >
                    Video Metadata
                </div>

                <div
                    id="videoCurrentTimeDisplay"
                    style="
                        font-size:14px;
                        margin-bottom:8px;
                    "
                >
                    Tempo Atual: 00:00:00
                </div>

                <div
                    id="videoDurationDisplay"
                    style="
                        font-size:14px;
                    "
                >
                    Duração: carregando...
                </div>

                <div
                    style="
                        margin-top:12px;
                        display:flex;
                        gap:8px;
                        align-items:center;
                        flex-wrap:wrap;
                    "
                >

                    <input
                        id="videoSeekInput"
                        type="text"
                        placeholder="30, 1:30 ou 00:01:30"
                        style="
                            padding:8px;
                            border:1px solid #ccc;
                            border-radius:8px;
                            width:180px;
                        "
                    />

                    <button
                        type="button"
                        id="videoSeekButton"
                        style="
                            min-width: 120px;
                            padding: 8px 12px;
                            border-radius:8px;
                        "
                    >
                        Ir para Tempo
                    </button>

                </div>

                <div
                    id="videoSeekFeedback"
                    style="
                        margin-top:8px;
                        font-size:12px;
                        color:#666;
                        min-height:18px;
                    "
                ></div>

            </div>
            
            <div
                style="
                    margin-top:16px;
                    margin-bottom:16px;
                    border:1px solid #ddd;
                    border-radius:10px;
                    background:#fafafa;
                    padding:16px;
                "
            >
            
                <div
                    style="
                        font-size:12px;
                        color:#666;
                        margin-bottom:12px;
                    "
                >
                    Seleção de Cortes
                </div>
            
                <div
                    style="
                        display:flex;
                        gap:8px;
                        align-items:center;
                        flex-wrap:wrap;
                    "
                >
            
                    <button
                        type="button"
                        id="clipMarkInButton"
                        style="
                            min-width:120px;
                            padding:8px 12px;
                            border-radius:8px;
                        "
                    >
                        Marcar Início
                    </button>
            
                    <button
                        type="button"
                        id="clipMarkOutButton"
                        style="
                            min-width:120px;
                            padding:8px 12px;
                            border-radius:8px;
                        "
                    >
                        Marcar Fim
                    </button>
            
                    <button
                        type="button"
                        id="clipClearSelectionButton"
                        style="
                            min-width:120px;
                            padding:8px 12px;
                            border-radius:8px;
                        "
                    >
                        Limpar Seleção
                    </button>

                    <button
                        type="button"
                        id="clipSaveSelectionButton"
                        style="
                            min-width:120px;
                            padding:8px 12px;
                            border-radius:8px;
                        "
                    >
                        Salvar Seleção
                    </button>

                </div>
            
                <div
                    style="
                        margin-top:16px;
                        padding-top:12px;
                        border-top:1px solid #ddd;
                    "
                >
            
                    <div
                        id="clipSelectionDisplay"
                        style="
                            font-size:14px;
                        "
                    >
                        ☐ Seleção Atual | Início: -- | Fim: -- | Duração: --
                    </div>

                <section
                    aria-labelledby="materializedSelectionsHeading"
                    style="
                        margin-top:16px;
                    "
                >
                    <div
                        style="
                            display:flex;
                            justify-content:space-between;
                            align-items:center;
                            gap:12px;
                        "
                    >
                        <strong
                            id="materializedSelectionsHeading"
                        >
                            Selections materializadas
                        </strong>

                        <button
                            type="button"
                            id="clearAllMaterializedSelectionsButton"
                            disabled
                        >
                            Limpar todas
                        </button>
                    </div>

                    <div
                        role="table"
                        aria-label="Selections materializadas"
                        style="
                            margin-top:8px;
                        "
                    >
                        <div
                            role="row"
                            style="
                                display:grid;
                                grid-template-columns:1.4fr 1fr 1fr 1fr auto;
                                gap:8px;
                                padding:6px 0;
                                border-bottom:1px solid #ddd;
                                font-size:12px;
                                font-weight:600;
                            "
                        >
                            <span role="columnheader">ID</span>
                            <span role="columnheader">Início</span>
                            <span role="columnheader">Fim</span>
                            <span role="columnheader">Duração</span>
                            <span role="columnheader">Ação</span>
                        </div>

                        <div
                            id="materializedSelectionsList"
                            role="rowgroup"
                            aria-live="polite"
                        >
                            <div
                                role="row"
                                style="
                                    padding:8px 0;
                                    color:#666;
                                    font-size:13px;
                                "
                            >
                                <span role="cell">Nenhuma Selection materializada.</span>
                            </div>
                        </div>
                    </div>

                    <div
                        id="materializedSelectionExportFeedback"
                        role="status"
                        aria-live="polite"
                        style="
                            margin-top:8px;
                            font-size:13px;
                        "
                    ></div>
                </section>
            
                </div>
            
            </div>

            <div
                style="
                    border:1px solid #ddd;
                    border-radius:10px;
                    background:#fafafa;
                    padding:16px;
                "
            >
                <div
                    style="
                        display:flex;
                        gap:8px;
                        flex-wrap:wrap;
                    "
                >
                    <button
                        type="button"
                        id="videoDownloadButton"
                         style="
                            min-width:120px;
                            padding:8px 12px;
                            border-radius:8px;
                        "
                        onclick="
                            window.location.href=
                            '/jobs/' +
                            new URLSearchParams(window.location.search).get('jobId') +
                            '/assets/' +
                            '${videoAsset.assetId}' +
                            '/download'
                        "
                    >
                        Download Video
                    </button>

                </div>

            </div>

        </div>
    `;
}

window.VideoWorkspaceRenderer = {

    renderVideoWorkspace
};
