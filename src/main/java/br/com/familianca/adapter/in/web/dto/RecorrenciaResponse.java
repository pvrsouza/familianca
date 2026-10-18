package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.adapter.in.web.dto.Referencias.CategoriaRef;
import br.com.familianca.adapter.in.web.dto.Referencias.GrupoRef;
import br.com.familianca.adapter.in.web.dto.Referencias.MembroRef;
import br.com.familianca.application.port.in.GerenciarRecorrencias.RecorrenciaDetalhada;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.FormaPagamento;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record RecorrenciaResponse(Long id, TipoMovimento tipo, CategoriaRef categoria, GrupoRef grupo, MembroRef membro,
                                  String descricao, BigDecimal valor, int dia, Frequencia frequencia, String inicio,
                                  String fim, String observacao,
                                  @Schema(description = "Competências puladas.") List<String> puladas,
                                  @Schema(description = "Ano de referência dos valores abaixo (o atual).") int ano,
                                  @Schema(description = "Soma do previsto de cada ocorrência do ano (sem as puladas).")
                                  BigDecimal valorNoAno,
                                  @Schema(description = "Quanto pesa num ano cheio: valor × vezes por ano.")
                                  BigDecimal valorAnualizado,
                                  @Schema(description = "Confirma sozinha no vencimento (débito automático, valor fixo).")
                                  boolean confirmarSozinha,
                                  @Schema(description = "Desde quando confirma sozinha: vale para o que vence a partir daí.")
                                  LocalDate confirmarSozinhaDesde,
                                  @Schema(description = "Valor previsto das ocorrências do mês atual em diante: a "
                                          + "estimativa pela regra (despesa de valor variável) ou o valor cadastrado.")
                                  BigDecimal valorPrevisto,
                                  @Schema(description = "Regra que gerou o valorPrevisto (MEDIA, MAXIMO, MINIMO); nulo = "
                                          + "valor cadastrado (sem confirmação no ano, receita, confirma sozinha ou NENHUMA).")
                                  Estimativa previstoPor,
                                  @Schema(description = "Quantas confirmações do ano entraram na estimativa.")
                                  int confirmacoesNaPrevisao,
                                  @Schema(description = "Marcada como sem vencimento (o dia só posiciona no mês).")
                                  boolean semVencimento,
                                  FormaPagamento formaPagamento,
                                  @Schema(description = "Despesa com data certa para pagar: não marcada sem vencimento "
                                          + "e não cobrada no cartão. Receita: sempre falso.")
                                  boolean temVencimento,
                                  @Schema(description = "Vencimentos diferentes do dia cadastrado, por ocorrência.")
                                  List<VencimentoAjustado> vencimentosAjustados) {

    public record VencimentoAjustado(String competencia, LocalDate vencimento) {
    }

    public static RecorrenciaResponse de(RecorrenciaDetalhada d) {
        Recorrencia r = d.recorrencia();
        return new RecorrenciaResponse(r.id(), d.grupo().tipo(), CategoriaRef.de(d.categoria()), GrupoRef.de(d.grupo()),
                MembroRef.de(d.membro()), r.descricao(), r.valor().valor(), r.dia(), r.frequencia(),
                r.inicio().toString(), r.fim() != null ? r.fim().toString() : null, r.observacao(),
                r.puladas().stream().sorted().map(Competencia::toString).toList(), d.ano(), d.valorNoAno().valor(),
                d.valorAnualizado().valor(), r.confirmaSozinha(), r.confirmarSozinhaDesde(),
                d.previsao() != null ? d.previsao().valor().valor() : r.valor().valor(),
                d.previsao() != null ? d.previsao().regra() : null,
                d.previsao() != null ? d.previsao().confirmacoes() : 0, r.semVencimento(), r.formaPagamento(),
                d.grupo().tipo() == TipoMovimento.DESPESA && r.temVencimento(),
                r.vencimentosAjustados().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(e -> new VencimentoAjustado(e.getKey().toString(), e.getValue())).toList());
    }
}
