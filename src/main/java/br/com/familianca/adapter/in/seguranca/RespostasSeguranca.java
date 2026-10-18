package br.com.familianca.adapter.in.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** 401/403 dos filtros de segurança no mesmo formato ProblemDetail da API, com mensagem genérica. */
final class RespostasSeguranca {

    private RespostasSeguranca() {
    }

    static AuthenticationEntryPoint naoAutenticado() {
        return (request, response, e) -> escrever(response, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Faça login para continuar.");
    }

    static AccessDeniedHandler acessoNegado() {
        return (request, response, e) -> escrever(response, HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para esta operação.");
    }

    static void muitasRequisicoes(HttpServletResponse response, long segundosParaNovaTentativa) throws IOException {
        response.setHeader("Retry-After", String.valueOf(segundosParaNovaTentativa));
        escrever(response, HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições",
                "Muitas requisições em pouco tempo. Aguarde e tente novamente.");
    }

    /** Textos fixos, sem dado da requisição: montar o JSON à mão não abre brecha de injeção. */
    private static void escrever(HttpServletResponse response, HttpStatus status, String titulo, String detalhe)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(titulo, status.value(), detalhe));
    }
}
