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