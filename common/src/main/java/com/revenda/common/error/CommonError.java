package com.revenda.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonError implements ErrorCode {
    VALIDATION_FAILED(ErrorKind.INVALID, "Um ou mais campos são inválidos. Corrija e envie de novo."),
    UNAUTHENTICATED(ErrorKind.UNAUTHENTICATED, "Faça login para continuar."),
    FORBIDDEN(ErrorKind.FORBIDDEN, "Seu perfil não tem permissão para esta ação."),
    NOT_FOUND(ErrorKind.NOT_FOUND, "Não encontramos o que você procurou."),
    REQUEST_NOT_SUPPORTED(ErrorKind.INVALID, "Esta rota não aceita o método ou o formato enviado."),
    IDEMPOTENCY_KEY_REUSED(ErrorKind.UNPROCESSABLE,
            "Esta Idempotency-Key já foi usada com outro conteúdo. Gere uma chave nova para um pedido novo."),
    IDEMPOTENCY_KEY_REQUIRED(ErrorKind.PRECONDITION_REQUIRED, "Envie o cabeçalho Idempotency-Key com um UUID novo."),
    RATE_LIMITED(ErrorKind.TOO_MANY_REQUESTS, "Muitas tentativas seguidas. Aguarde um pouco e tente de novo."),
    DEPENDENCY_UNAVAILABLE(ErrorKind.UNAVAILABLE,
            "Um serviço necessário está fora do ar. Nada foi alterado; tente de novo em instantes."),
    INTERNAL_ERROR(ErrorKind.INTERNAL,
            "Erro inesperado. Tente de novo; se continuar, fale com o suporte informando o requestId.");

    private final ErrorKind kind;
    private final String message;
}
