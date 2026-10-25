package br.com.familianca.application.port.in;

/** Usado no login completo (senha e, se houver, segundo fator), para o registro de acesso do Marco Civil. */
public interface RegistrarAcesso {

    /** {@code porta} é a de origem do cliente, quando o proxy informa; senão, nula. */
    void registrar(Long usuarioId, String ip, Integer porta);
}
