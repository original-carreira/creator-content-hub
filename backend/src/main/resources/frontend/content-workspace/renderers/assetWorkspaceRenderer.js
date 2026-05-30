function renderAssetWorkspace(assetCollection) {

    const assets =
        assetCollection?.assets || [];

    const assetItems = assets
        .map(asset => {

            const actionItems =
                (asset.actions || [])
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
                                ${action.label}
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

                    <div>
                        ${asset.type}
                    </div>

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
                <strong>Assets Disponíveis</strong>

                <span
                    style="
                        font-size:12px;
                        color:#666;
                    "
                >
                    ${assets.length} asset(s)
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
                                Nenhum asset disponível.
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

window.AssetWorkspaceRenderer = {

    renderAssetWorkspace
};