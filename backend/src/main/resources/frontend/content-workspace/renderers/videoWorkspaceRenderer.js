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