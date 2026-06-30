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