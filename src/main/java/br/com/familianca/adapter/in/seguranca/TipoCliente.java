package br.com.familianca.adapter.in.seguranca;

/** De onde vem a sessão. WEB: cookie do navegador. MOBILE: id de sessão no header X-Auth-Token. */
public enum TipoCliente {
    WEB,
    MOBILE
}
