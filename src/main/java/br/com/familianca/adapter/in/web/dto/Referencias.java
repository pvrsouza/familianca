package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Natureza;

/** Resumos de categoria, grupo e membro embutidos nas respostas de lançamentos e recorrências. */
public final class Referencias {

    private Referencias() {
    }

    public record CategoriaRef(Long id, String nome) {
        public static CategoriaRef de(Categoria c) {
            return new CategoriaRef(c.id(), c.nome());
        }
    }

    public record GrupoRef(Long id, String nome, Natureza natureza) {
        public static GrupoRef de(Grupo g) {
            return new GrupoRef(g.id(), g.nome(), g.natureza());
        }
    }

    public record MembroRef(Long id, String nome) {
        public static MembroRef de(Membro m) {
            return m != null ? new MembroRef(m.id(), m.nome()) : null;
        }
    }
}
