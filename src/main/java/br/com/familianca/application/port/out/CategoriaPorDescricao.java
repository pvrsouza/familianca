package br.com.familianca.application.port.out;

/** Categoria de um lançamento e a descrição dele (sem acento e minúscula), para sugerir categoria ao importar. */
public record CategoriaPorDescricao(String descricao, Long categoriaId) {
}
