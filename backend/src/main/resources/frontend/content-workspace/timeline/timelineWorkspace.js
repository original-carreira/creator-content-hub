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
let onSelectionStartRequested = null;
let onTimelineInteractionRequested = null;
let onHandleInteractionRequested = null;
let onHandleMoveInteractionRequested = null;
let onHandleReleaseInteractionRequested = null;
let onSelectionMoveInteractionRequested = null;
let onSelectionMoveUpdateInteractionRequested =  null;
let onSelectionMoveReleaseInteractionRequested = null;


/*
 * Estado operacional utilizado exclusivamente pelo
 * TimelineWorkspace para coordenar o ciclo de vida
 * das interações do ponteiro sobre a Timeline.
 *
 * IMPORTANTE:
 * - NÃO representa estado do domínio Timeline
 * - NÃO representa WorkspaceState
 * - NÃO representa estado do vídeo
 * - NÃO representa estado da Clip Selection
 *
 * Este estado existe apenas durante a coordenação
 * dos eventos do ponteiro realizada pelo
 * Composition Root da Timeline.
 */
let isPointerInteractionActive = false;
let activeSelectionDragTarget = null;
let activeSelectionDragPointerId = null;
let activeHandleDragTarget = null;
let activeHandleDragPointerId = null;

function resolveInteractionTime(clientX) {

    const timelinePosition =
        timelineController
            ?.resolveTimelinePosition({

                clientX

            });

    return timelinePosition?.time ?? null;
}

function isSelectionRegionTarget(target) {

    return (
        !isSelectionHandleTarget(target) &&
        (
            Boolean(
                target?.dataset?.selectionId
            ) ||
            target?.dataset?.selectionType === "current" ||
            target?.id === "timelineSelectionRegion"
        )
    );
}

function getSelectionId(target) {

    const selectionId =
        target?.dataset?.selectionId;

    return selectionId
        ? selectionId
        : null;
}

function isSelectionHandleTarget(target) {

    return (
        target?.dataset?.handle === "start" ||
        target?.dataset?.handle === "end"
    );
}

function hasActiveSelectionPointerDrag() {

    return (
        activeSelectionDragTarget !== null ||
        activeHandleDragTarget !== null
    );
}

function isActiveSelectionDrag(event) {

    return (
        activeSelectionDragTarget !== null &&
        event.target === activeSelectionDragTarget &&
        event.pointerId === activeSelectionDragPointerId
    );
}

function isActiveHandleDrag(event) {

    return (
        activeHandleDragTarget !== null &&
        event.target === activeHandleDragTarget &&
        event.pointerId === activeHandleDragPointerId
    );
}

function finishSelectionDrag(
    event,
    releasePointerCapture = false
) {

    if (!isActiveSelectionDrag(event)) {
        return false;
    }

    const target =
        activeSelectionDragTarget;

    const selectionMoveReleaseInteraction = {

        selectionRegionId:
        target.id,

        selectionId:
        getSelectionId(target)

    };

    activeSelectionDragTarget =
        null;

    activeSelectionDragPointerId =
        null;

    if (
        releasePointerCapture &&
        target.hasPointerCapture(
            event.pointerId
        )
    ) {

        target.releasePointerCapture(
            event.pointerId
        );

    }

    if (
        onSelectionMoveReleaseInteractionRequested
    ) {

        onSelectionMoveReleaseInteractionRequested(
            selectionMoveReleaseInteraction
        );

    }

    return true;
}

function finishHandleDrag(
    event,
    releasePointerCapture = false
) {

    if (!isActiveHandleDrag(event)) {
        return false;
    }

    const target =
        activeHandleDragTarget;

    const handleReleaseInteraction = {

        handle:
        target.dataset.handle,

        selectionId:
        getSelectionId(target),

        interactionTime:
        resolveInteractionTime(
            event.clientX
        )

    };

    activeHandleDragTarget =
        null;

    activeHandleDragPointerId =
        null;

    if (
        releasePointerCapture &&
        target.hasPointerCapture(
            event.pointerId
        )
    ) {

        target.releasePointerCapture(
            event.pointerId
        );

    }

    if (
        onHandleReleaseInteractionRequested
    ) {

        onHandleReleaseInteractionRequested(
            handleReleaseInteraction
        );

    }

    return true;
}

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

    const selectionLayer =
        document.getElementById(
            "timelineSelectionLayer"
        );

    if (timelineTrack) {

        selectionLayer?.addEventListener(

            "pointerdown",

            event => {

                if (
                    isSelectionHandleTarget(
                        event.target
                    )
                ) {

                    event.stopPropagation();

                    return;

                }

                if (
                    !isSelectionRegionTarget(
                        event.target
                    )
                ) {
                    return;
                }

                if (hasActiveSelectionPointerDrag()) {

                    event.stopPropagation();

                    return;

                }

                event.stopPropagation();

                activeSelectionDragTarget =
                    event.target;

                activeSelectionDragPointerId =
                    event.pointerId;

                event.target.setPointerCapture(
                    event.pointerId
                );

                const interactionTime =
                    resolveInteractionTime(
                        event.clientX
                    );

                if (
                    interactionTime === null
                ) {

                    finishSelectionDrag(
                        event,
                        true
                    );

                    return;

                }

                const selectionMoveInteraction = {

                    selectionRegionId:
                    event.target.id,

                    selectionId:
                    getSelectionId(event.target),

                    interactionTime

                };

                if (
                    !onSelectionMoveInteractionRequested
                ) {
                    return;
                }

                onSelectionMoveInteractionRequested(
                    selectionMoveInteraction
                );

            }

        );

        selectionLayer?.addEventListener(

            "pointermove",

            event => {

                if (
                    !activeSelectionDragTarget ||
                    !isActiveSelectionDrag(event) ||
                    !isSelectionRegionTarget(
                        event.target
                    )
                ) {
                    return;
                }

                event.stopPropagation();

                const interactionTime =
                    resolveInteractionTime(
                        event.clientX
                    );

                if (
                    interactionTime === null
                ) {
                    return;
                }

                const selectionMoveUpdateInteraction = {

                    selectionRegionId:
                    event.target.id,

                    selectionId:
                    getSelectionId(event.target),

                    interactionTime

                };

                if (

                    !onSelectionMoveUpdateInteractionRequested

                ) {

                    return;

                }

                onSelectionMoveUpdateInteractionRequested(
                    selectionMoveUpdateInteraction
                );

            }

        );

        selectionLayer?.addEventListener(

            "pointerup",

            event => {

                if (
                    !isActiveSelectionDrag(event) ||
                    !isSelectionRegionTarget(event.target)
                ) {
                    return;
                }

                event.stopPropagation();

                finishSelectionDrag(
                    event,
                    true
                );

            }

        );

        [
            "pointercancel",
            "lostpointercapture"
        ].forEach(
            eventType => {

                selectionLayer?.addEventListener(
                    eventType,
                    event => {

                        if (
                            !isActiveSelectionDrag(event)
                        ) {
                            return;
                        }

                        event.stopPropagation();

                        finishSelectionDrag(event);

                    }
                );

            }
        );

        selectionLayer?.addEventListener(

            "click",

            event => {

                if (
                    isSelectionHandleTarget(
                        event.target
                    )
                ) {

                    event.stopPropagation();

                    return;

                }

                if (
                    !isSelectionRegionTarget(
                        event.target
                    )
                ) {
                    return;
                }

                event.stopPropagation();

            }

        );

        /*
         * Coordenação do ciclo de vida da interação do ponteiro.
         *
         * O TimelineWorkspace é responsável apenas por acompanhar
         * o início e o término da interação durante a coordenação
         * dos eventos do DOM.
         * ============================================================
         * Selection Handle Interaction
         *
         * Foundation para futuras interações dos
         * Selection Handles.
         *
         * Nesta etapa apenas o ponto oficial de
         * entrada das interações é materializado.
         *
         * Nenhuma decisão de domínio é executada.
         */
        selectionLayer?.addEventListener(

            "pointerdown",

            event => {

                const handle =
                    event.target.dataset.handle;

                if (!isSelectionHandleTarget(event.target)) {
                    return;
                }

                if (hasActiveSelectionPointerDrag()) {

                    event.stopPropagation();

                    return;

                }

                event.stopPropagation();

                event.target.setPointerCapture(
                    event.pointerId
                );

                activeHandleDragTarget =
                    event.target;

                activeHandleDragPointerId =
                    event.pointerId;

                const interactionTime =
                    resolveInteractionTime(
                        event.clientX
                    );

                if (
                    interactionTime === null
                ) {

                    finishHandleDrag(
                        event,
                        true
                    );

                    return;

                }

                const handleInteraction = {

                    handle,

                    selectionId:
                    getSelectionId(event.target),

                    interactionTime

                };

                if (
                    !onHandleInteractionRequested
                ) {
                    return;
                }

                onHandleInteractionRequested(
                    handleInteraction
                );

            }

        );

        selectionLayer?.addEventListener(

            "pointermove",

            event => {

                const handle =
                    event.target.dataset.handle;

                if (
                    !activeHandleDragTarget ||
                    !isActiveHandleDrag(event) ||
                    !isSelectionHandleTarget(event.target)
                ) {
                    return;
                }

                const interactionTime =
                    resolveInteractionTime(
                        event.clientX
                    );

                if (
                    interactionTime === null
                ) {
                    return;
                }

                const handleMoveInteraction = {

                    handle,

                    selectionId:
                    getSelectionId(event.target),

                    interactionTime

                };

                if (
                    !onHandleMoveInteractionRequested
                ) {
                    return;
                }

                onHandleMoveInteractionRequested(
                    handleMoveInteraction
                );

            }

        );

        selectionLayer?.addEventListener(

            "pointerup",

            event => {

                const handle =
                    event.target.dataset.handle;

                if (
                    !isActiveHandleDrag(event) ||
                    !isSelectionHandleTarget(event.target)
                ) {
                    return;
                }

                finishHandleDrag(
                    event,
                    true
                );

            }

        );

        [
            "pointercancel",
            "lostpointercapture"
        ].forEach(
            eventType => {

                selectionLayer?.addEventListener(
                    eventType,
                    event => {

                        if (
                            !isActiveHandleDrag(event)
                        ) {
                            return;
                        }

                        event.stopPropagation();

                        finishHandleDrag(event);

                    }
                );

            }
        );

        timelineTrack.addEventListener(
            "pointerdown",

            event => {

                if (hasActiveSelectionPointerDrag()) {

                    event.stopPropagation();

                    return;

                }

                isPointerInteractionActive = true;

                if (!onTimelineInteractionRequested) {
                    return;
                }

                const timelinePosition =

                    timelineController
                        ?.resolveTimelinePosition({

                            clientX: event.clientX

                        });

                if (!timelinePosition) {
                    return;
                }

                const timelineInteraction = {

                    interactionTime:
                    timelinePosition.time

                };

                onTimelineInteractionRequested(
                    timelineInteraction
                );

            }
        );

        timelineTrack.addEventListener(
            "pointerup",

            () => {

                isPointerInteractionActive = false;

            }
        );

        timelineTrack.addEventListener(
            "pointerleave",

            () => {

                isPointerInteractionActive = false;

            }
        );

        timelineTrack.addEventListener(
            "pointermove",

            event => {

                /*
                 * Durante o Scrubbing o Hover permanece
                 * temporariamente suspenso.
                 *
                 * DECISÃO
                 *
                 * Este comportamento foi adotado após
                 * validação comportamental com o usuário
                 * (EC-001).
                 *
                 * Durante uma interação ativa, a Timeline
                 * permanece exclusivamente em modo de
                 * navegação contínua.
                 */
                if (isPointerInteractionActive) {

                    const timelinePosition =

                        timelineController
                            ?.resolveTimelinePosition({

                                clientX: event.clientX

                            });

                    if (

                        timelinePosition &&

                        onSeekRequested

                    ) {

                        onSeekRequested(
                            timelinePosition.time
                        );

                    }

                    return;

                }

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
                    requestedTime === undefined
                ) {
                    return;
                }

                /*
                 * Navegação da Timeline.
                 */
                if (onSeekRequested) {

                    onSeekRequested(
                        requestedTime
                    );

                }

                /*
                 * Construção da seleção atual.
                 *
                 * UF-02 — Timeline Selection Creation
                 *
                 * Nesta etapa, um clique na Timeline também
                 * pode definir o início da seleção atual.
                 */
                if (onSelectionStartRequested) {

                    onSelectionStartRequested(
                        requestedTime
                    );

                }

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

function setSelectionStartHandler({
                                      handler
                                  }) {

    onSelectionStartRequested =
        handler;
}

function setTimelineInteractionHandler({
                                           handler
                                       }) {

    onTimelineInteractionRequested =
        handler;

}

function setHandleInteractionHandler({
                                         handler
                                     }) {

    onHandleInteractionRequested =
        handler;

}

function setHandleMoveInteractionHandler({
                                             handler
                                         }) {

    onHandleMoveInteractionRequested =
        handler;

}

function setHandleReleaseInteractionHandler({
                                                handler
                                            }) {

    onHandleReleaseInteractionRequested =
        handler;

}

function setSelectionMoveInteractionHandler({
                                                handler
                                            }) {

    onSelectionMoveInteractionRequested =
        handler;

}

function setSelectionMoveUpdateInteractionHandler({
                                                      handler
                                                  }) {

    onSelectionMoveUpdateInteractionRequested =
        handler;

}

function setSelectionMoveReleaseInteractionHandler({
                                                       handler
                                                   }) {

    onSelectionMoveReleaseInteractionRequested =
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
    setSeekHandler,
    setSelectionStartHandler,
    setTimelineInteractionHandler,
    setHandleInteractionHandler,
    setHandleMoveInteractionHandler,
    setHandleReleaseInteractionHandler,
    setSelectionMoveInteractionHandler,
    setSelectionMoveUpdateInteractionHandler,
    setSelectionMoveReleaseInteractionHandler,
    updateCurrentTime

};
