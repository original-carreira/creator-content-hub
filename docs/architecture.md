Controller → Service → Domain → Export
                    ↓
                 Client
                    ↓
             Infrastructure


# Media Operations

## Contratos Oficiais

### Selection

Representa a seleção produzida pelo domínio Clip e utilizada como contrato de integração entre domínios.

Observações:

- Não deve ser renomeada por domínios consumidores.
- Deve preservar a nomenclatura oficial adotada pelo sistema.
- Atua como contrato de integração entre Clip e Media Operations.

---

### OperationValidationResult

Contrato oficial de retorno das validações operacionais.

Estrutura conceitual:

OperationValidationResult

↓

isValid

↓

violations[]

Princípios:

- O contrato deve permanecer estável.
- As regras de validação evoluem incrementalmente.
- O contrato não deverá ser reduzido a booleanos.
- O contrato representa o resultado da operação e não apenas sucesso/falha.

---

## Códigos Oficiais do Domínio

### missing_start

Descrição:

A seleção não possui início definido.

Introduzido:

IMP-007 — Media Operations Foundation

---

### missing_end

Descrição:

A seleção não possui fim definido.

Introduzido:

IMP-007 — Media Operations Foundation

---

### invalid_range

Descrição:

A seleção possui intervalo inválido.

Regra atual:

startTime >= endTime

Introduzido:

IMP-007 — Media Operations Foundation

---

## Convenção para Domain Codes

Todos os códigos de domínio deverão seguir obrigatoriamente:

- idioma inglês;
- letras minúsculas;
- snake_case;
- representar condição de negócio;
- não representar mensagens para usuário;
- ser estáveis ao longo da evolução do domínio.

Exemplos:

missing_start

missing_end

invalid_range

operation_not_supported

selection_locked

---

## Princípio Arquitetural

Quando um domínio for orientado a operações, seus contratos públicos deverão representar o resultado da operação, e não apenas sucesso ou falha.

Fluxo oficial:

Operação

↓

OperationValidationResult

↓

violations[]