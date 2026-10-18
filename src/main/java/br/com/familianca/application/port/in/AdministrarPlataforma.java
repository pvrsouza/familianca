package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Assinatura;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Plano;

import java.util.List;

/** Operação da plataforma, só ADMIN. Enquanto não há gateway de pagamento, a assinatura é ajustada aqui. */
public interface AdministrarPlataforma {

    List<ResumoFamilia> listarFamilias();

    Familia alterarAssinatura(Long familiaId, Assinatura assinatura);

    record ResumoFamilia(Familia familia, Plano planoEmVigor, long membrosAtivos) {
    }
}
