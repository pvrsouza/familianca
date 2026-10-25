package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarFamilia;
import br.com.familianca.application.port.out.ConviteRepository;
import br.com.familianca.application.port.out.DadosFamiliaRepository;
import br.com.familianca.application.port.out.DadosFamiliaRepository.Apagados;
import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.SessoesUsuario;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RecursoForaDoPlanoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Convite;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.Usuario;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.service.CatalogoCategorias;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Percentual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

import static br.com.familianca.domain.Validacoes.obrigatorio;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class FamiliaService implements GerenciarFamilia {

    private static final Logger log = LoggerFactory.getLogger(FamiliaService.class);

    private final AcessoFamilia acesso;
    private final UsuarioAtual usuarioAtual;
    private final FamiliaRepository familias;
    private final MembroRepository membros;
    private final ConviteRepository convites;
    private final UsuarioRepository usuarios;
    private final SessoesUsuario sessoes;
    private final Relogio relogio;
    private final Duration validadeConvite;
    private final DadosFamiliaRepository dados;
    private final PlanoContasRepository planoContas;
    private final PasswordEncoder senhas;

    public FamiliaService(AcessoFamilia acesso, UsuarioAtual usuarioAtual, FamiliaRepository familias,
                          MembroRepository membros, ConviteRepository convites, UsuarioRepository usuarios,
                          SessoesUsuario sessoes, Relogio relogio,
                          @Value("${familianca.convites.validade:7d}") Duration validadeConvite,
                          DadosFamiliaRepository dados, PlanoContasRepository planoContas, PasswordEncoder senhas) {
        this.acesso = acesso;
        this.usuarioAtual = usuarioAtual;
        this.familias = familias;
        this.membros = membros;
        this.convites = convites;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.relogio = relogio;
        this.validadeConvite = validadeConvite;
        this.dados = dados;
        this.planoContas = planoContas;
        this.senhas = senhas;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public FamiliaDetalhe consultar() {
        Familia familia = acesso.familia();
        return new FamiliaDetalhe(familia, familia.assinatura().planoEmVigor(relogio.hoje()),
                membros.listar(familia.id()), convites.listarPendentes(familia.id(), relogio.agora()),
                acesso.temSaldoInicial(), acesso.primeiroAno());
    }

    @Override
    public Familia atualizar(String nome, Percentual metaAporte) {
        Familia familia = acesso.familia();
        familia.atualizar(nome, metaAporte);
        return familias.salvar(familia);
    }

    @Override
    public Familia definirEstimativa(Estimativa estimativa) {
        Familia familia = acesso.familia();
        familia.definirEstimativa(estimativa);
        return familias.salvar(familia);
    }

    @Override
    public Familia definirEstimativaAporte(Estimativa estimativa) {
        Familia familia = acesso.familia();
        Estimativa anterior = familia.estimativaAporte();
        familia.definirEstimativaAporte(estimativa);
        Familia salva = familias.salvar(familia);
        log.info("auditoria evento=REGRA_META_APORTE_ALTERADA usuarioId={} familiaId={} de={} para={}",
                usuarioAtual.id(), familia.id(), anterior, estimativa);
        return salva;
    }

    @Override
    public void definirMetaAporte(Competencia competencia, Percentual meta) {
        obrigatorio(competencia, "mês");
        int primeiro = acesso.primeiroAno();
        int ultimo = VisaoAnual.ultimoAnoPrevisto(relogio.hoje());
        if (competencia.ano() < primeiro || competencia.ano() > ultimo) {
            throw new RegraNegocioException("A meta de aporte vale de " + primeiro + " a " + ultimo);
        }
        Familia familia = acesso.familia();
        Percentual anterior = familia.metasAporte().get(competencia);
        familia.definirMetaAporte(competencia, meta);
        familias.salvar(familia);
        // "estimativa" = sem meta definida no mês (vale a estimada pela regra).
        log.info("auditoria evento={} usuarioId={} familiaId={} mes={} de={} para={}",
                meta != null ? "META_APORTE_DEFINIDA" : "META_APORTE_REMOVIDA", usuarioAtual.id(), familia.id(),
                competencia, anterior != null ? anterior : "estimativa", meta != null ? meta : "estimativa");
    }

    @Override
    public Membro alterarPapel(Long membroId, PapelFamilia papel) {
        obrigatorio(papel, "papel");
        Membro membro = exigirMembro(membroId);
        if (membro.papel() == papel) {
            return membro;
        }
        protegerProprioAcessoEUltimoDono(membro);
        Membro salvo = membros.salvar(membro.comPapel(papel));
        sessoes.encerrarTodas(membro.email());
        return salvo;
    }

    @Override
    public void removerMembro(Long membroId) {
        Membro membro = exigirMembro(membroId);
        if (!membro.ativo()) {
            return;
        }
        protegerProprioAcessoEUltimoDono(membro);
        membros.salvar(membro.inativo());
        sessoes.encerrarTodas(membro.email());
    }

    @Override
    public ConviteCriado convidar(String email, PapelFamilia papel) {
        obrigatorio(papel, "papel");
        String normalizado = Usuario.normalizarEmail(obrigatorio(email, "email"));
        Usuario.validarEmail(normalizado);
        Long familiaId = usuarioAtual.familiaId();
        usuarios.buscarPorEmail(normalizado).ifPresent(u -> {
            boolean daFamilia = membros.buscarPorUsuario(u.id()).filter(m -> m.familiaId().equals(familiaId)).isPresent();
            throw new ConflitoException(daFamilia ? "Esta pessoa já participa da família"
                    : "Este e-mail já tem conta no Familiança; por enquanto cada pessoa participa de uma única família");
        });
        OffsetDateTime agora = relogio.agora();
        convites.buscarPendente(familiaId, normalizado).ifPresent(pendente -> {
            if (pendente.valido(agora)) {
                throw new ConflitoException("Já existe convite pendente para este e-mail");
            }
            pendente.cancelar(); // expirado: dá lugar ao novo
            convites.salvar(pendente);
        });
        Plano plano = acesso.planoEmVigor();
        long ocupadas = membros.contarAtivos(familiaId) + convites.listarPendentes(familiaId, agora).size();
        if (ocupadas >= plano.limiteMembros()) {
            throw new RecursoForaDoPlanoException(plano, null, "O plano " + plano.rotulo() + " permite até "
                    + plano.limiteMembros() + " pessoas na família (contando os convites pendentes)");
        }
        String token = TokensConvite.gerar();
        Convite convite = convites.salvar(Convite.novo(familiaId, normalizado, papel, TokensConvite.hash(token),
                agora.plus(validadeConvite), usuarioAtual.membroId()));
        return new ConviteCriado(convite, token);
    }

    @Override
    public void cancelarConvite(Long conviteId) {
        Convite convite = convites.buscar(usuarioAtual.familiaId(), conviteId)
                .orElseThrow(() -> new NaoEncontradoException("Convite", conviteId));
        convite.cancelar();
        convites.salvar(convite);
    }

    @Override
    public void apagarDados(String senha) {
        Usuario usuario = usuarios.buscarPorId(usuarioAtual.id()).orElseThrow();
        if (senha == null || !senhas.matches(senha, usuario.senhaHash())) {
            throw new RegraNegocioException("Senha incorreta");
        }
        Long familiaId = usuarioAtual.familiaId();
        Apagados apagados = dados.apagarTudo();
        Familia familia = acesso.familia();
        familia.limparMetasAporte();
        familias.salvar(familia);
        planoContas.criarModelo(familiaId, CatalogoCategorias.padrao());
        log.info("auditoria evento=DADOS_FAMILIA_APAGADOS usuarioId={} familiaId={} lancamentos={} recorrencias={} "
                        + "saldos={} locaisSaldo={} investimentos={} grupos={} categorias={}", usuario.id(), familiaId,
                apagados.lancamentos(), apagados.recorrencias(), apagados.saldos(), apagados.locaisSaldo(),
                apagados.investimentos(),
                apagados.grupos(), apagados.categorias());
    }

    private Membro exigirMembro(Long membroId) {
        return membros.buscar(usuarioAtual.familiaId(), membroId)
                .orElseThrow(() -> new NaoEncontradoException("Membro", membroId));
    }

    /** Evita ficar sem ninguém para gerenciar a família. */
    private void protegerProprioAcessoEUltimoDono(Membro membro) {
        if (membro.id().equals(usuarioAtual.membroId())) {
            throw new RegraNegocioException("Não é possível remover nem mudar o papel de si mesmo");
        }
        if (membro.dono() && membro.ativo() && membros.contarDonosAtivos(membro.familiaId()) <= 1) {
            throw new RegraNegocioException("É preciso manter pelo menos um dono ativo na família");
        }
    }
}
