function renderVideoWorkspace(job) {

    if (!job?.videoAvailable) {
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
                            '/assets/video/download'
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