package br.com.familianca.application.port.out;

import java.time.OffsetDateTime;

/** Registro de acesso do Marco Civil (Art. 15): só gravar e apagar o vencido; consulta só por SQL. */
public interface RegistroAcessoRepository {

    void registrar(Long usuarioId, String ip, Integer porta, OffsetDateTime momento);

    int apagarAnterioresA(OffsetDateTime limite);
}
