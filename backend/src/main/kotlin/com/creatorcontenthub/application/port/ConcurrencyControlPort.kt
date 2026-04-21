package com.creatorcontenthub.application.port

interface ConcurrencyControlPort {

    /**
     * Adquire permissão para execução.
     *
     * ⚠️ BLOQUEANTE:
     * - Suspende a thread até haver disponibilidade
     *
     * Uso:
     * - Apenas em contextos controlados (não HTTP)
     */
    fun acquire()

    /**
     * Libera permissão após execução.
     *
     * ⚠️ IMPORTANTE:
     * - Deve ser chamado APENAS se acquire/tryAcquire tiver sucesso
     * - Uso incorreto pode causar inconsistência de concorrência
     */
    fun release()

    /**
     * Tenta adquirir permissão dentro de um tempo limite.
     *
     * ✔ NÃO BLOQUEANTE indefinidamente
     * ✔ Base do backpressure (Fase 10)
     *
     * @param timeoutMillis tempo máximo de espera
     * @return true se conseguiu adquirir, false se timeout
     *
     * Uso recomendado:
     * - Fluxos HTTP / entrada do sistema
     */
    fun tryAcquire(timeoutMillis: Long): Boolean
}