package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Convite;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.vo.Percentual;

import java.util.List;

/** A família do usuário logado. Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarFamilia {

    FamiliaDetalhe consultar();

    Familia atualizar(String nome, Percentual metaAporte);

    /** Regra padrão para estimar os próximos meses das despesas que não se repetem. */
    Familia definirEstimativa(Estimativa estimativa);

    /** Ninguém muda o próprio papel; sempre sobra pelo menos um DONO ativo. Encerra as sessões do membro. */
    Membro alterarPapel(Long membroId, PapelFamilia papel);

    /** Inativa o membro (o histórico dele fica) e encerra as sessões. Ninguém remove a si mesmo. */
    void removerMembro(Long membroId);

    /** Convite por link, válido por alguns dias. O token só aparece aqui; o banco guarda o hash. */
    ConviteCriado convidar(String email, PapelFamilia papel);

    void cancelarConvite(Long conviteId);

    /**
     * Apaga todo o orçamento (lançamentos, recorrências, saldos, investimentos e o plano de contas) e recria as
     * categorias padrão, para recomeçar do zero. Pessoas, convites e assinatura ficam. Confere a senha de quem pede.
     */
    void apagarDados(String senha);

    /** temSaldoInicial: sem ele não se lança nada (o front leva para informar o saldo antes). */
    record FamiliaDetalhe(Familia familia, Plano planoEmVigor, List<Membro> membros, List<Convite> convitesPendentes,
                          boolean temSaldoInicial) {
    }

    record ConviteCriado(Convite convite, String token) {
    }
}
