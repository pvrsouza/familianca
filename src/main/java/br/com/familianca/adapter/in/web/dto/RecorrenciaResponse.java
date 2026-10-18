package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.adapter.in.web.dto.Referencias.CategoriaRef;
import br.com.familianca.adapter.in.web.dto.Referencias.GrupoRef;
import br.com.familianca.adapter.in.web.dto.Referencias.MembroRef;
import br.com.familianca.application.port.in.GerenciarRecorrencias.RecorrenciaDetalhada;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecorrenciaResponse(Long id, TipoMovimento tipo, CategoriaRef categoria, GrupoRef grupo, MembroRef membro,
                                  String descricao, BigDecimal valor, int dia, Frequencia frequencia, String inicio,
                                  String fim, String observacao,
                                  @Schema(description = "Competências puladas.") List<String> puladas,
                                  @Schema(description = "Ano de referência dos valores abaixo (o atual).") int ano,
                                  @Schema(description = "valor × ocorrências no ano (sem as puladas).")
                                  BigDecimal valorNoAno,
                                  @Schema(description = "Quanto pesa num ano cheio: valor × vezes por ano.")
                                  BigDecimal valorAnualizado,
                                  @Schema(description = "Confirma sozinha no vencimento (débito automático, valor fixo).")
                                  boolean confirmarSozinha,
                                  @Schema(description = "Desde quando confirma sozinha: vale para o que vence a partir daí.")
                                  LocalDate confirmarSozinhaDesde) {

    public static RecorrenciaResponse de(RecorrenciaDetalhada d) {
        Recorrencia r = d.recorrencia();
        return new RecorrenciaResponse(r.id(), d.grupo().tipo(), CategoriaRef.de(d.categoria()), GrupoRef.de(d.grupo()),
                MembroRef.de(d.membro()), r.descricao(), r.valor().valor(), r.dia(), r.frequencia(),
                r.inicio().toString(), r.fim() != null ? r.fim().toString() : null, r.observacao(),
                r.puladas().stream().sorted().map(Competencia::toString).toList(), d.ano(), d.valorNoAno().valor(),
                d.valorAnualizado().valor(), r.confirmaSozinha(), r.confirmarSozinhaDesde());
    }
}
