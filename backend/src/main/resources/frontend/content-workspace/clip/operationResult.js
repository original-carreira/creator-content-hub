function createOperationResult({

                                   operation,

                                   success,

                                   message = null,

                                   payload = null

                               }) {

    return {

        operation,

        success,

        message,

        payload
    };
}

window.OperationResult = {

    createOperationResult
};