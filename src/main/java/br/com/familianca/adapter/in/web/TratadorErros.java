package br.com.familianca.adapter.in.web;

import br.com.familianca.domain.exception.AcessoNegadoException;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RecursoForaDoPlanoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * 400 validação/JSON inválido, 401 não autenticado, 402 fora do plano, 403 sem permissão, 404 não encontrado,
 * 409 conflito, 422 regra de negócio, 429 muitas tentativas, 500 inesperado. Respostas sem stack trace; o log também
 * não leva o conteúdo do corpo da requisição (pode ter senha ou dado pessoal).
 */
@RestControllerAdvice
public class TratadorErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);
    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    @ExceptionHandler(NaoEncontradoException.class)
    ProblemDetail naoEncontrado(NaoEncontradoException e, HttpServletRequest req) {
        return problema(req, HttpStatus.NOT_FOUND, "Recurso não encontrado", e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ProblemDetail conflito(ConflitoException e, HttpServletRequest req) {
        return problema(req, HttpStatus.CONFLICT, "Conflito", e.getMessage());
    }

    /** Corrida entre duas requisições que passaram pela checagem ao mesmo tempo; a constraint do banco segura. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException e, HttpServletRequest req) {
        return problema(req, HttpStatus.CONFLICT, "Conflito", "Conflito com dados já existentes. Atualize e tente de novo.");
    }

    @ExceptionHandler(RegraNegocioException.class)
    ProblemDetail regraNegocio(RegraNegocioException e, HttpServletRequest req) {
        return problema(req, UNPROCESSABLE, "Regra de negócio violada", e.getMessage());
    }

    @ExceptionHandler(RecursoForaDoPlanoException.class)
    ProblemDetail foraDoPlano(RecursoForaDoPlanoException e, HttpServletRequest req) {
        ProblemDetail p = problema(req, HttpStatus.PAYMENT_REQUIRED, "Não disponível no plano", e.getMessage());
        p.setProperty("plano", e.plano().name());
        if (e.recurso() != null) {
            p.setProperty("recurso", e.recurso().name());
        }
        return p;
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ProblemDetail acessoNegadoNaFamilia(AcessoNegadoException e, HttpServletRequest req) {
        return problema(req, HttpStatus.FORBIDDEN, "Acesso negado", e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail credenciaisInvalidas(BadCredentialsException e, HttpServletRequest req) {
        return problema(req, HttpStatus.UNAUTHORIZED, "Não autenticado", "Credenciais inválidas");
    }

    @ExceptionHandler(MfaObrigatorioException.class)
    ProblemDetail mfaObrigatorio(MfaObrigatorioException e, HttpServletRequest req) {
        ProblemDetail p = problema(req, HttpStatus.UNAUTHORIZED, "Código de verificação necessário", e.getMessage());
        p.setProperty("mfaObrigatorio", true);
        return p;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail naoAutenticado(AuthenticationException e, HttpServletRequest req) {
        return problema(req, HttpStatus.UNAUTHORIZED, "Não autenticado", "Faça login para continuar.");
    }

    /** @PreAuthorize negado dentro do controller (o filtro trata os negados antes de chegar aqui). */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail acessoNegado(AccessDeniedException e, HttpServletRequest req) {
        return problema(req, HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para esta operação.");
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    ResponseEntity<ProblemDetail> tentativasExcedidas(TentativasExcedidasException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.segundosParaNovaTentativa()))
                .body(problema(req, HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e, HttpServletRequest req) {
        log.error("{} {} -> 500", req.getMethod(), req.getRequestURI(), e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Consulte os logs.");
    }

    /** O padrão do Spring responde só "Failed to read request"; aqui o detalhe diz qual campo/valor o JSON rejeitou. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String causa = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(status,
                "JSON inválido: " + (causa == null ? ex.getMessage() : causa.lines().findFirst().orElse(causa)));
        corpo.setTitle("Requisição inválida");
        return handleExceptionInternal(ex, corpo, headers, status, request);
    }

    /** Detalhe padrão é "Invalid request content."; aqui lista campo: motivo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ex.getBody().setDetail(ex.getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; ")));
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /** Passagem única de todos os erros tratados pelo Spring (validação, JSON, método, rota): registra o motivo. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        String detalhe = ex instanceof HttpMessageNotReadableException ? "JSON inválido (detalhe omitido do log)"
                : body instanceof ProblemDetail p ? p.getDetail()
                : ex instanceof ErrorResponse e ? e.getBody().getDetail() : ex.getMessage();
        HttpServletRequest req = ((ServletWebRequest) request).getRequest();
        log.warn("{} {} -> {}: {}", req.getMethod(), req.getRequestURI(), status.value(), detalhe);
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problema(HttpServletRequest req, HttpStatusCode status, String titulo, String detalhe) {
        log.warn("{} {} -> {}: {}", req.getMethod(), req.getRequestURI(), status.value(), detalhe);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
