package com.revenda.common.error;

/**
 * Um vizinho do caminho síncrono não respondeu, respondeu com erro ou com algo que não se lê. A mensagem diz qual e
 * como, para o log; nunca o corpo da resposta nem dado pessoal.
 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String neighbor, String failure) {
        super(neighbor + " indisponível (" + failure + ")");
    }
}
