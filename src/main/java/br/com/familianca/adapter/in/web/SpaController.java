package br.com.familianca.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rotas do front (Vue, history mode): ao abrir ou recarregar uma delas, o navegador recebe o index.html e o router do
 * front assume. Só as rotas listadas aqui são liberadas no {@code SecurityConfig}; qualquer outra URL continua negada.
 * A página não carrega dados: tudo que é da família vem da /api, que exige sessão.
 */
@Controller
public class SpaController {

    /** Mantenha igual às rotas de frontend/src/router.ts. */
    public static final String[] ROTAS = {
            "/entrar", "/criar-conta", "/convite/*", "/ano", "/ano/*", "/lancamentos", "/recorrencias",
            "/quanto-pesa", "/familia", "/categorias", "/investimentos", "/conta",
    };

    @GetMapping({"/entrar", "/criar-conta", "/convite/{token}", "/ano", "/ano/{ano}", "/lancamentos", "/recorrencias",
            "/quanto-pesa", "/familia", "/categorias", "/investimentos", "/conta"})
    public String spa() {
        return "forward:/index.html";
    }
}
