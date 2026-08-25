/*
 * TimelineController
 *
 * RESPONSIBILITIES:
 * - timeline operational coordination
 * - timeline view model preparation
 *
 * IMPORTANT:
 * - must NOT perform rendering
 * - must NOT control HTML5 Video
 * - must NOT access backend
 * - must NOT mutate WorkspaceState
 *
 * TimelineViewModel Contract
 *
 * {
 *     duration,
 *     currentTime,
 *     selections,
 *     activeSelectionId
 * }
 *
 * Future Extensions:
 * - markers
 * - overlays
 *
* CURRENT RESPONSIBILITIES:
* - current time coordination
* - View coordination
*
*FUTURE RESPONSIBILITIES:
* - current position synchronization
* - selection synchronization
* - marker synchronization
* - overlay synchronization
*
* DOM updates shall be performed through the
* Timeline DOM Contract exposed by TimelineRenderer.
*/

function createTimelineController() {

    let timelineView = null;
    let timelineState = {

        duration: null,

        currentTime: null,

        selections: [],

        activeSelectionId: null
    };

    function formatDuration(seconds) {

        if (seconds == null) {
            return "00:00:00";
        }

        const totalSeconds =
            Math.floor(seconds);

        const hours =
            String(Math.floor(totalSeconds / 3600))
                .padStart(2, "0");

        const minutes =
            String(Math.floor((totalSeconds % 3600) / 60))
                .padStart(2, "0");

        const secs =
            String(totalSeconds % 60)
                .padStart(2, "0");

        return `${hours}:${minutes}:${secs}`;
    }

    function calculateTimelinePosition({
                                           clientX
                                       }) {

        const timelineTrack =
            document.getElementById(
                "timelineTrack"
            );

        if (!timelineTrack) {
            return null;
        }

        const bounds =
            timelineTrack.getBoundingClientRect();

        const relativeX =
            clientX - bounds.left;

        const progress =
            Math.min(

                1,

                Math.max(

                    0,

                    relativeX / bounds.width

                )

            );

        if (
            timelineState.duration == null
        ) {
            return null;
        }

        return {

            progress,

            time:
                progress *
                timelineState.duration

        };
    }

    function resolveTimelinePosition({
                                         clientX
                                     }) {

        return calculateTimelinePosition({

            clientX

        });
    }

    function buildTimelineViewModel({
                                        videoAsset
    }) {

        return {

            duration: timelineState.duration,

            durationText:
                formatDuration(
                    timelineState.duration
                ),

            currentTime: null,

            selections: [],

            activeSelectionId: null
        };
    }

    function updateCurrentTime({
                                   currentTime
                               }) {
        timelineState.currentTime =
            currentTime;

        if (!timelineView) {
            return;
        }

        timelineView.updateTimeline({

            timelineState

        });
    }

    function updateDuration({
                                duration
                            }) {

        timelineState.duration =
            duration;

        if (!timelineView) {
            return;
        }

        timelineView.updateDuration({

            timelineState

        });

    }

    function updateSelections({
                                  selections,
                                  activeSelectionId = null
                              }) {

        timelineState.selections =
            selections;

        timelineState.activeSelectionId =
            activeSelectionId;

        if (!timelineView) {
            return;
        }

        timelineView.updateTimeline({

            timelineState

        });

    }

    function updatePointerPosition({
                                       clientX
                                   }) {

        const timelinePosition =

            resolveTimelinePosition({

                clientX

            });

        if (

            !timelinePosition ||

            !timelineView

        ) {
            return;
        }

        timelineView.updateHoverTime({

            hoverTime:
            timelinePosition.time

        });
    }

    function requestSeek({
                             clientX
                         }) {

        const timelinePosition =

            resolveTimelinePosition({

                clientX

            });

        if (!timelinePosition) {
            return;
        }

        return timelinePosition.time;
    }

    function setTimelineView({
                                 view
                             }) {

        timelineView = view;
    }

    return {

        buildTimelineViewModel,
        updateCurrentTime,
        updateDuration,
        updateSelections,
        updatePointerPosition,
        requestSeek,
        resolveTimelinePosition,
        setTimelineView
    };
}

window.TimelineController = {

    createTimelineController
};