package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarSaldos;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.LocalSaldoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.SaldoRepository;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.MovimentoDoMes;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class SaldoService implements GerenciarSaldos {

    private final SaldoRepository saldos;
    private final LancamentoRepository lancamentos;
    private final PlanoContasRepository planoContas;
    private final LocalSaldoRepository locais;
    private final Relogio relogio;

    public SaldoService(SaldoRepository saldos, LancamentoRepository lancamentos, PlanoContasRepository planoContas,
                        LocalSaldoRepository locais, Relogio relogio) {
        this.locais = locais;
        this.saldos = saldos;
        this.lancamentos = lancamentos;
        this.planoContas = planoContas;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<SaldoMes> listar(int ano) {
        return saldos.listar(ano).stream().sorted(Comparator.comparing(SaldoMes::competencia)).toList();
    }

    @Override
    public SaldoMes definir(Competencia competencia, Dinheiro valor, List<SaldoMes.PorLocal> porLocal,
                            String observacao, LocalDate dataReferencia, SaldoMes.Conferencia conferencia) {
        if (dataReferencia != null && dataReferencia.isAfter(relogio.hoje())) {
            throw new RegraNegocioException("O saldo precisa ser de hoje ou de um dia que já passou");
        }
        // O saldo inicial (nenhum saldo antes deste mês) pode ser de qualquer mês, menos de um que ainda não chegou.
        if (competencia.compareTo(Competencia.de(relogio.hoje())) > 0
                && saldos.primeira().map(p -> p.compareTo(competencia) >= 0).orElse(true)) {
            throw new RegraNegocioException("O saldo inicial precisa ser do mês atual ou de um mês que já passou");
        }
        // Mês que já passou: só o saldo do dia 1º (está no extrato). O de outro dia só no mês corrente; um saldo
        // guardado com dia quando o mês era o corrente continua valendo e pode ser corrigido mantendo o dia.
        LocalDate dia = dataReferencia != null ? dataReferencia : conferencia != null ? conferencia.dia() : null;
        if (dia != null && competencia.compareTo(Competencia.de(relogio.hoje())) < 0
                && !dia.equals(saldos.listar(competencia.ano()).stream()
                .filter(s -> s.competencia().equals(competencia)).findFirst().map(SaldoService::diaGuardado)
                .orElse(null))) {
            throw new RegraNegocioException("Para um mês que já passou, informem só o saldo do dia 1º");
        }
        if (conferencia != null && conferencia.dia().isAfter(relogio.hoje())) {
            throw new RegraNegocioException("O saldo para conferir precisa ser de hoje ou de um dia que já passou");
        }
        if (porLocal != null && !porLocal.isEmpty()) {
            // Lugar inativo vale: a família pode corrigir um mês antigo em que ele ainda existia.
            porLocal.forEach(p -> locais.buscar(p.localId())
                    .orElseThrow(() -> new NaoEncontradoException("Lugar do saldo", p.localId())));
            return saldos.salvar(SaldoMes.porLocal(competencia, porLocal, observacao, dataReferencia, conferencia));
        }
        if (valor == null) {
            throw new RegraNegocioException("Informe o saldo");
        }
        return saldos.salvar(new SaldoMes(competencia, valor, observacao, dataReferencia, conferencia));
    }

    private static LocalDate diaGuardado(SaldoMes s) {
        return s.dataReferencia() != null ? s.dataReferencia() : s.conferencia() != null ? s.conferencia().dia() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public MovimentoDoMes movimentoAte(LocalDate data) {
        Map<Long, TipoMovimento> tipoGrupo = planoContas.listarGrupos().stream()
                .collect(Collectors.toMap(Grupo::id, Grupo::tipo));
        Map<Long, TipoMovimento> tipoPorCategoria = planoContas.listarCategorias().stream()
                .filter(c -> tipoGrupo.containsKey(c.grupoId()))
                .collect(Collectors.toMap(Categoria::id, c -> tipoGrupo.get(c.grupoId())));
        return MovimentoDoMes.ate(data, lancamentos.listar(data.withDayOfMonth(1), data), tipoPorCategoria);
    }

    @Override
    public void remover(Competencia competencia) {
        // O saldo inicial é obrigatório: o último saldo informado não sai, só se corrige (pode ser zero).
        if (saldos.contar() == 1 && saldos.listar(competencia.ano()).stream()
                .anyMatch(s -> s.competencia().equals(competencia))) {
            throw new RegraNegocioException("Este é o saldo inicial da família e não pode ser removido: corrijam o "
                    + "valor (pode ser zero ou negativo)");
        }
        saldos.remover(competencia);
    }
}
