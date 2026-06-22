function renderVideoWorkspace(
    assets,
    mediaNavigationContext
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
                
                <div
                    style="
                        margin-bottom:12px;
                        padding:12px;
                        border:1px solid #ddd;
                        border-radius:8px;
                        background:#fafafa;
                    "
                >
                    <div
                        style="
                            font-weight:bold;
                            margin-bottom:6px;
                        "
                    >
                        Seleções Salvas
                    </div>

                    ${
                        mediaNavigationContext
                            ? `
                                <div>
                                    Seleção:
                                    ${mediaNavigationContext.name}
                                </div>

                                <div>
                                    ${mediaNavigationContext.ranges.length}
                                    cortes registrados
                                </div>
                            `
                            : `
                                <div>
                                    Nenhuma seleção criada para este vídeo.
                                </div>
                            `
                    }
                </div>

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