/*
 * TimelineView
 *
 * RESPONSIBILITIES:
 * - timeline incremental visual updates
 * - timeline DOM coordination
 *
 * IMPORTANT:
 * - must NOT perform initial rendering
 * - must NOT access HTML5 Video
 * - must NOT access backend
 * - must NOT mutate WorkspaceState
 *
 * DOM CONTRACT:
 * - timelineCurrentPositionLayer
 * - timelineSelectionLayer
 * - timelineMarkerLayer
 * - timelineOverlayLayer
 *
 * TimelineView is responsible only for
 * incremental updates after TimelineRenderer
 * has completed the initial rendering.
 *
 * VISUAL UPDATE PIPELINE
 *
 * TimelineState
 *        ↓
 * updateTimeline()
 *        ↓
 * Timeline Layers
 *
 * Future layer updates:
 * - Current Position
 * - Selection
 * - Markers
 * - Overlays
 */

function createTimelineView() {

    const selectionElementsById =
        new Map();

    function formatTime(seconds) {

        const totalSeconds =
            Math.floor(seconds ?? 0);

        const hours =
            String(
                Math.floor(totalSeconds / 3600)
            ).padStart(2, "0");

        const minutes =
            String(
                Math.floor((totalSeconds % 3600) / 60)
            ).padStart(2, "0");

        const secs =
            String(
                totalSeconds % 60
            ).padStart(2, "0");

        return `${hours}:${minutes}:${secs}`;
    }

    function updateCurrentPositionLayer({
                                            progress,
                                            currentTime
                                        }) {

        const currentPositionIndicator =
            document.getElementById(
                "timelineCurrentPositionIndicator"
            );

        const currentPositionLabel =
            document.getElementById(
                "timelineCurrentPositionLabel"
            );

        if (
            !currentPositionIndicator ||

            !currentPositionLabel
        ) {
            return;

        }

        currentPositionIndicator.style.left =
            `${progress * 100}%`;

        currentPositionLabel.style.left =
            `${progress * 100}%`;

        currentPositionLabel.textContent =
            formatTime(currentTime);

    }

    function updateSelectionLayer({
                                      timelineState
                                  }) {

        /*
         * FOUNDATION
         *
         * Responsável pela atualização incremental
         * da Selection Layer.
         */

        const selectionLayer =
            document.getElementById(
                "timelineSelectionLayer"
            );

        if (!selectionLayer) {
            return;
        }

        const duration =
            timelineState.duration;

        if (

            duration === null ||

            duration <= 0

        ) {
            return;
        }

        const timelineBounds =
            selectionLayer.getBoundingClientRect();

        const timelineWidth =
            timelineBounds.width;

        const currentSelectionIds =
            new Set();

        (timelineState.selections || [])
            .forEach(
                (selection, index) => {

                    if (!selection) {
                        return;
                    }

                    const selectionId =
                        selection.id ??
                        `current-${index}`;

                    const selectionKey =
                        String(selectionId);

                    const hasMaterializedId =
                        selection.id !== undefined &&
                        selection.id !== null;

                    currentSelectionIds.add(
                        selectionKey
                    );

                    let selectionElements =
                        selectionElementsById.get(
                            selectionKey
                        );

                    if (!selectionElements) {

                        const selectionRegion =
                            document.createElement(
                                "div"
                            );

                        selectionRegion.classList.add(
                            "timeline-selection-region"
                        );

                        const startHandle =
                            document.createElement(
                                "div"
                            );

                        startHandle.classList.add(
                            "timeline-selection-handle"
                        );

                        startHandle.classList.add(
                            "timeline-selection-handle-start"
                        );

                        startHandle.dataset.handle =
                            "start";

                        const endHandle =
                            document.createElement(
                                "div"
                            );

                        endHandle.classList.add(
                            "timeline-selection-handle"
                        );

                        endHandle.classList.add(
                            "timeline-selection-handle-end"
                        );

                        endHandle.dataset.handle =
                            "end";

                        selectionLayer.appendChild(
                            selectionRegion
                        );

                        selectionLayer.appendChild(
                            startHandle
                        );

                        selectionLayer.appendChild(
                            endHandle
                        );

                        selectionElements = {
                            selectionRegion,
                            startHandle,
                            endHandle
                        };

                        selectionElementsById.set(
                            selectionKey,
                            selectionElements
                        );

                    }

                    const legacyElementIds =
                        index === 0;

                    selectionElements
                        .selectionRegion.id =
                        legacyElementIds
                            ? "timelineSelectionRegion"
                            : `timelineSelectionRegion-${selectionKey}`;

                    selectionElements
                        .startHandle.id =
                        legacyElementIds
                            ? "timelineSelectionStartHandle"
                            : `timelineSelectionStartHandle-${selectionKey}`;

                    selectionElements
                        .endHandle.id =
                        legacyElementIds
                            ? "timelineSelectionEndHandle"
                            : `timelineSelectionEndHandle-${selectionKey}`;

                    selectionElements
                        .selectionRegion.dataset.selectionId =
                        hasMaterializedId
                            ? selectionKey
                            : "";

                    selectionElements
                        .selectionRegion.dataset.selectionType =
                        hasMaterializedId
                            ? "materialized"
                            : "current";

                    selectionElements
                        .startHandle.dataset.selectionId =
                        hasMaterializedId
                            ? selectionKey
                            : "";

                    selectionElements
                        .startHandle.dataset.selectionType =
                        hasMaterializedId
                            ? "materialized"
                            : "current";

                    selectionElements
                        .endHandle.dataset.selectionId =
                        hasMaterializedId
                            ? selectionKey
                            : "";

                    selectionElements
                        .endHandle.dataset.selectionType =
                        hasMaterializedId
                            ? "materialized"
                            : "current";

                    const hue =
                        (210 + index * 67) % 360;

                    selectionElements
                        .selectionRegion.style.backgroundColor =
                        `hsla(${hue}, 75%, 55%, 0.28)`;

                    selectionElements
                        .startHandle.style.backgroundColor =
                        `hsl(${hue}, 75%, 45%)`;

                    selectionElements
                        .endHandle.style.backgroundColor =
                        `hsl(${hue}, 75%, 45%)`;

                    const hasCompleteSelection =
                        selection.startTime !== null &&
                        selection.endTime !== null;

                    if (!hasCompleteSelection) {

                        selectionElements
                            .selectionRegion.style.display =
                            "none";

                        selectionElements
                            .startHandle.style.display =
                            "none";

                        selectionElements
                            .endHandle.style.display =
                            "none";

                        return;

                    }

                    const startHandleProgress =
                        Math.min(
                            1,
                            Math.max(
                                0,
                                selection.startTime /
                                duration
                            )
                        );

                    const endHandleProgress =
                        Math.min(
                            1,
                            Math.max(
                                0,
                                selection.endTime /
                                duration
                            )
                        );

                    const startHandlePosition =
                        startHandleProgress *
                        timelineWidth;

                    const endHandlePosition =
                        endHandleProgress *
                        timelineWidth;

                    selectionElements
                        .selectionRegion.style.display =
                        "";

                    selectionElements
                        .selectionRegion.style.left =
                        `${startHandlePosition}px`;

                    selectionElements
                        .selectionRegion.style.width =
                        `${endHandlePosition - startHandlePosition}px`;

                    selectionElements
                        .startHandle.style.display =
                        "";

                    selectionElements
                        .endHandle.style.display =
                        "";

                    selectionElements
                        .startHandle.style.left =
                        `${startHandlePosition}px`;

                    selectionElements
                        .endHandle.style.left =
                        `${endHandlePosition}px`;

                }
            );

        selectionElementsById
            .forEach(
                (selectionElements, selectionKey) => {

                    if (
                        currentSelectionIds.has(
                            selectionKey
                        )
                    ) {
                        return;
                    }

                    selectionElements.selectionRegion.remove();
                    selectionElements.startHandle.remove();
                    selectionElements.endHandle.remove();

                    selectionElementsById.delete(
                        selectionKey
                    );

                }
            );

    }

    function updateDuration({
                                timelineState
                            }) {

        const timelineEndTime =
            document.getElementById(
                "timelineEndTime"
            );

        if (!timelineEndTime) {
            return;
        }

        timelineEndTime.textContent =
            formatTime(
                timelineState.duration
            );
    }

    function updateHoverTime({
                                 hoverTime
                             }) {

        const timelineHoverTime =
            document.getElementById(
                "timelineHoverTime"
            );

        if (!timelineHoverTime) {
            return;
        }

        timelineHoverTime.textContent =
            formatTime(
                hoverTime
            );
    }

    function updateTimeline({
                                timelineState
                            }) {

        if (

            timelineState.duration === null ||

            timelineState.duration <= 0

        ) {

            return;
        }

        const progress =

            (timelineState.currentTime ?? 0) /

            timelineState.duration;

        updateCurrentPositionLayer({

            progress,
            currentTime: timelineState.currentTime

        });

        updateSelectionLayer({

            timelineState

        });
    }

    return {

        updateTimeline,
        updateDuration,
        updateHoverTime

    };
}

window.TimelineView = {

    createTimelineView

};
