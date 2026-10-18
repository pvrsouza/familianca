package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Mfa;

import java.util.List;
import java.util.Optional;

/** Segundo fator e códigos de recuperação do usuário. O segredo é guardado cifrado pelo adapter. */
public interface MfaRepository {

    /** false quando não há chave para cifrar os segredos (MFA desligado no ambiente). */
    boolean disponivel();

    Optional<Mfa> buscar(Long usuarioId);

    void salvar(Mfa mfa);

    /** Apaga o segundo fator e os códigos de recuperação. */
    void remover(Long usuarioId);

    /** Substitui todos os códigos de recuperação (só os hashes). */
    void trocarCodigosRecuperacao(Long usuarioId, List<String> hashes);

    /** Marca o código como usado; false se não existe ou já foi usado. */
    boolean usarCodigoRecuperacao(Long usuarioId, String hash);
}
