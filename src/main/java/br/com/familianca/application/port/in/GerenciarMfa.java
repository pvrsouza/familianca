package br.com.familianca.application.port.in;

import java.util.List;

/** Verificação em duas etapas (aplicativo autenticador) da conta do usuário logado. Opcional. */
public interface GerenciarMfa {

    boolean ativo();

    /** O ambiente tem a chave do MFA configurada. Sem ela, configurar/ativar respondem 422 (o front esconde a opção). */
    boolean disponivel();

    /**
     * Gera um segredo novo, ainda sem valer no login (refazer descarta o anterior). Já ativa: 409.
     *
     * @return segredo em Base32 (digitação manual) e o link otpauth:// para o QR code
     */
    ConfiguracaoMfa configurar();

    /**
     * Confere um código do aplicativo e passa a exigir o segundo fator no login. Encerra as outras sessões do
     * usuário, abertas sem ele.
     *
     * @return códigos de recuperação (uso único), mostrados só agora
     */
    List<String> ativar(String codigo);

    /** Troca os códigos de recuperação; exige um código do aplicativo. */
    List<String> gerarCodigosRecuperacao(String codigo);

    /** Exige a senha e um código (do aplicativo ou de recuperação). */
    void desativar(String senha, String codigo);

    record ConfiguracaoMfa(String segredo, String uri) {
    }
}
