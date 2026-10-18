package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarMfa.ConfiguracaoMfa;
import io.swagger.v3.oas.annotations.media.Schema;

public record MfaConfiguracaoResponse(
        @Schema(description = "Para digitar no aplicativo, se não der para ler o QR code.") String segredo,
        @Schema(description = "Conteúdo do QR code (otpauth://totp/...). O front gera a imagem; não envie a terceiros.")
        String uri) {

    public static MfaConfiguracaoResponse de(ConfiguracaoMfa c) {
        return new MfaConfiguracaoResponse(c.segredo(), c.uri());
    }
}
