/*
 * TimelineWorkspace
 *
 * RESPONSIBILITIES:
 * - timeline composition root
 * - timeline orchestration
 *
 * IMPORTANT:
 * - must NOT access HTML5 Video
 * - must NOT access WorkspaceState
 * - must NOT perform rendering directly
 * - must NOT perform DOM manipulation
 * - must NOT access backend
 *
 * COMPOSITION CONTRACT:
 *
 * VideoWorkspaceRenderer
 *          ↓
 * TimelineWorkspace
 *          ↓
 * TimelineController
 *          ↓
 * TimelineRenderer
 *
 * TimelineWorkspace is the only component allowed
 * to compose TimelineController and TimelineRenderer.
 *
 * INPUT CONTRACT:
 *
 * {
 *     videoAsset
 * }
 *
 * Returns:
 * - Timeline HTML
 */

let timelineController = null;
let timelineView = null;
let onSeekRequested = null;

function renderTimeline({
                            videoAsset,
                            videoElement = null,
                        }) {

    if (!timelineController) {

        timelineController =
            window.TimelineController
                .createTimelineController();
    }

    const timelineViewModel =
        timelineController
            .buildTimelineViewModel({

                videoAsset

            });

    return window.TimelineRenderer
        .renderTimeline(
            timelineViewModel
        );
}

function initialize({
                        videoElement
                    }) {

    if (!videoElement) {
        return;
    }

    if (!timelineView) {

        timelineView =
            window.TimelineView
                .createTimelineView();
    }

    timelineController
        ?.setTimelineView({

            view: timelineView

        });

    videoElement.addEventListener(

        "loadedmetadata",

        () => {

            timelineController
                ?.updateDuration({

                    duration: videoElement.duration

                });

        }
    );

    const timelineTrack =
        document.getElementById(
            "timelineTrack"
        );

    if (timelineTrack) {

        timelineTrack.addEventListener(
            "mousemove",

            event => {

                timelineController
                    ?.updatePointerPosition({

                        clientX: event.clientX

                    });

            }
        );

        timelineTrack.addEventListener(
            "click",

            event => {

                const requestedTime =

                    timelineController
                        ?.requestSeek({

                            clientX: event.clientX

                        });

                if (

                    requestedTime === undefined ||

                    !onSeekRequested

                ) {
                    return;
                }

                onSeekRequested(
                    requestedTime
                );

            }
        );
    }
}

function setSeekHandler({
                            handler
                        }) {

    onSeekRequested =
        handler;
}

function updateCurrentTime({
                               currentTime
                           }) {

    if (!timelineController) {
        return;
    }

    timelineController
        .updateCurrentTime({

            currentTime

        });
}
window.TimelineWorkspace = {

    renderTimeline,
    initialize,
    updateCurrentTime,
    setSeekHandler
};