function renderAudioWorkspace(assetCollection) {

    const assets =
        (assetCollection?.assets || [])
            .filter(asset =>
                asset.assetId === "mp3"
            );

    const assetItems = assets
        .map(asset => {

            const actionItems =
                (asset.actions || [])
                    .filter(action =>
                        action.actionId === "download"
                    )
                    .map(action => {
                        return `
                <button
                    type="button"
                    data-asset-id="${asset.assetId}"
                    data-action-id="${action.actionId}"
                    style="
                        padding:4px 10px;
                        border:1px solid #ddd;
                        border-radius:6px;
                        background:#fff;
                        cursor:pointer;
                        font-size:12px;
                    "
                >
                    Download MP3
                </button>
            `;
                    })
                    .join("");

            return `
                <li
                    style="
                        padding:8px 0;
                        border-bottom:1px solid #eee;
                    "
                >
                    <div
                        style="
                            display:flex;
                            gap:8px;
                            margin-top:6px;
                            flex-wrap:wrap;
                        "
                    >
                        ${actionItems}
                    </div>

                </li>
            `;
        })
        .join("");

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
                <strong>Audio Workspace</strong>

                <span
                    style="
                        font-size:12px;
                        color:#666;
                    "
                >
                    Derived Asset
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

                ${
        assets.length === 0
            ? `
                            <div
                                style="
                                    color:#666;
                                    font-size:14px;
                                "
                            >
                                Nenhum áudio disponível.
                            </div>
                        `
            : `
                            <ul
                                style="
                                    list-style:none;
                                    padding:0;
                                    margin:0;
                                "
                            >
                                ${assetItems}
                            </ul>
                        `
    }

            </div>

        </div>
    `;
}

window.AudioWorkspaceRenderer = {

    renderAudioWorkspace
};