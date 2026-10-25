package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.domain.model.Assinatura;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.StatusAssinatura;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Percentual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Transactional
public class FamiliaPersistenceAdapter implements FamiliaRepository {

    private final FamiliaJpaRepository jpa;

    public FamiliaPersistenceAdapter(FamiliaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Familia salvar(Familia familia) {
        FamiliaEntity e = familia.id() != null ? jpa.findById(familia.id()).orElseThrow() : new FamiliaEntity();
        e.nome = familia.nome();
        e.plano = familia.assinatura().plano().name();
        e.statusAssinatura = familia.assinatura().status().name();
        e.assinaturaValidaAte = familia.assinatura().validaAte();
        e.metaAportePercentual = familia.metaAporte() != null ? familia.metaAporte().valor() : null;
        e.estimativa = familia.estimativa().name();
        e.estimativaAporte = familia.estimativaAporte().name();
        e.metasAporte.clear();
        familia.metasAporte().forEach((competencia, meta) -> e.metasAporte.put(competencia.toString(), meta.valor()));
        return paraDominio(jpa.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Familia> buscarPorId(Long id) {
        return jpa.findById(id).map(FamiliaPersistenceAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Familia> listar() {
        return jpa.findAll(Sort.by("id")).stream().map(FamiliaPersistenceAdapter::paraDominio).toList();
    }

    @Override
    public void travar(Long id) {
        jpa.findTravadaById(id);
    }

    private static Familia paraDominio(FamiliaEntity e) {
        return Familia.reconstituir(e.id, e.nome,
                new Assinatura(Plano.valueOf(e.plano), StatusAssinatura.valueOf(e.statusAssinatura),
                        e.assinaturaValidaAte),
                e.metaAportePercentual != null ? Percentual.of(e.metaAportePercentual) : null,
                Estimativa.valueOf(e.estimativa), Estimativa.valueOf(e.estimativaAporte),
                e.metasAporte.entrySet().stream().collect(Collectors.toMap(m -> Competencia.parse(m.getKey()),
                        m -> Percentual.of(m.getValue()))));
    }
}
