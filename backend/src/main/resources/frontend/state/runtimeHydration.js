function hydrateRuntimeProcessingStage(runtimeState, job){
    if (
        job.transcription &&
        !job.summary
    ) {

        runtimeState.stage =
            "SUMMARIZING";

    } else if (
        !job.transcription
    ) {

        runtimeState.stage =
            "TRANSCRIBING";
    }
}

window.RuntimeHydration = {
    hydrateRuntimeProcessingStage
};