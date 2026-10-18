package br.com.familianca.application.service;

import br.com.familianca.application.port.in.CadastrarFamilia;
import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.model.Assinatura;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Papel;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Usuario;
import br.com.familianca.domain.service.CatalogoCategorias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sem @PreAuthorize: é o cadastro público, antes de existir login. */
@Service
@Transactional
public class CadastroService implements CadastrarFamilia {

    private static final Logger log = LoggerFactory.getLogger(CadastroService.class);

    private final UsuarioRepository usuarios;
    private final FamiliaRepository familias;
    private final MembroRepository membros;
    private final PlanoContasRepository planoContas;
    private final PasswordEncoder senhas;
    private final Relogio relogio;
    private final int diasTeste;

    public CadastroService(UsuarioRepository usuarios, FamiliaRepository familias, MembroRepository membros,
                           PlanoContasRepository planoContas, PasswordEncoder senhas, Relogio relogio,
                           @Value("${familianca.assinatura.dias-teste:30}") int diasTeste) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.membros = membros;
        this.planoContas = planoContas;
        this.senhas = senhas;
        this.relogio = relogio;
        this.diasTeste = diasTeste;
    }

    @Override
    public Membro cadastrar(String nome, String email, String senha, String nomeFamilia) {
        return criar(nome, email, senha, nomeFamilia, Papel.USER);
    }

    Membro criar(String nome, String email, String senha, String nomeFamilia, Papel papel) {
        Usuario.validarSenha(senha);
        String normalizado = Usuario.normalizarEmail(email);
        if (usuarios.buscarPorEmail(normalizado).isPresent()) {
            throw new ConflitoException("Já existe uma conta com este e-mail");
        }
        Usuario usuario = usuarios.salvar(Usuario.novo(nome, normalizado, senhas.encode(senha), papel));
        Familia familia = familias.salvar(Familia.nova(nomeFamiliaOuPadrao(nomeFamilia, usuario.nome()),
                Assinatura.teste(relogio.hoje(), diasTeste)));
        Membro dono = membros.salvar(Membro.novo(familia.id(), usuario.id(), PapelFamilia.DONO));
        planoContas.criarModelo(familia.id(), CatalogoCategorias.padrao());
        log.info("auditoria evento=FAMILIA_CRIADA usuarioId={} familiaId={}", usuario.id(), familia.id());
        return dono;
    }

    private static String nomeFamiliaOuPadrao(String nomeFamilia, String nomeUsuario) {
        if (nomeFamilia != null && !nomeFamilia.isBlank()) {
            return nomeFamilia;
        }
        return "Família de " + nomeUsuario.trim().split("\\s+")[0];
    }
}
