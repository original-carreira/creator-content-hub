async function fetchJobs({
                             status = "",
                             sort = "desc",
                             limit = 20
                         } = {}) {

    let url = `/jobs?limit=${limit}`;

    if (status) {
        url += `&status=${status}`;
    }

    url += `&sort=createdAt`;

    if (sort) {
        url += `&order=${sort}`;
    }

    const res = await fetch(url);

    if (!res.ok) {
        throw new Error("Erro ao carregar jobs");
    }

    return await res.json();
}


async function fetchJobDetails(jobId) {

    const res = await fetch(`/jobs/${jobId}`);

    const data = await res.json();

    if (!res.ok || !data.success) {
        throw new Error("Erro ao carregar detalhes");
    }

    return data;
}

async function searchJobs(query) {

    const res = await fetch(
        `/jobs/search?q=${encodeURIComponent(query)}`
    );

    if (!res.ok) {
        throw new Error("Erro na requisição");
    }

    return await res.json();
}

async function deleteJobs(jobIds = []) {

    const res = await fetch("/jobs/delete", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            jobIds
        })
    });

    if (!res.ok) {
        throw new Error("Erro ao deletar jobs");
    }

    return await res.json();
}

async function ingestYoutube(url) {

    const res = await fetch("/ingest/youtube", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            url
        })
    });

    if (!res.ok) {
        throw new Error("Erro ao iniciar ingestão");
    }

    return await res.json();
}

window.JobsApi = {
    fetchJobs,
    fetchJobDetails,
    searchJobs,
    deleteJobs,
    ingestYoutube
};