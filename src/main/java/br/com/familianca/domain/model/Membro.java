package br.com.familianca.domain.model;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Pessoa na família. nome e email vêm do usuário (só leitura aqui). Membro inativo não entra mais, mas continua
 * dono dos lançamentos que fez (o histórico da família não muda).
 */
public record Membro(Long id, Long familiaId, Long usuarioId, String nome, String email, PapelFamilia papel,
                     boolean ativo) {

    public Membro {
        obrigatorio(familiaId, "família");
        obrigatorio(usuarioId, "usuário");
        obrigatorio(papel, "papel");
    }

    public static Membro novo(Long familiaId, Long usuarioId, PapelFamilia papel) {
        return new Membro(null, familiaId, usuarioId, null, null, papel, true);
    }

    public Membro comPapel(PapelFamilia novo) {
        return new Membro(id, familiaId, usuarioId, nome, email, novo, ativo);
    }

    public Membro inativo() {
        return new Membro(id, familiaId, usuarioId, nome, email, papel, false);
    }

    public boolean dono() {
        return papel == PapelFamilia.DONO;
    }
}
