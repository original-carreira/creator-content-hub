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
        target?.id ===
        "timelineSelectionRegion"
    );
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
                    !isSelectionRegionTarget(
                        event.target
                    )
                ) {
                    return;
                }

                event.stopPropagation();

                activeSelectionDragTarget =
                    event.target;

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
                    return;
                }

                const selectionMoveInteraction = {

                    selectionRegionId:
                    event.target.id,

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
                    !activeSelectionDragTarget ||
                    !isSelectionRegionTarget(
                        event.target
                    )
                ) {
                    return;
                }

                event.stopPropagation();

                event.target.releasePointerCapture(
                    event.pointerId
                );

                activeSelectionDragTarget =
                    null;

                const selectionMoveReleaseInteraction = {

                    selectionRegionId:
                    event.target.id

                };

                if (

                    !onSelectionMoveReleaseInteractionRequested

                ) {

                    return;

                }

                onSelectionMoveReleaseInteractionRequested(
                    selectionMoveReleaseInteraction
                );

            }

        );

        selectionLayer?.addEventListener(

            "click",

            event => {

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

                if (!handle) {
                    return;
                }

                event.stopPropagation();

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
                    return;
                }

                const handleInteraction = {

                    handle,

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

                if (!handle) {
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

                if (!handle) {
                    return;
                }

                if (
                    event.target.hasPointerCapture(
                        event.pointerId
                    )
                ) {

                    event.target.releasePointerCapture(
                        event.pointerId
                    );

                }

                const interactionTime =
                    resolveInteractionTime(
                        event.clientX
                    );

                const handleReleaseInteraction = {

                    handle,

                    interactionTime

                };

                if (
                    !onHandleReleaseInteractionRequested
                ) {
                    return;
                }

                onHandleReleaseInteractionRequested(
                    handleReleaseInteraction
                );

            }

        );

        timelineTrack.addEventListener(
            "pointerdown",

            event => {

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
