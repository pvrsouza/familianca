package br.com.familianca.domain.service;

import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Percentual;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static br.com.familianca.domain.model.Natureza.FIXA;
import static br.com.familianca.domain.model.Natureza.VARIAVEL;
import static br.com.familianca.domain.model.TipoMovimento.DESPESA;
import static br.com.familianca.domain.model.TipoMovimento.RECEITA;

/**
 * Planos de contas prontos. O padrão é criado em toda família nova (a partir da planilha que originou o produto); o
 * modelo PJ é opcional (recurso DESPESAS_PJ). A família edita, desativa e exclui à vontade depois.
 */
public final class CatalogoCategorias {

    private CatalogoCategorias() {
    }

    public record ModeloCategoria(String nome, boolean tributavel, Percentual aliquota, boolean recorrente) {

        public ModeloCategoria(String nome, boolean tributavel, Percentual aliquota) {
            this(nome, tributavel, aliquota, false);
        }
    }

    /** Do padrão, o que costuma se repetir todo mês: ao lançar nelas, o app já sugere repetir no ano. */
    static final Set<String> RECORRENTES = Set.of("Salário", "Benefícios (vale-refeição, alimentação)",
            "Aluguéis recebidos", "Pensão e auxílios", "Aluguel ou financiamento", "Condomínio", "Energia", "Água",
            "Gás", "Internet", "Telefone e celular", "Serviços domésticos", "Plano de saúde", "Plano odontológico",
            "Financiamento do veículo", "Academia e esportes", "Mesadas", "Escola e faculdade", "Pós-graduação",
            "Tarifas bancárias", "Assinaturas e streaming", "Serviços digitais");

    public record ModeloGrupo(TipoMovimento tipo, Natureza natureza, String nome, boolean empresa,
                              List<ModeloCategoria> categorias) {
    }

    public static List<ModeloGrupo> padrao() {
        return List.of(
                grupo(RECEITA, FIXA, "Receitas fixas", "Salário", "Benefícios (vale-refeição, alimentação)",
                        "Aluguéis recebidos", "Pensão e auxílios", "Outras receitas fixas"),
                grupo(RECEITA, VARIAVEL, "Receitas variáveis", "13º salário", "Férias", "Bônus e PLR",
                        "Trabalhos extras", "Dividendos e rendimentos", "Reembolsos", "Outras receitas"),
                grupo(DESPESA, FIXA, "Habitação", "Aluguel ou financiamento", "Condomínio", "IPTU e taxas",
                        "Energia", "Água", "Gás", "Internet", "Telefone e celular", "Supermercado",
                        "Serviços domésticos", "Outros"),
                grupo(DESPESA, FIXA, "Saúde", "Plano de saúde", "Plano odontológico", "Médicos e terapeutas",
                        "Dentista", "Medicamentos", "Outros"),
                grupo(DESPESA, FIXA, "Transporte", "Financiamento do veículo", "IPVA e licenciamento",
                        "Seguro do veículo", "Combustível", "Estacionamento", "Lavagem", "Transporte público",
                        "Táxi e aplicativos", "Outros"),
                grupo(DESPESA, FIXA, "Despesas pessoais", "Higiene e cuidados pessoais", "Cosméticos",
                        "Cabeleireiro", "Vestuário", "Academia e esportes", "Anuidades de cartão", "Mesadas",
                        "Outros"),
                grupo(DESPESA, FIXA, "Educação", "Escola e faculdade", "Cursos e livros", "Pós-graduação",
                        "Material escolar", "Uniformes", "Outros"),
                grupo(DESPESA, FIXA, "Lazer", "Restaurantes", "Livros, jornais e revistas", "Games e brinquedos",
                        "Passagens", "Hospedagens", "Passeios", "Outros"),
                grupo(DESPESA, FIXA, "Outras despesas fixas", "Tarifas bancárias", "Assinaturas e streaming",
                        "Serviços digitais", "Pets", "Doações", "Imposto de renda", "Outros"),
                grupo(DESPESA, VARIAVEL, "Despesas temporárias e variáveis", "Manutenção e reparos da casa",
                        "Médicos e terapeutas esporádicos", "Manutenção do veículo", "Viagens e férias",
                        "Presentes", "Utilidades domésticas e decoração", "Material de escritório", "Correios",
                        "Outros"));
    }

    /** Empresa do provedor (PJ): impostos por alíquota sobre as notas fiscais emitidas, mais os custos fixos. */
    public static List<ModeloGrupo> pj() {
        return List.of(
                new ModeloGrupo(RECEITA, VARIAVEL, "Receitas da empresa (PJ)", false, List.of(
                        new ModeloCategoria("Notas fiscais emitidas", true, null))),
                new ModeloGrupo(DESPESA, FIXA, "Empresa (PJ)", true, List.of(
                        new ModeloCategoria("INSS", false, null, true),
                        new ModeloCategoria("DAS / Simples Nacional", false, Percentual.ZERO),
                        new ModeloCategoria("ISS", false, Percentual.ZERO),
                        new ModeloCategoria("PIS", false, Percentual.ZERO),
                        new ModeloCategoria("IRPJ", false, Percentual.ZERO),
                        new ModeloCategoria("CSLL", false, Percentual.ZERO),
                        new ModeloCategoria("Contador", false, null, true),
                        new ModeloCategoria("Pró-labore", false, null, true),
                        new ModeloCategoria("Despesas gerais da empresa", false, null))));
    }

    private static ModeloGrupo grupo(TipoMovimento tipo, Natureza natureza, String nome, String... categorias) {
        return new ModeloGrupo(tipo, natureza, nome, false,
                Arrays.stream(categorias).map(c -> new ModeloCategoria(c, false, null, RECORRENTES.contains(c))).toList());
    }
}
