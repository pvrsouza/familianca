package br.com.familianca.application.service;

import br.com.familianca.application.port.in.AdministrarPlataforma;
import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.Assinatura;
import br.com.familianca.domain.model.Familia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class AdminService implements AdministrarPlataforma {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final FamiliaRepository familias;
    private final MembroRepository membros;
    private final Relogio relogio;

    public AdminService(FamiliaRepository familias, MembroRepository membros, Relogio relogio) {
        this.familias = familias;
        this.membros = membros;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumoFamilia> listarFamilias() {
        LocalDate hoje = relogio.hoje();
        return familias.listar().stream()
                .map(f -> new ResumoFamilia(f, f.assinatura().planoEmVigor(hoje), membros.contarAtivos(f.id())))
                .toList();
    }

    @Override
    public Familia alterarAssinatura(Long familiaId, Assinatura assinatura) {
        Familia familia = familias.buscarPorId(familiaId)
                .orElseThrow(() -> new NaoEncontradoException("Família não encontrada: " + familiaId));
        familia.alterarAssinatura(assinatura);
        log.info("auditoria evento=ASSINATURA_ALTERADA familiaId={} plano={} status={}", familiaId,
                assinatura.plano(), assinatura.status());
        return familias.salvar(familia);
    }
}
