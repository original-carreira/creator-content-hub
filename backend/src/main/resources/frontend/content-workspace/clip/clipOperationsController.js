/*
 * ClipOperationsController
 *
 * DOMÍNIO:
 * - Clip Operations
 *
 * RESPONSABILIDADES:
 * - executar operações sobre uma Clip Selection
 * - expor a API pública do domínio
 *
 * NÃO DEVE:
 * - conhecer DOM
 * - conhecer Timeline
 * - conhecer HTML5 Video
 * - conhecer WorkspaceState
 * - acessar backend
 */

function createClipOperationsController({
                                            clipSelectionController
                                        }) {

    function clearSelection() {

        if (
            !clipSelectionController.hasSelection()
        ) {

            return window.OperationResult
                .createOperationResult({

                    operation:
                    window.ClipOperation.CLEAR_SELECTION,

                    success: false,

                    message:
                        "Selection is already empty."

                });
        }

        clipSelectionController
            .clearSelection();

        return window.OperationResult
            .createOperationResult({

                operation:
                window.ClipOperation.CLEAR_SELECTION,

                success: true

            });
    }

    function getApi() {

        return {

            clearSelection
        };
    }

    return {

        getApi
    };
}

window.ClipOperationsController = {

    createClipOperationsController
};