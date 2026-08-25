/*
 * SelectionEditingController
 *
 * RESPONSABILIDADES
 * - composição do domínio Selection Editing
 * - orquestração do domínio Selection Editing
 *
 * IMPORTANTE
 * - NÃO deve acessar o DOM diretamente
 * - NÃO deve acessar o HTML5 Video
 * - NÃO deve realizar renderização
 * - NÃO deve modificar diretamente o WorkspaceState
 * - NÃO deve acessar o backend
 *
 * FOUNDATION
 *
 * Este controller representa o Bounded Context
 * Selection Editing introduzido pela IMP-010.
 *
 * Durante esta Foundation sua evolução ocorrerá
 * incrementalmente seguindo o Plano Oficial
 * de Execução homologado.
 *
 * PIPELINE DE EVOLUÇÃO
 *
 * Interação
 *      ↓
 * Resolução
 *      ↓
 * Decisão
 *      ↓
 * Execução
 *      ↓
 * Estado da Clip Selection
 *
 * Este controller é o proprietário do pipeline
 * de edição.
 *
 * A Timeline permanece responsável apenas por
 * produzir as interações.
 *
 * O ClipSelectionController permanece sendo o
 * proprietário do estado da seleção.
 */

function createSelectionEditingController(
    dependencies
) {
    const {

        clipSelectionController

    } = dependencies;

    /*
     * ============================================================
     * Estado Privado
     * ============================================================
     *
     * Estado operacional pertencente exclusivamente
     * ao domínio Selection Editing.
     *
     * Este estado NÃO representa:
     *
     * - WorkspaceState
     * - Estado da Timeline
     * - Estado da Clip Selection
     *
     * Existe apenas para coordenar o pipeline
     * interno de edição.
     *
     * IMPORTANTE
     *
     * O estado evolui incrementalmente durante
     * a IMP-010.
     *
     * Cada etapa da Foundation materializa apenas
     * o conhecimento necessário para o estágio
     * atual do pipeline.
     */

    const state = {

        editingSession: {

            resizeSession: null,

            moveSession: null

        },

        interactionResolution: null,

        editingDecision: null
    };

    /*
     * ============================================================
     * Sessão de Edição
     * ============================================================
     *
     * RESPONSABILIDADES
     *
     * - coordenar o ciclo de vida da edição
     * - manter informações operacionais temporárias
     *
     * EVOLUÇÃO
     *
     * Esta responsabilidade permanece neste
     * controller enquanto pertencer
     * exclusivamente ao domínio Selection Editing.
     *
     * GATILHOS DE EXTRAÇÃO
     *
     * Considerar extração quando:
     *
     * - surgirem múltiplos consumidores;
     * - existir reutilização entre domínios;
     * - o gerenciamento do ciclo de vida tornar-se
     *   independente;
     * - esta responsabilidade tornar-se dominante
     *   dentro do controller.
     * ============================================================
     */

    /*
     * Implementação futura.
     */

    /*
     * ============================================================
     * Resolução da Interação
     * ============================================================
     *
     * RESPONSABILIDADES
     *
     * - resolver o alvo operacional da interação;
     * - identificar a região operacional da edição.
     *
     * ENTRADA
     *
     * Timeline Interaction
     *
     * SAÍDA
     *
     * Interaction Resolution
     *
     * EVOLUÇÃO
     *
     * Esta responsabilidade pertence
     * exclusivamente ao domínio
     * Selection Editing.
     *
     * GATILHOS DE EXTRAÇÃO
     *
     * Considerar extração quando:
     *
     * - surgirem múltiplos algoritmos
     *   de resolução;
     * - surgirem múltiplos consumidores;
     * - existir reutilização entre domínios;
     * - esta responsabilidade tornar-se dominante
     *   dentro do controller.
     * ============================================================
     *
     * CONTRATO
     *
     * Entrada
     *
     * Timeline Interaction
     *
     * Responsável por representar uma interação
     * produzida pela Timeline e consumida pelo
     * domínio Selection Editing.
     *
     * Nesta etapa o contrato permanece apenas
     * conceitualmente definido.
     *
     * Sua materialização ocorrerá
     * incrementalmente durante a
     * Interaction Resolution Foundation.
     */

    function handleTimelineInteraction(
        timelineInteraction
    ) {

        processInteractionResolution(
            resolveInteraction(
                timelineInteraction
            )
        );

    }

    function processInteractionResolution(
        interactionResolution
    ) {

        state.interactionResolution =
            interactionResolution;

        const clipSelection =
            clipSelectionController
                .getSelection();

        const selectionState =
            resolveSelectionState(
                clipSelection
            );

        const editingDecision =
            resolveEditingDecision({

                selectionState,

                interactionResolution,

                clipSelection

            });

        state.editingDecision =
            editingDecision;

        executeEditingDecision(
            editingDecision
        );

        return editingDecision;
    }

    function handleHandleInteraction(
        handleInteraction
    ) {

        processInteractionResolution(
            resolveHandleInteraction(
                handleInteraction
            )
        );

    }

    function handleHandleMoveInteraction(
        handleMoveInteraction
    ) {

        processInteractionResolution(
            resolveHandleMoveInteraction(
                handleMoveInteraction
            )
        );

    }

    function handleHandleReleaseInteraction(
        handleReleaseInteraction
    ) {

        processInteractionResolution(
            resolveHandleReleaseInteraction(
                handleReleaseInteraction
            )
        );

    }

    function handleSelectionMoveInteraction(
        selectionMoveInteraction
    ) {

        processInteractionResolution(
            resolveSelectionMoveInteraction(
                selectionMoveInteraction
            )
        );

    }

    function handleSelectionMoveUpdateInteraction(
        selectionMoveUpdateInteraction
    ) {

        processInteractionResolution(
            resolveSelectionMoveUpdateInteraction(
                selectionMoveUpdateInteraction
            )
        );

    }

    function handleSelectionMoveReleaseInteraction(
        selectionMoveReleaseInteraction
    ) {

        processInteractionResolution(
            resolveSelectionMoveReleaseInteraction(
                selectionMoveReleaseInteraction
            )
        );

    }

    function resolveInteraction(
        timelineInteraction
    ) {

        /*
         * Resultado da resolução da interação.
         *
         * Representa exclusivamente o resultado
         * produzido pelo domínio de resolução.
         *
         * Modelo conceitual:
         *
         * InteractionResolution
         *
         * Este modelo será consumido pela
         * Editing Decision Foundation.
         *
         * O contrato deverá evoluir apenas
         * quando novas informações forem
         * produzidas pela Interaction
         * Resolution Foundation.
         */

        /*************************************************
         * ALT-01
         *
         * Primeira regra da Interaction Resolution
         *
         * Nesta Foundation toda interação produzida pela
         * Timeline representa o início de uma interação
         * de ponteiro sobre a Timeline.
         *************************************************/

        const interactionType =
            "TIMELINE_POINTER_DOWN";

        /*
         * ALT-08
         *
         * Primeira informação operacional
         * propagada pela Interaction Resolution.
         */

        const interactionTime =
            timelineInteraction
                .interactionTime;

        const target = null;

        const hitLocation = null;

        const interactionResolution = {

            interactionType,

            interactionTime,

            target,

            hitLocation

        };

        return interactionResolution;

    }

    function resolveHandleInteraction(
        handleInteraction
    ) {

        const interactionType =
            "HANDLE_POINTER_DOWN";

        const handle =
            handleInteraction.handle;

        const interactionResolution = {

            interactionType,

            handle,

            interactionTime:
            handleInteraction.interactionTime

        };

        return interactionResolution;

    }

    function resolveHandleMoveInteraction(
        handleMoveInteraction
    ) {

        const interactionType =
            "HANDLE_POINTER_MOVE";

        const handle =
            handleMoveInteraction.handle;

        const interactionResolution = {

            interactionType,

            handle,

            interactionTime:
            handleMoveInteraction.interactionTime

        };

        return interactionResolution;

    }

    function resolveHandleReleaseInteraction(
        handleReleaseInteraction
    ) {

        const interactionType =
            "HANDLE_POINTER_UP";

        const handle =
            handleReleaseInteraction.handle;

        const interactionResolution = {

            interactionType,

            handle,

            interactionTime:
            handleReleaseInteraction.interactionTime

        };

        return interactionResolution;

    }

    function resolveSelectionMoveInteraction(
        selectionMoveInteraction
    ) {

        const interactionType =
            "SELECTION_POINTER_DOWN";

        const selectionRegionId =
            selectionMoveInteraction.selectionRegionId;

        const interactionResolution = {

            interactionType,

            selectionRegionId,

            interactionTime:
            selectionMoveInteraction.interactionTime

        };

        return interactionResolution;

    }

    function resolveSelectionMoveUpdateInteraction(
        selectionMoveUpdateInteraction
    ) {

        const interactionType =
            "SELECTION_POINTER_MOVE";

        const interactionResolution = {

            interactionType,

            selectionRegionId:
            selectionMoveUpdateInteraction
                .selectionRegionId,

            interactionTime:
            selectionMoveUpdateInteraction
                .interactionTime

        };

        return interactionResolution;

    }

    function resolveSelectionMoveReleaseInteraction(
        selectionMoveReleaseInteraction
    ) {

        const interactionType =
            "SELECTION_POINTER_UP";

        const interactionResolution = {

            interactionType,

            selectionRegionId:
            selectionMoveReleaseInteraction
                .selectionRegionId

        };

        return interactionResolution;

    }

    /*
     * ============================================================
     * Decisão de Edição
     * ============================================================
     *
     * RESPONSABILIDADES
     *
     * - transformar uma Interaction Resolution
     *   em uma Editing Decision.
     *
     * ENTRADA
     *
     * Interaction Resolution
     *
     * SAÍDA
     *
     * Editing Decision
     *
     * EVOLUÇÃO
     *
     * Editing Decision representa um objeto
     * de domínio.
     *
     * Sua evolução deverá ocorrer através
     * do enriquecimento do contrato e não
     * pela criação de contratos paralelos.
     *
     * GATILHOS DE EXTRAÇÃO
     *
     * Considerar extração quando:
     *
     * - surgirem múltiplas estratégias
     *   de decisão;
     * - surgirem múltiplos consumidores;
     * - as políticas de decisão tornarem-se
     *   independentes.
     * ============================================================
     */

    function resolveSelectionState(
        clipSelection
    ) {

        const hasSelectionStart =

            clipSelection.startTime !== null;

        const hasSelectionEnd =

            clipSelection.endTime !== null;

        return {

            hasSelectionStart,

            hasSelectionEnd,

            isEmptySelection:

                !hasSelectionStart &&

                !hasSelectionEnd,

            isOpenSelection:

                hasSelectionStart &&

                !hasSelectionEnd,

            isCompletedSelection:

                hasSelectionStart &&

                hasSelectionEnd

        };

    }

    /*
     * ============================================================
     * Políticas de Decisão
     * ============================================================
     *
     * RESPONSABILIDADES
     *
     * - avaliar as regras operacionais da edição;
     * - determinar quando uma operação pode ser executada;
     *
     * IMPORTANTE
     *
     * Estas funções não produzem decisões.
     *
     * Apenas avaliam políticas utilizadas pela
     * Editing Decision.
     *
     * EVOLUÇÃO
     *
     * Novas políticas serão adicionadas
     * incrementalmente conforme novos estados
     * operacionais forem materializados.
     * ============================================================
     */

    function canBeginSelectionEditing(
        selectionState
    ) {

        return (
            selectionState.isEmptySelection
        );

    }

    function canCompleteSelectionEditing({
                                             selectionState,
                                             interactionResolution,
                                             clipSelection
                                         }) {

        if (
            !selectionState.isOpenSelection
        ) {
            return false;
        }

        /*
         * S-02
         *
         * A conclusão da seleção somente
         * é permitida quando a interação
         * ocorre após o início da seleção.
         */

        return (

            interactionResolution
                .interactionTime >

            clipSelection
                .startTime

        );

    }

    function canRestartSelectionEditing({
                                            selectionState,
                                            interactionResolution
                                        }) {

        if (
            !selectionState.isCompletedSelection
        ) {
            return false;
        }

        return (

            interactionResolution
                .interactionType ===
            "TIMELINE_POINTER_DOWN"

        );

    }

    function createEditingDecision({
                                       operation,
                                       interactionResolution
                                   }) {

        return {

            operation,

            interactionTime:
            interactionResolution
                .interactionTime

        };

    }

    function resolveEditingDecision({

                                        selectionState,

                                        interactionResolution,

                                        clipSelection,

                                        editingIntent

                                    }) {

        let operation = null;
        /*
         * ============================================================
         * HANDLE_INTERACTION
         * ============================================================
         *
         * Foundation para futuras operações de
         * redimensionamento da seleção.
         */
        if (

            interactionResolution
                .interactionType ===
            "HANDLE_POINTER_DOWN"

        ) {

            let operation = null;

            if (

                interactionResolution.handle ===
                "start"

            ) {

                operation =
                    "BEGIN_SELECTION_RESIZE_START";

            }

            else if (

                interactionResolution.handle ===
                "end"

            ) {

                operation =
                    "BEGIN_SELECTION_RESIZE_END";

            }

            return createEditingDecision({

                operation,

                interactionResolution

            });

        }

        if (

            interactionResolution
                .interactionType ===
            "HANDLE_POINTER_MOVE"

        ) {

            let operation = null;

            if (

                state.editingSession
                    .resizeSession?.handle ===
                "start"

            ) {

                operation =
                    "UPDATE_SELECTION_RESIZE_START";

            }

            else if (

                state.editingSession
                    .resizeSession?.handle ===
                "end"

            ) {

                operation =
                    "UPDATE_SELECTION_RESIZE_END";

            }

            return createEditingDecision({

                operation,

                interactionResolution

            });

        }

        if (

            interactionResolution
                .interactionType ===
            "HANDLE_POINTER_UP"

        ) {

            return createEditingDecision({

                operation:
                    "END_SELECTION_RESIZE",

                interactionResolution

            });

        }

        if (

            interactionResolution
                .interactionType ===
            "SELECTION_POINTER_DOWN"

        ) {

            return createEditingDecision({

                operation:
                    "BEGIN_SELECTION_MOVE",

                interactionResolution

            });

        }

        if (

            interactionResolution
                .interactionType ===
            "SELECTION_POINTER_MOVE"

        ) {

            return createEditingDecision({

                operation:
                    "UPDATE_SELECTION_MOVE",

                interactionResolution

            });

        }

        if (

            interactionResolution
                .interactionType ===
            "SELECTION_POINTER_UP"

        ) {

            return createEditingDecision({

                operation:
                    "END_SELECTION_MOVE",

                interactionResolution

            });

        }

        /*
         * ============================================================
         * RESTART_SELECTION_EDITING
         * ============================================================
         */

        if (

            canRestartSelectionEditing({

                selectionState,

                interactionResolution

            })

        ) {

            operation =
                "RESTART_SELECTION_EDITING";

        }


        /*
         * ============================================================
         * BEGIN_SELECTION_EDITING
         *
         * Criação de uma nova seleção.
         * ============================================================
         */

        else if (

            canBeginSelectionEditing(

                selectionState

            )

        ) {

            operation =
                "BEGIN_SELECTION_EDITING";

        }


        /*
         * ============================================================
         * COMPLETE_SELECTION_EDITING
         *
         * Finalização da seleção aberta.
         * ============================================================
         */

        else if (

            canCompleteSelectionEditing({

                selectionState,

                interactionResolution,

                clipSelection

            })

        ) {

            operation =
                "COMPLETE_SELECTION_EDITING";

        }


        return createEditingDecision({

            operation,

            interactionResolution

        });

    }

    function executeEditingDecision(
        editingDecision
    ) {

        switch (
            editingDecision.operation
            ) {

            case "BEGIN_SELECTION_EDITING":

                /*
                 * ALT-13
                 *
                 * A decisão já determinou que a
                 * interação representa o início
                 * da seleção.
                 */

                clipSelectionController
                    .setClipStartTime(
                        editingDecision.interactionTime
                    );

                break;

            case "COMPLETE_SELECTION_EDITING":

                /*
                 * ALT-13
                 *
                 * A decisão já determinou que a
                 * interação representa a conclusão
                 * da seleção.
                 */

                clipSelectionController
                    .setClipEndTime(
                        editingDecision.interactionTime
                    );

                break;

            case "RESTART_SELECTION_EDITING":

                /*
                 * ALT-14
                 *
                 * A decisão determinou que uma seleção
                 * completa deve ser substituída por uma
                 * nova intenção de seleção.
                 *
                 * A execução limpa o estado anterior
                 * e inicia uma nova seleção.
                 */

                clipSelectionController
                    .clearSelection();

                clipSelectionController
                    .setClipStartTime(
                        editingDecision.interactionTime
                    );

                break;

            case "BEGIN_SELECTION_RESIZE_START":

                state.editingSession.resizeSession = {

                    handle: "start",
                    state: "ACTIVE",
                    interactionTime:
                    editingDecision.interactionTime

                };

                break;

            case "BEGIN_SELECTION_RESIZE_END":

                state.editingSession.resizeSession = {

                    handle: "end",
                    state: "ACTIVE",
                    interactionTime:
                    editingDecision.interactionTime

                };

                break;

            case "BEGIN_SELECTION_MOVE":

                const selection =
                    clipSelectionController
                        .getSelection();

                state.editingSession.moveSession = {

                    interactionTime:
                    editingDecision.interactionTime,

                    startTime:
                    selection.startTime,

                    endTime:
                    selection.endTime

                };

                break;

            case "UPDATE_SELECTION_RESIZE_START":

                clipSelectionController
                    .setClipStartTime(
                        editingDecision.interactionTime
                    );

                break;

            case "UPDATE_SELECTION_RESIZE_END":

                clipSelectionController
                    .setClipEndTime(
                        editingDecision.interactionTime
                    );

                break;

            case "UPDATE_SELECTION_MOVE":

                const moveSession =
                    state.editingSession
                        .moveSession;

                if (!moveSession) {
                    break;
                }

                const interactionTime =
                    editingDecision.interactionTime;

                const anchorInteractionTime =
                    moveSession.interactionTime;

                const interactionDelta =
                    interactionTime -
                    anchorInteractionTime;

                const newStartTime =
                    moveSession.startTime +
                    interactionDelta;

                const newEndTime =
                    moveSession.endTime +
                    interactionDelta;

                clipSelectionController
                    .setSelection(

                        newStartTime,

                        newEndTime

                    );

                break;

            case "END_SELECTION_RESIZE":

                state.editingSession.resizeSession =
                    null;

                break;

            case "END_SELECTION_MOVE":

                state.editingSession.moveSession =
                    null;

                break;

            default:

                break;

        }

    }

    /*
     * ============================================================
     * Execução da Edição
     * ============================================================
     *
     * RESPONSABILIDADES
     *
     * - executar uma Editing Decision;
     * - coordenar operações sobre o
     *   ClipSelectionController.
     *
     * ENTRADA
     *
     * Editing Decision
     *
     * SAÍDA
     *
     * Estado da Clip Selection
     *
     * EVOLUÇÃO
     *
     * Esta responsabilidade permanece
     * interna enquanto o
     * ClipSelectionController for
     * o único destino da execução.
     *
     * GATILHOS DE EXTRAÇÃO
     *
     * Considerar extração quando:
     *
     * - surgirem múltiplos destinos
     *   de execução;
     * - persistência passar a integrar
     *   a execução;
     * - histórico ou undo tornarem-se
     *   parte da execução;
     * - as políticas de execução
     *   tornarem-se independentes.
     * ============================================================
     */

    /*
     * Implementação futura.
     */

    /*
     * ============================================================
     * API Pública
     * ============================================================
     *
     * A API pública evoluirá
     * incrementalmente durante
     * a IMP-010.
     *
     * Nesta primeira etapa o domínio
     * ainda não expõe capacidades
     * operacionais.
     */

    /*
     * ============================================================
     * API Pública
     * ============================================================
     *
     * A API pública evoluirá incrementalmente durante
     * a IMP-010.
     *
     * As capacidades públicas serão adicionadas apenas
     * quando o domínio efetivamente adquirir novas
     * responsabilidades.
     *
     * Nesta etapa a API permanece intencionalmente vazia.
     */

    return {

        handleTimelineInteraction,
        handleHandleInteraction,
        handleHandleMoveInteraction,
        handleHandleReleaseInteraction,
        handleSelectionMoveInteraction,
        handleSelectionMoveUpdateInteraction,
        handleSelectionMoveReleaseInteraction,
    };

}

window.SelectionEditingController = {

    createSelectionEditingController
};