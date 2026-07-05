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

        /*
         * Coordenação do ciclo de vida da interação do ponteiro.
         *
         * O TimelineWorkspace é responsável apenas por acompanhar
         * o início e o término da interação durante a coordenação
         * dos eventos do DOM.
         */
        timelineTrack.addEventListener(
            "pointerdown",

            () => {

                isPointerInteractionActive = true;

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

    updateCurrentTime

};