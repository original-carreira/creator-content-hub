function bindTranscriptSearch(
    workspaceStateApi
) {

    const input =
        document.getElementById(
            "transcriptSearchInput"
        );

    bindSegmentNavigation();

    if (!input) {
        return;
    }

    const previousButton =
        document.getElementById(
            "previousOccurrenceButton"
        );

    const nextButton =
        document.getElementById(
            "nextOccurrenceButton"
        );

    if (previousButton) {

        previousButton.addEventListener(
            "click",
            () => {

                goToPreviousOccurrence(
                    workspaceStateApi
                );

                updateSearchOccurrenceCounter(
                    workspaceStateApi
                );
            }
        );
    }

    if (nextButton) {

        nextButton.addEventListener(
            "click",
            () => {

                goToNextOccurrence(
                    workspaceStateApi
                );

                updateSearchOccurrenceCounter(
                    workspaceStateApi
                );
            }
        );
    }

    input.addEventListener(
        "input",
        (event) => {

            const value =
                event.target.value.trim();

            workspaceStateApi
                .setTranscriptSearchTerm(
                    value
                );

            window.TranscriptRenderer
                .renderTranscriptSegments(

                    workspaceStateApi
                        .workspaceState
                        .transcript?.segments || [],

                    workspaceStateApi
                        .workspaceState
                        .transcriptSearch
                        .searchTerm,

                    workspaceStateApi
                        .workspaceState
                        .transcriptSearch
                        .activeOccurrenceIndex
                );

            const occurrences =
                getTranscriptOccurrences();

            workspaceStateApi
                .workspaceState
                .transcriptSearch
                .totalOccurrences =
                occurrences.length;

            if (occurrences.length > 0) {

                workspaceStateApi
                    .workspaceState
                    .transcriptSearch
                    .activeOccurrenceIndex =
                    0;

                window.TranscriptRenderer
                    .renderTranscriptSegments(

                        workspaceStateApi
                            .workspaceState
                            .transcript?.segments || [],

                        workspaceStateApi
                            .workspaceState
                            .transcriptSearch
                            .searchTerm,

                        workspaceStateApi
                            .workspaceState
                            .transcriptSearch
                            .activeOccurrenceIndex
                    );

                scrollToOccurrence(0);

                updateSearchOccurrenceCounter(
                    workspaceStateApi
                );

            } else {

                workspaceStateApi
                    .workspaceState
                    .transcriptSearch
                    .activeOccurrenceIndex = -1;

                updateSearchOccurrenceCounter(
                    workspaceStateApi
                );
            }
        }
    );
}

function getTranscriptOccurrences() {

    const transcriptionEl =
        document.getElementById(
            "transcription"
        );

    if (!transcriptionEl) {
        return [];
    }

    return Array.from(
        transcriptionEl.querySelectorAll(
            "mark"
        )
    );
}

function scrollToOccurrence(index) {

    const occurrences =
        getTranscriptOccurrences();

    if (
        index < 0 ||
        index >= occurrences.length
    ) {
        return;
    }

    const occurrence =
        occurrences[index];

    occurrence.scrollIntoView({
        behavior: "smooth",
        block: "center"
    });
}

function goToNextOccurrence(workspaceStateApi) {
    const occurrences =
        getTranscriptOccurrences();

    if (occurrences.length === 0) {
        return;
    }

    const currentIndex =
        workspaceStateApi
            .workspaceState
            .transcriptSearch
            .activeOccurrenceIndex;

    const nextIndex =
        currentIndex >= occurrences.length - 1
            ? 0
            : currentIndex + 1;

    workspaceStateApi
        .workspaceState
        .transcriptSearch
        .activeOccurrenceIndex =
        nextIndex;

    window.TranscriptRenderer
        .renderTranscriptSegments(

            workspaceStateApi
                .workspaceState
                .transcript?.segments || [],

            workspaceStateApi
                .workspaceState
                .transcriptSearch
                .searchTerm,

            workspaceStateApi
                .workspaceState
                .transcriptSearch
                .activeOccurrenceIndex
        );

    scrollToOccurrence(nextIndex);
}

function goToPreviousOccurrence(workspaceStateApi) {
    const occurrences =
        getTranscriptOccurrences();

    if (occurrences.length === 0) {
        return;
    }

    const currentIndex =
        workspaceStateApi
            .workspaceState
            .transcriptSearch
            .activeOccurrenceIndex;

    const previousIndex =
        currentIndex <= 0
            ? occurrences.length - 1
            : currentIndex - 1;

    workspaceStateApi
        .workspaceState
        .transcriptSearch
        .activeOccurrenceIndex =
        previousIndex;

    window.TranscriptRenderer
        .renderTranscriptSegments(

            workspaceStateApi
                .workspaceState
                .transcript?.segments || [],

            workspaceStateApi
                .workspaceState
                .transcriptSearch
                .searchTerm,

            workspaceStateApi
                .workspaceState
                .transcriptSearch
                .activeOccurrenceIndex
        );

    scrollToOccurrence(previousIndex);
}

function updateSearchOccurrenceCounter(workspaceStateApi){

    const counterEl =
        document.getElementById(
            "searchOccurrenceCounter"
        );

    if (!counterEl) {
        return;
    }

    const total =
        workspaceStateApi
            .workspaceState
            .transcriptSearch
            .totalOccurrences;

    const activeIndex =
        workspaceStateApi
            .workspaceState
            .transcriptSearch
            .activeOccurrenceIndex;

    if (total === 0) {

        counterEl.innerText =
            "0 de 0";

        return;
    }

    counterEl.innerText =
        `${activeIndex + 1} de ${total}`;
}

function getTranscriptSegments() {

    const transcriptionEl =
        document.getElementById(
            "transcription"
        );

    if (!transcriptionEl) {
        return [];
    }

    return Array.from(
        transcriptionEl.querySelectorAll(
            "[data-segment-index]"
        )
    );
}

function clearActiveSegment() {

    getTranscriptSegments()
        .forEach(
            (segment) => {

                segment.style.backgroundColor =
                    "";

                segment.style.padding =
                    "";

                segment.style.borderRadius =
                    "";
            }
        );
}

function activateSegment(index) {

    const segment =
        document.querySelector(
            `[data-segment-index="${index}"]`
        );

    if (!segment) {
        return;
    }

    clearActiveSegment();

    segment.style.backgroundColor =
        "#eef6ff";

    segment.style.padding =
        "6px";

    segment.style.borderRadius =
        "8px";

    segment.scrollIntoView({
        behavior: "smooth",
        block: "center"
    });
}

function bindSegmentNavigation() {

    const transcriptionEl =
        document.getElementById(
            "transcription"
        );

    if (!transcriptionEl) {
        return;
    }

    transcriptionEl.addEventListener(
        "click",
        (event) => {

            const segment =
                event.target.closest(
                    "[data-segment-index]"
                );

            if (!segment) {
                return;
            }

            activateSegment(
                Number(
                    segment.dataset.segmentIndex
                )
            );
        }
    );
}

window.TranscriptSearchController = {

    bindTranscriptSearch
};