package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarSaldos;
import br.com.familianca.application.port.out.SaldoRepository;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class SaldoService implements GerenciarSaldos {

    private final SaldoRepository saldos;

    public SaldoService(SaldoRepository saldos) {
        this.saldos = saldos;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<SaldoMes> listar(int ano) {
        return saldos.listar(ano).stream().sorted(Comparator.comparing(SaldoMes::competencia)).toList();
    }

    @Override
    public SaldoMes definir(Competencia competencia, Dinheiro valor, String observacao) {
        return saldos.salvar(new SaldoMes(competencia, valor, observacao));
    }

    @Override
    public void remover(Competencia competencia) {
        saldos.remover(competencia);
    }
}
