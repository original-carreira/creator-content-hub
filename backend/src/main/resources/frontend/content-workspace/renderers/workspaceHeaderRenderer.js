function renderJobHeader(job) {

    return `
        <div
            style="
                display:flex;
                gap:20px;
                align-items:flex-start;
                margin-bottom:15px;
            "
        >

            <!-- THUMBNAIL -->
            <div>
                <img
                    src="${job.thumbnailUrl || ''}"
                    alt="thumb"
                    style="
                        width:240px;
                        height:135px;
                        object-fit:cover;
                        border-radius:8px;
                        background:#eee;
                    "
                    onerror="this.style.display='none'"
                />
            </div>

            <!-- INFO -->
            <div>

                <div class="detail-block">
                    <strong>Status:</strong> ${job.status}
                </div>

                <div class="detail-block">
                    <strong>Título:</strong>
                    ${job.title || "(sem título)"}
                </div>

            </div>
        </div>
    `;
}

window.WorkspaceHeaderRenderer = {

    renderJobHeader
};