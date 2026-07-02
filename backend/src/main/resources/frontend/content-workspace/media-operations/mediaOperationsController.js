/*
 * MediaOperationsController
 *
 * DOMÍNIO:
 * - Media Operations
 *
 * RESPONSABILIDADES:
 * - controlar o ciclo de vida do domínio
 * - realizar a inicialização do domínio
 * - expor a API pública do domínio
 *
 * IMPORTANTE:
 * - NÃO deve conhecer Selection
 * - NÃO deve conhecer Timeline
 * - NÃO deve conhecer WorkspaceState
 * - NÃO deve conhecer HTML5 Video
 * - NÃO deve manipular DOM
 * - NÃO deve acessar o backend
 *
 * ARQUITETURA
 *
 * Este componente representa o Composition Root
 * do domínio Media Operations.
 *
 * Toda evolução do domínio deverá ocorrer por
 * composição através deste Controller.
 *
 * DECISÃO HOMOLOGADA PELO CORE
 *
 * MediaOperationsState é uma Reserva Arquitetural
 * explícita deste domínio.
 *
 * Sua criação está prevista pela arquitetura,
 * porém NÃO deverá ser materializada enquanto
 * o domínio não possuir estado operacional próprio.
 *
 * O estado atualmente permitido neste Controller
 * limita-se exclusivamente ao estado de infraestrutura:
 *
 * - initialized
 * - dependencies
 *
 * Esse estado permanece privado ao Controller e
 * não justifica, neste momento, a criação de um
 * componente dedicado.
 *
 * GATILHO PARA EVOLUÇÃO
 *
 * A criação de MediaOperationsState somente será
 * permitida quando o domínio passar a administrar
 * efetivamente estado de negócio, como por exemplo:
 *
 * - currentSelection
 * - selectionCollection
 * - activeOperation
 * - operationHistory
 * - pendingOperations
 * - selectionMetadata
 *
 * Até que esse gatilho seja atingido, o estado
 * deverá permanecer interno ao Controller,
 * conforme decisão homologada pelo CORE.
 */

function createMediaOperationsController() {

    const domainState = {

        initialized: false,

        dependencies: {}
    };

    const domainApi = {

        validateSelection,

        resolveAvailableOperations
    };

    function initialize({
                            dependencies = {}
                        } = {}) {

        if (domainState.initialized) {
            return;
        }

        domainState.dependencies =
            dependencies;

        domainState.initialized = true;
    }

    function dispose() {

        domainState.dependencies = {};

        domainState.initialized = false;
    }

    function validateSelection(
        selection
    ) {

        const violations = [];

        if (
            selection.startTime == null
        ) {

            violations.push(
                "missing_start"
            );
        }

        if (
            selection.endTime == null
        ) {

            violations.push(
                "missing_end"
            );
        }

        if (

            selection.startTime != null &&

            selection.endTime != null &&

            selection.startTime >=
            selection.endTime

        ) {

            violations.push(
                "invalid_range"
            );
        }

        return {

            isValid:
                violations.length === 0,

            violations
        };
    }

    function resolveAvailableOperations(
        selection
    ) {

        const operations = [];

        if (
            selection.startTime == null
        ) {

            operations.push(
                "DEFINE_SELECTION_START"
            );
        }

        if (
            selection.startTime != null
        ) {

            operations.push(
                "DEFINE_SELECTION_END"
            );

            operations.push(
                "CLEAR_SELECTION"
            );
        }

        if (
            selection.endTime != null
        ) {

            if (
                selection.startTime == null
            ) {

                operations.push(
                    "DEFINE_SELECTION_START"
                );
            }

            if (
                !operations.includes(
                    "CLEAR_SELECTION"
                )
            ) {

                operations.push(
                    "CLEAR_SELECTION"
                );
            }
        }

        if (

            selection.startTime != null &&

            selection.endTime != null

        ) {

            if (
                !operations.includes(
                    "DEFINE_SELECTION_START"
                )
            ) {

                operations.push(
                    "DEFINE_SELECTION_START"
                );
            }
        }

        return {

            operations

        };
    }

    function getApi() {

        return domainApi;
    }

    return {

        initialize,

        dispose,

        getApi
    };
}
window.MediaOperationsController = {

    createMediaOperationsController
};