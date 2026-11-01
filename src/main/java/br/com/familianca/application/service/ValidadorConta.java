package br.com.familianca.application.service;

import br.com.familianca.application.port.out.LocalSaldoRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.AcessoNegadoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.LocalSaldo;
import org.springframework.stereotype.Component;

/** Regras da conta (lugar onde está o dinheiro) de um lançamento ou de uma recorrência. */
@Component
class ValidadorConta {

    private final LocalSaldoRepository locais;
    private final UsuarioAtual usuario;

    ValidadorConta(LocalSaldoRepository locais, UsuarioAtual usuario) {
        this.locais = locais;
        this.usuario = usuario;
    }

    /**
     * Conta da família, ativa (ou a que o registro já tinha: contaAtual) e, para MEMBRO, dele ou da família toda.
     * Nula é válida: a conta é opcional.
     */
    void exigir(Long id, Long contaAtual) {
        if (id == null) {
            return;
        }
        LocalSaldo local = locais.buscar(id).orElseThrow(() -> new NaoEncontradoException("Lugar do saldo", id));
        if (!local.ativo() && !id.equals(contaAtual)) {
            throw new RegraNegocioException("A conta " + local.nome() + " está desativada");
        }
        if (local.membroId() != null && !usuario.dono() && !local.membroId().equals(usuario.membroId())) {
            throw new AcessoNegadoException("Só o dono da família usa uma conta de outra pessoa");
        }
    }
}
