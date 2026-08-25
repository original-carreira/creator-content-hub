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

        /*
         * ============================================================
         * SELECTION_REGION
         * ============================================================
         */

        const selectionRegionId =
            "timelineSelectionRegion";

        let selectionRegion =
            document.getElementById(
                selectionRegionId
            );

        if (!selectionRegion) {

            selectionRegion =
                document.createElement(
                    "div"
                );

            selectionRegion.id =
                selectionRegionId;

            selectionRegion.classList.add(
                "timeline-selection-region"
            );

            selectionLayer.appendChild(
                selectionRegion
            );

        }

        /*
         * ============================================================
         * START_HANDLE
         * ============================================================
         */

        const startHandleId =
            "timelineSelectionStartHandle";

        let startHandle =
            document.getElementById(
                startHandleId
            );

        if (!startHandle) {

            startHandle =
                document.createElement(
                    "div"
                );

            startHandle.id =
                startHandleId;

            startHandle.classList.add(
                "timeline-selection-handle"
            );

            startHandle.classList.add(
                "timeline-selection-handle-start"
            );

            startHandle.dataset.handle =
                "start";

            selectionLayer.appendChild(
                startHandle
            );

        }

        /*
         * ============================================================
         * END_HANDLE
         * ============================================================
         */

        const endHandleId =
            "timelineSelectionEndHandle";

        let endHandle =
            document.getElementById(
                endHandleId
            );

        if (!endHandle) {

            endHandle =
                document.createElement(
                    "div"
                );

            endHandle.id =
                endHandleId;

            endHandle.classList.add(
                "timeline-selection-handle"
            );

            endHandle.classList.add(
                "timeline-selection-handle-end"
            );

            endHandle.dataset.handle =
                "end";

            selectionLayer.appendChild(
                endHandle
            );

        }

        /*
         * ============================================================
         * HANDLE POSITION
         * ============================================================
         */

        const selection =
            timelineState.selections[0];

        if (!selection) {
            return;
        }

        const startTime =
            selection.startTime;

        const endTime =
            selection.endTime;

        const duration =
            timelineState.duration;

        if (

            duration === null ||

            duration <= 0

        ) {
            return;
        }

        const hasCompleteSelection =

            startTime !== null &&

            endTime !== null;

        if (!hasCompleteSelection) {
            return;
        }

        const timelineBounds =
            selectionLayer.getBoundingClientRect();

        const timelineWidth =
            timelineBounds.width;

        const startHandleProgress =
            Math.min(

                1,

                Math.max(

                    0,

                    startTime /
                    duration

                )

            );

        const startHandlePosition =
            startHandleProgress *
            timelineWidth;

        const endHandleProgress =
            Math.min(

                1,

                Math.max(

                    0,

                    endTime /
                    duration

                )

            );

        const endHandlePosition =
            endHandleProgress *
            timelineWidth;

        /*
         * ============================================================
         * SELECTION REGION POSITION
         * ============================================================
         */

        const selectionRegionPosition =
            startHandlePosition;

        /*
         * ============================================================
         * SELECTION REGION SIZE
         * ============================================================
         */

        const selectionRegionWidth =
            endHandlePosition -
            startHandlePosition;


        /*
         * ============================================================
         * SELECTION REGION VISIBILITY
         * ============================================================
         */

        const selectionRegionVisible =
            hasCompleteSelection;

        if (selectionRegionVisible) {

            selectionRegion.style.display =
                "";

        } else {

            selectionRegion.style.display =
                "none";

        }


        /*
         * ============================================================
         * HANDLE VISIBILITY
         * ============================================================
         */

        const handlesVisible =
            hasCompleteSelection;

        if (handlesVisible) {

            startHandle.style.display =
                "";

            endHandle.style.display =
                "";

        } else {

            startHandle.style.display =
                "none";

            endHandle.style.display =
                "none";

        }

        /*
         * ============================================================
         * SELECTION REGION POSITION
         * ============================================================
         */

        selectionRegion.style.left =
            `${selectionRegionPosition}px`;

        /*
         * ============================================================
         * SELECTION REGION SIZE
         * ============================================================
         */

        selectionRegion.style.width =
            `${selectionRegionWidth}px`;

        /*
         * ============================================================
         * HANDLE POSITION
         * ============================================================
         */

        startHandle.style.left =
            `${startHandlePosition}px`;

        endHandle.style.left =
            `${endHandlePosition}px`;

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