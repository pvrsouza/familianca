package br.com.familianca.application.service;

import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.AcessoNegadoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RecursoForaDoPlanoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.Recurso;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Família do usuário logado: plano em vigor, membros e as permissões que dependem do papel. */
@Component
public class AcessoFamilia {

    private final UsuarioAtual usuarioAtual;
    private final FamiliaRepository familias;
    private final MembroRepository membros;
    private final Relogio relogio;

    public AcessoFamilia(UsuarioAtual usuarioAtual, FamiliaRepository familias, MembroRepository membros,
                         Relogio relogio) {
        this.usuarioAtual = usuarioAtual;
        this.familias = familias;
        this.membros = membros;
        this.relogio = relogio;
    }

    public Familia familia() {
        return familias.buscarPorId(usuarioAtual.familiaId()).orElseThrow();
    }

    public Plano planoEmVigor() {
        return familia().assinatura().planoEmVigor(relogio.hoje());
    }

    public void exigir(Recurso recurso) {
        Plano plano = planoEmVigor();
        if (!plano.permite(recurso)) {
            throw new RecursoForaDoPlanoException(plano, recurso,
                    recurso.rotulo() + " não faz parte do plano " + plano.rotulo());
        }
    }

    /** Todos os membros da família (inclusive inativos), por id. */
    public Map<Long, Membro> membrosPorId() {
        return membros.listar(usuarioAtual.familiaId()).stream()
                .collect(Collectors.toMap(Membro::id, Function.identity()));
    }

    public List<Membro> membros() {
        return membros.listar(usuarioAtual.familiaId());
    }

    /** Membro ativo da família a quem o registro vai pertencer; nulo = o membro logado. */
    public Membro responsavel(Long membroId) {
        Long id = membroId != null ? membroId : usuarioAtual.membroId();
        Membro membro = membros.buscar(usuarioAtual.familiaId(), id)
                .orElseThrow(() -> new NaoEncontradoException("Membro", id));
        if (!membro.ativo()) {
            throw new RegraNegocioException(membro.nome() + " não está mais ativo na família");
        }
        return membro;
    }

    /** MEMBRO só mexe no que é dele; DONO mexe em tudo. */
    public void exigirPodeAlterar(Long membroIdDoRegistro) {
        if (!usuarioAtual.dono() && !usuarioAtual.membroId().equals(membroIdDoRegistro)) {
            throw new AcessoNegadoException("Só o dono da família altera lançamentos de outra pessoa");
        }
    }
}
