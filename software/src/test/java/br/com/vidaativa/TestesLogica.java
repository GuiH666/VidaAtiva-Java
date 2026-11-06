package br.com.vidaativa;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.service.BibliotecaExercicios;
import br.com.vidaativa.service.Indicadores;
import br.com.vidaativa.model.AvaliacaoFisica;
import br.com.vidaativa.security.PasswordUtil;
import br.com.vidaativa.security.TokenService;
import br.com.vidaativa.service.Precificacao;
import br.com.vidaativa.service.Validacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Testes da lógica "pura" do sistema, a que NÃO depende de banco de dados, de tela nem de rede. Não precisa de JUnit nem
 * de MySQL: é um programa comum com {@code main}, com um mini-framework próprio no fim do arquivo.
 *
 * <p>Como rodar (na pasta {@code software}; o {@code rodar.sh}/{@code rodar.bat} já baixa as bibliotecas para {@code lib/}):
 * <pre>
 *   javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name '*.java')
 *   java -cp "out:lib/*" br.com.vidaativa.TestesLogica
 * </pre>
 * No Windows, troque {@code :} por {@code ;} no classpath (e liste os arquivos .java de outro jeito, ou rode pela IDE).
 * O {@code -cp "lib/*"} no javac é necessário porque o comando compila o projeto inteiro, e a API usa o Gson.
 * A saída mostra uma linha por verificação ("ok" ou "FALHOU") e um total no fim. O programa termina com código 1 se
 * algum teste falhar (assim dá para usar em automação).
 *
 * <p>O que cobre, na ordem em que roda:
 * <ul>
 *   <li>{@link #senhas()}: hash PBKDF2 de {@code PasswordUtil} (formato, salt, SHA-256 antigo e migração, hash inválido);</li>
 *   <li>{@link #tokens()}: token assinado de {@code TokenService} (válido, adulterado, outro segredo, expirado);</li>
 *   <li>{@link #precos()}: regras de {@code Precificacao} (convencional, TotalPass, o que cada plano inclui);</li>
 *   <li>{@link #validacoes()}: validações de formulário de {@code Validacao};</li>
 *   <li>{@link #vencimentos()}: cálculo do próximo vencimento em {@code AlunoDAO.proximoVencimento} (método estático, não abre conexão);</li>
 *   <li>{@link #imc()}: cálculo do IMC em {@code AvaliacaoFisica};</li>
 *   <li>{@link #painelGerente()}: números do painel do gerente em {@code Indicadores.calcular};</li>
 *   <li>{@link #biblioteca()}: busca e filtro da biblioteca de exercícios da ficha de treino.</li>
 * </ul>
 * O que NÃO cobre: DAOs (precisam do MySQL), telas Swing e as rotas HTTP (essas estão em {@code TestesApi}).
 *
 * <p>Para acrescentar um teste: crie ou use um método {@code static void}, use {@link #check} (condição verdadeira) ou
 * {@link #lanca} (deve lançar IllegalArgumentException) e chame o método no {@code main}.
 */
public class TestesLogica {
    // Contadores globais; o resultado final e o código de saída dependem deles.
    private static int ok = 0, falhas = 0;

    public static void main(String[] args) throws Exception {
        senhas();
        tokens();
        precos();
        validacoes();
        vencimentos();
        imc();
        painelGerente();
        biblioteca();
        System.out.println("\n" + ok + " verificações ok, " + falhas + " falha(s).");
        if (falhas > 0) System.exit(1);
    }

    // ---------- PasswordUtil ----------
    static void senhas() {
        String h = PasswordUtil.hash("123456");
        check("hash novo tem prefixo pbkdf2", h.startsWith("pbkdf2$"));
        check("hash cabe em VARCHAR(255)", h.length() < 255);
        check("senha certa confere", PasswordUtil.verificar("123456", h));
        check("senha errada não confere", !PasswordUtil.verificar("1234567", h));
        check("dois hashes da mesma senha são diferentes (salt)", !h.equals(PasswordUtil.hash("123456")));
        // Simula um usuário antigo, cuja senha foi gravada como SHA-256 puro: precisa continuar entrando e ser marcado para migrar.
        String antigo = PasswordUtil.sha256("123456");
        check("SHA-256 antigo ainda confere", PasswordUtil.verificar("123456", antigo));
        check("SHA-256 antigo pede atualização", PasswordUtil.precisaAtualizar(antigo));
        check("hash novo não pede atualização", !PasswordUtil.precisaAtualizar(h));
        // Garante que o hash gravado em database/seed.sql continua válido (se mudar o formato, o seed precisa ser refeito).
        check("hash do seed.sql confere com 123456",
                PasswordUtil.verificar("123456", "pbkdf2$120000$bVfzWX/sFvm1HfpeGf7Nlg==$WK+ITfb3bP00cYF9Ua7rkvj3RglDWZvmD5tuX6oBnxc="));
        // Hash corrompido deve dar "senha errada" (false), nunca derrubar o login com uma exceção.
        check("hash lixo é rejeitado sem estourar", !PasswordUtil.verificar("x", "pbkdf2$abc"));
        check("senha nula é rejeitada", !PasswordUtil.verificar(null, h));
    }

    // ---------- TokenService ----------
    static void tokens() {
        TokenService t = new TokenService("segredo-de-teste-com-mais-de-16", 3600);
        String tok = t.emitir(42);
        check("token válido devolve o id", t.validar(tok) == 42);
        check("token adulterado é rejeitado", t.validar(tok.substring(0, tok.length() - 2) + "xx") == -1);
        check("token de outro segredo é rejeitado", new TokenService("outro-segredo-bem-diferente", 3600).validar(tok) == -1);
        // emitir(id, 1L) cria um token que expirou em 1970 (segundo 1 do calendário epoch).
        check("token expirado é rejeitado", t.validar(t.emitir(42, 1L)) == -1);
        check("token vazio/nulo é rejeitado", t.validar(null) == -1 && t.validar("") == -1 && t.validar("abc") == -1 && t.validar(".") == -1);
        // Ataque simulado: troca o conteúdo (usuário 1, validade enorme) mas mantém a assinatura antiga; a assinatura não pode bater.
        String payloadTrocado = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("1|9999999999".getBytes()) + tok.substring(tok.indexOf('.'));
        check("trocar o id no token invalida a assinatura", t.validar(payloadTrocado) == -1);
    }

    // ---------- Precificacao ----------
    static void precos() {
        // Valores de tabela dos três planos, iguais aos do schema.sql.
        BigDecimal basico = new BigDecimal("89.90"), plus = new BigDecimal("129.90"), premium = new BigDecimal("199.90");
        check("convencional paga o valor do plano", Precificacao.valorMensalidade("Plus", plus, "CONVENCIONAL").compareTo(plus) == 0);
        check("TotalPass Básico = 0", Precificacao.valorMensalidade("Básico", basico, "TOTALPASS").signum() == 0);
        // O nome do plano é normalizado (sem acento, sem diferença de maiúsculas), pois pode vir digitado de formas diferentes.
        check("TotalPass Basico (sem acento) = 0", Precificacao.valorMensalidade("basico", basico, "TOTALPASS").signum() == 0);
        check("TotalPass Plus = upgrade 29,90", Precificacao.valorMensalidade("Plus", plus, "TOTALPASS").compareTo(new BigDecimal("29.90")) == 0);
        check("TotalPass Premium = upgrade 49,90", Precificacao.valorMensalidade("Premium", premium, "TOTALPASS").compareTo(new BigDecimal("49.90")) == 0);
        check("plano Básico não inclui ficha", !Precificacao.incluiFicha("Básico"));
        check("plano Plus inclui ficha", Precificacao.incluiFicha("Plus"));
        check("só Premium inclui avaliação", Precificacao.incluiAvaliacao("Premium") && !Precificacao.incluiAvaliacao("Plus"));
    }

    // ---------- Validacao ----------
    static void validacoes() {
        // " Ana@X.com " deve virar "ana@x.com": a validação também limpa o valor (tira espaços e põe em minúsculas).
        check("e-mail válido", "ana@x.com".equals(Validacao.email(" Ana@X.com ")));
        lanca("e-mail sem @", () -> Validacao.email("ana.x.com"));
        check("usuário válido", "joao.silva".equals(Validacao.username("Joao.Silva")));
        // Daqui em diante, "lanca" verifica que a entrada inválida gera IllegalArgumentException com mensagem para o usuário.
        lanca("usuário com espaço", () -> Validacao.username("joao silva"));
        lanca("usuário curto", () -> Validacao.username("jo"));
        lanca("senha curta", () -> Validacao.senha("123"));
        check("senha de 6 ok", "123456".equals(Validacao.senha("123456")));
        lanca("telefone sem DDD", () -> Validacao.telefone("99999-9999"));
        check("telefone com DDD", "(11) 99999-9999".equals(Validacao.telefone("(11) 99999-9999")));
        lanca("campo obrigatório vazio", () -> Validacao.obrigatorio("  ", "Nome"));
        lanca("documento curto", () -> Validacao.documento("12"));

        // --- CPF (requisitos RF001 / RS001 / DAD01: "CPF válido e único") ---
        // Um documento com 11 dígitos é tratado como CPF: confere os dígitos verificadores e guarda sempre com máscara.
        check("CPF válido com máscara é mantido", "529.982.247-25".equals(Validacao.documento("529.982.247-25")));
        check("CPF válido só com números ganha máscara", "529.982.247-25".equals(Validacao.documento("52998224725")));
        // É isso que faz a chave UNIQUE do banco pegar o CPF repetido, não importa como o funcionário digitou.
        check("mesmo CPF digitado de formas diferentes vira o mesmo valor",
                Validacao.documento(" 529.982.247-25 ").equals(Validacao.documento("52998224725"))
                        && Validacao.documento("529 982 247 25").equals(Validacao.documento("529.982.247-25")));
        check("outro CPF válido", "111.444.777-35".equals(Validacao.documento("11144477735")));
        lanca("CPF com dígito verificador errado", () -> Validacao.documento("529.982.247-24"));
        lanca("CPF com todos os dígitos iguais (000...)", () -> Validacao.documento("000.000.000-00"));
        lanca("CPF com todos os dígitos iguais (111...)", () -> Validacao.documento("111.111.111-11"));
        // RG pode ter letra e tamanho diferente: continua aceito exatamente como foi digitado.
        check("RG com letra continua aceito como digitado", "12.345.678-X".equals(Validacao.documento("12.345.678-X")));
    }

    // ---------- AlunoDAO.proximoVencimento ----------
    // A data de "hoje" é passada por parâmetro (12/10/2026) para o teste dar sempre o mesmo resultado, em qualquer dia.
    static void vencimentos() {
        LocalDate hoje = LocalDate.of(2026, 10, 12);
        check("dia 20 ainda este mês", AlunoDAO.proximoVencimento(20, hoje).equals(LocalDate.of(2026, 10, 20)));
        check("dia 5 já passou: vai pro mês que vem", AlunoDAO.proximoVencimento(5, hoje).equals(LocalDate.of(2026, 11, 5)));
        check("dia igual a hoje vai pro mês que vem", AlunoDAO.proximoVencimento(12, hoje).equals(LocalDate.of(2026, 11, 12)));
        check("dezembro vira janeiro", AlunoDAO.proximoVencimento(5, LocalDate.of(2026, 12, 20)).equals(LocalDate.of(2027, 1, 5)));
        check("dia 30 em fevereiro ajusta para 28", AlunoDAO.proximoVencimento(30, LocalDate.of(2027, 2, 1)).equals(LocalDate.of(2027, 2, 28)));
    }

    // ---------- AvaliacaoFisica.imc ----------
    static void imc() {
        // Parâmetros do record: id, alunoId, professorId, nomes, data, peso (kg), altura (m), % gordura, observações.
        AvaliacaoFisica v = new AvaliacaoFisica(1, 1, 1, "A", "P", LocalDate.now(), new BigDecimal("78.50"), new BigDecimal("1.78"), null, null);
        check("IMC 78,5kg / 1,78m = 24,8", v.imc().compareTo(new BigDecimal("24.8")) == 0);
        check("IMC sem altura é null", new AvaliacaoFisica(1, 1, 1, "A", "P", LocalDate.now(), new BigDecimal("70"), null, null, null).imc() == null);
    }

    // ---------- Indicadores (painel do gerente) ----------
    private static Aluno aluno(int id, String nome, String plano, String tipo, LocalDate inicio, boolean ativo) {
        return new Aluno(id, id, nome, nome + "@x.com", nome.toLowerCase(), "52998224725", "(11) 99999-0000", null, inicio,
                1, plano, new BigDecimal("100.00"), tipo, "PIX", null, null, ativo);
    }

    private static Pagamento cobranca(int id, int alunoId, String nome, String ref, LocalDate data, String valor, String forma, String status, LocalDate venc) {
        return new Pagamento(id, alunoId, nome, ref, data, new BigDecimal(valor), forma, status, venc);
    }

    static void painelGerente() {
        System.out.println("\n[Indicadores do painel do gerente]");
        LocalDate hoje = LocalDate.of(2026, 11, 5);
        List<Aluno> alunos = List.of(
                aluno(1, "Ana", "Plus", "CONVENCIONAL", LocalDate.of(2026, 11, 5), true),
                aluno(2, "Bruno", "Básico", "TOTALPASS", LocalDate.of(2026, 11, 1), true),
                aluno(3, "Carla", "Premium", "CONVENCIONAL", LocalDate.of(2026, 10, 20), false),
                aluno(4, "Davi", "Plus", "CONVENCIONAL", LocalDate.of(2026, 10, 31), true),
                aluno(5, "Elisa", "Plus", "CONVENCIONAL", LocalDate.of(2026, 11, 5), true));
        List<Pagamento> pagamentos = List.of(
                cobranca(1, 1, "Ana", "2026-11", LocalDate.of(2026, 11, 3), "100.00", "PIX", "PAGO", LocalDate.of(2026, 11, 10)),
                cobranca(2, 2, "Bruno", "2026-11", null, "50.00", null, "PENDENTE", LocalDate.of(2026, 11, 8)),
                cobranca(3, 4, "Davi", "2026-11", null, "80.00", null, "PENDENTE", LocalDate.of(2026, 11, 2)),      // vencida: conta como atrasada
                cobranca(4, 3, "Carla", "2026-10", null, "120.00", null, "ATRASADO", LocalDate.of(2026, 10, 10)),
                cobranca(5, 1, "Ana", "2026-10", LocalDate.of(2026, 11, 4), "100.00", "CARTAO", "PAGO", LocalDate.of(2026, 10, 10)), // paga em novembro, referente a outubro
                cobranca(6, 5, "Elisa", "2026-11", null, "90.00", null, "CANCELADO", LocalDate.of(2026, 11, 10)),
                cobranca(7, 2, "Bruno", "2026-10", LocalDate.of(2026, 10, 12), "50.00", "PIX", "PAGO", LocalDate.of(2026, 10, 10)));

        Indicadores.Resultado r = Indicadores.calcular(YearMonth.of(2026, 11), hoje, alunos, pagamentos);
        check("alunos ativos = 4 e inativos = 1", r.alunosAtivos() == 4 && r.alunosInativos() == 1);
        check("cadastros hoje = 2 (Ana e Elisa)", r.cadastrosHoje() == 2);
        check("cadastros no mês = 3 e no mês anterior = 2 (inclui o aluno inativo)", r.cadastrosMes() == 3 && r.cadastrosMesAnterior() == 2);
        check("cadastros por dia: dia 1 = 1, dia 5 = 2, 30 posições", r.cadastrosPorDia().length == 30 && r.cadastrosPorDia()[0] == 1 && r.cadastrosPorDia()[4] == 2);
        check("alunos por plano: Plus 3, Básico 1 (só ativos), Plus primeiro", r.alunosPorPlano().get("Plus") == 3 && r.alunosPorPlano().get("Básico") == 1
                && r.alunosPorPlano().keySet().iterator().next().equals("Plus") && !r.alunosPorPlano().containsKey("Premium"));
        check("TotalPass = 1 e convencional = 3", r.totalPass() == 1 && r.convencional() == 3);
        check("cobranças do mês: 1 paga, 1 pendente, 1 atrasada, 1 cancelada",
                r.cobrancasPagas() == 1 && r.cobrancasPendentes() == 1 && r.cobrancasAtrasadas() == 1 && r.cobrancasCanceladas() == 1);
        check("recebido no mês = 200 (regime de caixa: inclui outubro pago em novembro)", r.recebidoMes().compareTo(new BigDecimal("200.00")) == 0);
        check("recebido no mês anterior = 50", r.recebidoMesAnterior().compareTo(new BigDecimal("50.00")) == 0);
        check("recebido hoje = 0", r.recebidoHoje().signum() == 0);
        check("a receber do mês = 50 (só pendente ainda não vencida)", r.aReceberMes().compareTo(new BigDecimal("50.00")) == 0);
        check("atrasado do mês = 80 e atrasado total = 200 (todos os meses)", r.atrasadoMes().compareTo(new BigDecimal("80.00")) == 0
                && r.atrasadoTotal().compareTo(new BigDecimal("200.00")) == 0);
        check("2 alunos em atraso (Davi e Carla)", r.alunosEmAtraso() == 2);
        check("adimplência do mês = 1 de 3 = 33,3% (cancelada fora)", Math.abs(r.adimplencia() - 33.333) < 0.01);
        check("recebido por forma: Pix 100 e Cartão 100", r.recebidoPorForma().get("PIX").compareTo(new BigDecimal("100.00")) == 0
                && r.recebidoPorForma().get("CARTAO").compareTo(new BigDecimal("100.00")) == 0);
        check("lista de atrasos: a mais antiga primeiro (Carla 26 dias, depois Davi 3 dias)", r.atrasos().size() == 2
                && r.atrasos().get(0).aluno().equals("Carla") && r.atrasos().get(0).diasAtraso() == 26 && r.atrasos().get(1).diasAtraso() == 3);
        check("vencem em 7 dias: só a do Bruno (a do Davi já está atrasada)", r.vencemEmBreve().size() == 1 && r.vencemEmBreve().get(0).alunoNome().equals("Bruno"));
        check("últimos 6 meses terminam no mês escolhido, com recebido 200 e em aberto 130",
                r.ultimosMeses().size() == 6 && r.ultimosMeses().get(5).mes().equals(YearMonth.of(2026, 11))
                        && r.ultimosMeses().get(5).recebido().compareTo(new BigDecimal("200.00")) == 0
                        && r.ultimosMeses().get(5).emAberto().compareTo(new BigDecimal("130.00")) == 0);
        check("cadastros recentes: os mais novos primeiro", r.cadastrosRecentes().get(0).dataInicio().equals(LocalDate.of(2026, 11, 5)));

        Indicadores.Resultado out = Indicadores.calcular(YearMonth.of(2026, 10), hoje, alunos, pagamentos);
        check("mês de outubro: recebido 50, atrasada a de Carla (120), cadastros 2", out.recebidoMes().compareTo(new BigDecimal("50.00")) == 0
                && out.atrasadoMes().compareTo(new BigDecimal("120.00")) == 0 && out.cadastrosMes() == 2 && out.cadastrosHoje() == 2);

        Indicadores.Resultado vazio = Indicadores.calcular(YearMonth.of(2026, 1), hoje, alunos, pagamentos);
        check("mês sem cobranças: adimplência indefinida (NaN) e 31 dias no gráfico", Double.isNaN(vazio.adimplencia()) && vazio.cadastrosPorDia().length == 31);
        check("sem dados nenhum: não quebra", Indicadores.calcular(YearMonth.of(2026, 11), hoje, List.of(), List.of()).alunosAtivos() == 0);
        check("variação: 200 sobre 50 = +300%; sem base = NaN", Indicadores.Resultado.variacao(200, 50) == 300.0 && Double.isNaN(Indicadores.Resultado.variacao(5, 0)));
        check("situação: pendente vencida vira ATRASADO; pendente em dia continua", Indicadores.situacao(pagamentos.get(2), hoje).equals("ATRASADO")
                && Indicadores.situacao(pagamentos.get(1), hoje).equals("PENDENTE"));
    }

    // ---------- Biblioteca de exercícios ----------
    static void biblioteca() {
        System.out.println("\n[Biblioteca de exercícios]");
        check("sem filtro devolve o catálogo todo", BibliotecaExercicios.filtrar(null, "").size() == BibliotecaExercicios.LISTA.size());
        check("filtro por grupo só traz o grupo", BibliotecaExercicios.filtrar("Peito", "").stream().allMatch(e -> e.grupo().equals("Peito"))
                && !BibliotecaExercicios.filtrar("Peito", "").isEmpty());
        check("busca ignora acento e maiúscula (\"TRICEPS\" acha Tríceps)", !BibliotecaExercicios.filtrar(null, "TRICEPS").isEmpty());
        check("busca sem resultado devolve lista vazia", BibliotecaExercicios.filtrar(null, "xyzxyz").isEmpty());
        check("buscar por nome exato acha o exercício", BibliotecaExercicios.buscar("supino reto com barra").isPresent());
        boolean modelosOk = true;
        for (var m : BibliotecaExercicios.MODELOS)
            for (var t : m.treinos()) for (String n : t.exercicios()) if (BibliotecaExercicios.buscar(n).isEmpty()) { modelosOk = false; System.out.println("    exercício do modelo fora do catálogo: " + n); }
        check("todo exercício dos modelos de ficha existe no catálogo", modelosOk);
    }

    // ---------- mini-framework ----------
    /** Registra e imprime o resultado de uma verificação: "ok" se a condição for verdadeira, "FALHOU" se não. */
    private static void check(String nome, boolean condicao) {
        if (condicao) { ok++; System.out.println("  ok   " + nome); }
        else { falhas++; System.out.println("  FALHOU  " + nome); }
    }

    /** Passa se executar {@code r} lançar IllegalArgumentException; falha se terminar sem erro. Imprime a mensagem do erro. */
    private static void lanca(String nome, Runnable r) {
        try { r.run(); falhas++; System.out.println("  FALHOU  (deveria lançar erro) " + nome); }
        catch (IllegalArgumentException e) { ok++; System.out.println("  ok   " + nome + " -> \"" + e.getMessage() + "\""); }
    }
}
