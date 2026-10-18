package br.com.familianca;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaTest extends IntegracaoBase {

    @Test
    void migracoesCriamTodasAsTabelas() {
        List<String> tabelas = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tabelas).contains("usuario", "familia", "membro", "convite", "grupo_categoria", "categoria",
                "recorrencia", "recorrencia_excecao", "lancamento", "saldo_mes", "investimento",
                "movimento_investimento", "spring_session", "spring_session_attributes");
    }

    @Test
    void relogioDeTesteFixo() {
        assertThat(relogio.hoje()).isEqualTo(HOJE_PADRAO);
    }
}
