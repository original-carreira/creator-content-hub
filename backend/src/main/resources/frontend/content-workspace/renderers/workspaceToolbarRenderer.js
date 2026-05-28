function renderWorkspaceToolbar() {

    return `
        <div
            style="
                display:flex;
                justify-content:space-between;
                align-items:center;
                gap:12px;
                margin-bottom:18px;
                padding:14px;
                border:1px solid #ddd;
                border-radius:10px;
                background:#fafafa;
                flex-wrap:wrap;
            "
        >

            <div
                style="
                    display:flex;
                    align-items:center;
                    gap:8px;
                    flex:1;
                    min-width:260px;
                "
            >
                <input
                    id="transcriptSearchInput"
                    type="text"
                    placeholder="Buscar na transcrição..."
                    style="
                        width:100%;
                        padding:10px;
                        border:1px solid #ccc;
                        border-radius:8px;
                    "
                />
            </div>
            
            <div
                style="
                    display:flex;
                    align-items:center;
                    gap:6px;
                "
            >

                <button
                    id="previousOccurrenceButton"
                    type="button"
                    style="
                        padding:8px 10px;
                        border:1px solid #ccc;
                        border-radius:8px;
                        cursor:pointer;
                        background:white;
                    "
                >
                    ↑
                </button>

                <button
                    id="nextOccurrenceButton"
                    type="button"
                    style="
                        padding:8px 10px;
                        border:1px solid #ccc;
                        border-radius:8px;
                        cursor:pointer;
                        background:white;
                    "
                >
                    ↓
                </button>

                <span
                    id="searchOccurrenceCounter"
                    style="
                        font-size:13px;
                        color:#666;
                        min-width:70px;
                    "
                >
                    0 de 0
                </span>

            </div>

            <div
                style="
                    display:flex;
                    align-items:center;
                    gap:10px;
                    flex-wrap:wrap;
                "
            >
                <button
                    type="button"
                    style="
                        padding:10px 14px;
                        border:none;
                        border-radius:8px;
                        cursor:pointer;
                    "
                >
                    Export TXT
                </button>

                <button
                    type="button"
                    style="
                        padding:10px 14px;
                        border:none;
                        border-radius:8px;
                        cursor:pointer;
                    "
                >
                    Export DOCX
                </button>

                <button
                    type="button"
                    style="
                        padding:10px 14px;
                        border:none;
                        border-radius:8px;
                        cursor:pointer;
                    "
                >
                    Media
                </button>
            </div>
        </div>
    `;
}

window.WorkspaceToolbarRenderer = {

    renderWorkspaceToolbar
};