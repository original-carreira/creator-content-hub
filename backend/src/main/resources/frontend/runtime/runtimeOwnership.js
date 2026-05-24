function isActiveRuntimeJob(jobId, activeJobId){
    return jobId === activeJobId;
}

function isActiveDetailsJob(jobId, activeDetailsJobId){
    return jobId === activeDetailsJobId;
}

window.RuntimeOwnership = {
    isActiveRuntimeJob,
    isActiveDetailsJob
};