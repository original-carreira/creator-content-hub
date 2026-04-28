async function search() {
    const statusEl = document.getElementById("status");
    const resultsEl = document.getElementById("results");
    const detailsEl = document.getElementById("details");

    const q = document.getElementById("searchInput").value;

    // reset UI
    resultsEl.innerHTML = "";
    detailsEl.innerHTML = "";
    statusEl.innerText = "Buscando...";

    try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);

        const res = await fetch(`/jobs/search?q=${encodeURIComponent(q)}`, {
            signal: controller.signal
        });

        clearTimeout(timeout);

        if (!res.ok) {
            throw new Error("Erro na requisição");
        }

        const data = await res.json();

        if (!data?.data?.items || data.data.items.length === 0) {
            statusEl.innerText = "Nenhum resultado encontrado";
            return;
        }

        statusEl.innerText = "";

        renderResults(data.data.items);

    } catch (err) {
        console.error("Erro na busca:", err);
        statusEl.innerText = "Erro ao buscar dados";
    }
}

function renderResults(items) {
    const resultsEl = document.getElementById("results");

    resultsEl.innerHTML = "";

    items.forEach(item => {
        const div = document.createElement("div");
        div.className = "result-item";

        div.onclick = () => loadDetails(item.jobId);

        div.innerHTML = `
      <div class="result-snippet">
        ${item.snippet ? item.snippet : "(sem snippet)"}
      </div>
      <div class="result-meta">
        <span>Status: ${item.status}</span>
        <span>${new Date(item.createdAt).toLocaleString()}</span>
      </div>
    `;

        resultsEl.appendChild(div);
    });
}

async function loadDetails(jobId) {
    const detailsEl = document.getElementById("details");
    const statusEl = document.getElementById("status");

    detailsEl.innerHTML = "Carregando detalhes...";
    statusEl.innerText = "";

    try {
        const res = await fetch(`/jobs/${jobId}`);
        const data = await res.json();

        if (!res.ok || !data.success) {
            throw new Error("Erro ao carregar detalhes");
        }

        const job = data.data;

        renderDetails(job);

    } catch (err) {
        console.error("Erro ao carregar detalhes:", err);
        detailsEl.innerHTML = "Erro ao carregar detalhes";
    }
}

function renderDetails(job) {
    const detailsEl = document.getElementById("details");

    detailsEl.innerHTML = `
    <h2>Detalhes do Job</h2>

    <div class="detail-block">
      <strong>Status:</strong> ${job.status}
    </div>

    <div class="detail-block">
      <strong>Criado em:</strong> ${new Date(job.createdAt).toLocaleString()}
    </div>

    <div class="detail-block">
      <strong>Finalizado em:</strong> ${
        job.finishedAt ? new Date(job.finishedAt).toLocaleString() : "—"
    }
    </div>

    <div class="detail-block">
      <strong>Transcription:</strong>
      <pre>${job.transcription || "(vazio)"}</pre>
    </div>

    <div class="detail-block">
      <strong>Summary:</strong>
      <pre>${job.summary || "(vazio)"}</pre>
    </div>
  `;
}