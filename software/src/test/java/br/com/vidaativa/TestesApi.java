package br.com.vidaativa;

import br.com.vidaativa.api.ApiServer;
import br.com.vidaativa.api.PortalDados;
import br.com.vidaativa.model.*;
import br.com.vidaativa.security.PasswordUtil;
import br.com.vidaativa.security.TokenService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Testa a API do site de ponta a ponta: sobe a {@code ApiServer} de verdade, numa porta livre escolhida pelo sistema, e
 * faz requisições HTTP reais contra ela. Os dados vêm de uma classe em memória ({@link DadosFalsos}), então NÃO precisa de
 * MySQL, nem do software aberto, nem de internet. Sem JUnit: é um programa com {@code main}.
 *
 * <p>Como rodar (na pasta {@code software}; o {@code rodar.sh}/{@code rodar.bat} já baixa o Gson para {@code lib/}):
 * <pre>
 *   javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name '*.java')
 *   java -cp "out:lib/*" br.com.vidaativa.TestesApi
 * </pre>
 * No Windows, troque {@code :} por {@code ;} no classpath. Cada verificação imprime "ok" ou "FALHOU"; no fim aparece o
 * total e o programa termina com código 1 se algo falhou.
 *
 * <p>O que cobre, na ordem em que roda (a ordem importa, veja o aviso em {@link #forcaBruta()}):
 * <ul>
 *   <li>{@link #publicas()}: rotas sem login, cabeçalhos CORS e de segurança, 404 e 405;</li>
 *   <li>{@link #login()}: erros de login (400/401), mesma mensagem para usuário inexistente, gerente barrado, e que nada
 *       sensível (senha/hash) vai na resposta;</li>
 *   <li>{@link #protegidas}: token falso, adulterado, expirado ou de não-aluno (401); o conteúdo de /me, fichas,
 *       pagamentos e avaliações; que um aluno NUNCA vê dados de outro; e o preço TotalPass;</li>
 *   <li>{@link #senha()}: troca de senha (erros e sucesso) e login com a senha nova;</li>
 *   <li>{@link #forcaBruta()}: bloqueio com 429 depois de várias senhas erradas.</li>
 * </ul>
 * O que NÃO cobre: a conversão do banco real (DAOs/{@code DadosBanco}) nem o limite de 8 KB do corpo (413).
 */
public class TestesApi {
    // Contadores globais; o resultado final e o código de saída dependem deles.
    private static int ok = 0, falhas = 0;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    // Endereço base da API de teste ("http://localhost:PORTA"), definido no main depois de subir o servidor.
    private static String base;

    /**
     * Banco falso: 1 aluno ativo (Pablo, id 7, usuário 70), 1 aluno de OUTRA pessoa (id 8, usuário 80).
     * Pablo é cliente convencional do plano Plus; Maria é TotalPass Premium (usada para testar o preço de upgrade).
     * Também existe o login "gerente" (perfil GERENTE), para provar que só aluno entra pela API.
     * As fichas, pagamentos e avaliações são fixos; a ficha da Maria é propositalmente devolvida quando o id pedido não é
     * o do Pablo, para o teste detectar se a API algum dia misturar alunos.
     * Os logins/senhas de teste são: pablo/123456, maria/abcdef e gerente/123456.
     */
    static class DadosFalsos implements PortalDados {
        // Não é final: a troca de senha do teste altera este valor (simula a gravação no banco).
        String senhaPablo = "123456";
        final Aluno pablo = new Aluno(7, 70, "Pablo Henrique", "pablo@email.com", "pablo", "000", "(11) 99999-9999",
                LocalDate.of(2000, 5, 10), LocalDate.of(2026, 7, 15), 2, "Plus", new BigDecimal("129.90"), "CONVENCIONAL", "PIX",
                LocalDate.of(2026, 10, 30), null, true);
        final Aluno maria = new Aluno(8, 80, "Maria Outra", "maria@email.com", "maria", "111", "(11) 98888-8888",
                null, LocalDate.of(2026, 1, 1), 3, "Premium", new BigDecimal("199.90"), "TOTALPASS", "BOLETO",
                LocalDate.of(2026, 11, 5), null, true);

        // Imita o UsuarioDAO.autenticar: devolve o usuário se login e senha conferem, senão null.
        public Usuario autenticar(String login, String senha) {
            if (login.equals("pablo") && senha.equals(senhaPablo)) return new Usuario(70, "Pablo Henrique", "pablo@email.com", "ALUNO", true);
            if (login.equals("maria") && senha.equals("abcdef")) return new Usuario(80, "Maria Outra", "maria@email.com", "ALUNO", true);
            if (login.equals("gerente") && senha.equals("123456")) return new Usuario(1, "Gerente", "g@x.com", "GERENTE", true);
            return null;
        }
        public Aluno alunoPorUsuario(int usuarioId) { return usuarioId == 70 ? pablo : usuarioId == 80 ? maria : null; }
        public List<Plano> planos() { return List.of(new Plano(1, "Básico", new BigDecimal("89.90"), "desc")); }
        public List<FichaTreino> fichasAtivas(int alunoId) {
            if (alunoId != 7) return List.of(new FichaTreino(99, 8, 1, "Maria Outra", "Prof", "FICHA DA MARIA", null, null, null, null, null, "ATIVA", List.of()));
            return List.of(new FichaTreino(1, 7, 1, "Pablo Henrique", "Professor Demo", "Hipertrofia A/B", "Hipertrofia", 4,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), "obs", "ATIVA",
                    List.of(new Treino(1, "Treino A — Peito e tríceps", "Segunda-feira", null,
                            List.of(new Exercicio(1, "Supino reto", "Peito", 4, "8-10", "60 kg", "90 s", null))))));
        }
        public List<Pagamento> pagamentos(int alunoId) {
            return List.of(new Pagamento(1, alunoId, "x", "2026-10", null, new BigDecimal("129.90"), "PIX", "PENDENTE", LocalDate.of(2026, 10, 30)));
        }
        public List<AvaliacaoFisica> avaliacoes(int alunoId) {
            return List.of(new AvaliacaoFisica(1, alunoId, 1, "x", "Professor Demo", LocalDate.of(2026, 9, 25),
                    new BigDecimal("78.50"), new BigDecimal("1.78"), new BigDecimal("18.40"), null));
        }
        // Imita as regras reais: nova senha com menos de 6 caracteres lança erro; senha atual errada devolve false.
        public boolean alterarSenha(int usuarioId, String atual, String nova) {
            if (nova.length() < 6) throw new IllegalArgumentException("A senha deve ter no mínimo 6 caracteres.");
            if (!atual.equals(senhaPablo)) return false;
            senhaPablo = nova;
            return true;
        }
    }

    /** Sobe a API, roda todos os grupos de testes, para a API e imprime o total. */
    public static void main(String[] args) throws Exception {
        TokenService tokens = new TokenService("segredo-de-teste-com-mais-de-16", 3600);
        ApiServer api = new ApiServer(new DadosFalsos(), tokens, "https://site.exemplo");
        // Porta 0 = o sistema escolhe uma porta livre, assim o teste não briga com uma API já rodando na 8080.
        int porta = api.start(0);
        base = "http://localhost:" + porta;
        try {
            publicas();
            String token = login();
            protegidas(token, tokens);
            senha();
            forcaBruta();
        } finally {
            // O finally garante que a API pare mesmo se um teste lançar exceção.
            api.stop();
        }
        System.out.println("\n" + ok + " verificações ok, " + falhas + " falha(s).");
        if (falhas > 0) System.exit(1);
    }

    // ---------- rotas públicas, CORS e erros gerais ----------
    static void publicas() throws Exception {
        var r = get("/api/status", null);
        check("status 200", r.statusCode() == 200);
        check("status diz online", json(r).get("status").getAsString().equals("online"));
        check("CORS usa a origem configurada", "https://site.exemplo".equals(r.headers().firstValue("Access-Control-Allow-Origin").orElse("")));
        check("resposta é JSON UTF-8", r.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        check("cabeçalho nosniff", "nosniff".equals(r.headers().firstValue("X-Content-Type-Options").orElse("")));

        var planos = get("/api/planos", null);
        check("planos 200 e traz acento certo", planos.statusCode() == 200 && planos.body().contains("Básico"));

        // Pré-voo: a requisição OPTIONS que o navegador faz antes de um POST/Authorization vindo de outro endereço.
        var pre = send("OPTIONS", "/api/aluno/me", null, null);
        check("pré-voo CORS responde 204", pre.statusCode() == 204);
        check("pré-voo libera Authorization", pre.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("Authorization"));

        // /api/alunos e /api/fichas já existiram como listas gerais; o teste garante que ninguém as recoloque (privacidade).
        check("rota inexistente = 404", get("/api/alunos", null).statusCode() == 404);
        check("lista geral de alunos NÃO existe mais", get("/api/alunos", null).statusCode() == 404 && get("/api/fichas", null).statusCode() == 404);
        check("método errado = 405", send("POST", "/api/planos", "{}", null).statusCode() == 405);
    }

    // ---------- login ----------
    /** Testa os erros de login e devolve o token do Pablo para os testes seguintes. */
    static String login() throws Exception {
        check("login sem corpo = 400", send("POST", "/api/login", "", null).statusCode() == 400);
        check("login com JSON inválido = 400", send("POST", "/api/login", "{oi", null).statusCode() == 400);
        check("senha errada = 401", send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"errada\"}", null).statusCode() == 401);
        check("usuário inexistente = 401 (mesma mensagem)", json(send("POST", "/api/login", "{\"login\":\"fantasma\",\"senha\":\"x\"}", null)).get("erro").getAsString().equals("Usuário ou senha inválidos."));
        check("gerente NÃO entra pela API do aluno", send("POST", "/api/login", "{\"login\":\"gerente\",\"senha\":\"123456\"}", null).statusCode() == 401);

        var ok = send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"123456\"}", null);
        check("login do aluno = 200", ok.statusCode() == 200);
        JsonObject o = json(ok);
        check("login devolve token e nome", o.has("token") && o.get("nome").getAsString().equals("Pablo Henrique"));
        check("login NÃO devolve hash/senha", !ok.body().toLowerCase().contains("senha") && !ok.body().toLowerCase().contains("hash"));
        return o.get("token").getAsString();
    }

    // ---------- rotas que exigem login ----------
    /**
     * @param token token válido do Pablo, obtido em {@link #login()}
     * @param tokens o mesmo TokenService da API (mesmo segredo), usado para fabricar tokens expirados e de outro usuário
     */
    static void protegidas(String token, TokenService tokens) throws Exception {
        // Tentativas de acesso indevido: cada uma deve receber 401.
        check("/me sem token = 401", get("/api/aluno/me", null).statusCode() == 401);
        check("/me com token falso = 401", get("/api/aluno/me", "abc.def").statusCode() == 401);
        // Troca os 3 últimos caracteres (parte da assinatura) por "AAA": a assinatura deixa de bater.
        check("/me com token adulterado = 401", get("/api/aluno/me", token.substring(0, token.length() - 3) + "AAA").statusCode() == 401);
        check("/me com token expirado = 401", get("/api/aluno/me", tokens.emitir(70, 1L)).statusCode() == 401);
        // Token legítimo e assinado, mas do usuário 1 (que não é aluno no banco falso): tem que ser recusado mesmo assim.
        check("token de usuário que não é aluno = 401", get("/api/aluno/me", tokens.emitir(1)).statusCode() == 401);

        // A partir daqui o acesso é legítimo: confere o conteúdo devolvido.
        var me = get("/api/aluno/me", token);
        check("/me = 200", me.statusCode() == 200);
        JsonObject m = json(me);
        check("/me traz nome e plano", m.get("nome").getAsString().equals("Pablo Henrique") && m.getAsJsonObject("plano").get("nome").getAsString().equals("Plus"));
        check("datas saem como AAAA-MM-DD", m.get("proximoVencimento").getAsString().equals("2026-10-30") && m.get("clienteDesde").getAsString().equals("2026-07-15"));
        check("situação do pagamento calculada", m.get("situacaoPagamento").getAsString().equals("PENDENTE"));
        check("valor da mensalidade = valor do plano (convencional)", m.get("valorMensalidade").getAsBigDecimal().compareTo(new BigDecimal("129.90")) == 0);
        // Privacidade: campos internos do model Aluno não podem aparecer no JSON do site.
        check("/me NÃO expõe documento, usuário nem id", !me.body().contains("\"documento\"") && !me.body().contains("usuarioId") && !me.body().contains("\"id\""));

        JsonArray fichas = JsonParser.parseString(get("/api/aluno/me/fichas", token).body()).getAsJsonArray();
        check("fichas: só a do próprio aluno", fichas.size() == 1 && fichas.get(0).getAsJsonObject().get("nome").getAsString().equals("Hipertrofia A/B"));
        check("ficha traz treinos e exercícios", fichas.get(0).getAsJsonObject().getAsJsonArray("treinos").get(0).getAsJsonObject()
                .getAsJsonArray("exercicios").get(0).getAsJsonObject().get("nome").getAsString().equals("Supino reto"));
        // O banco falso devolve "FICHA DA MARIA" se a API pedir fichas de outro aluno; ela não pode aparecer para o Pablo.
        check("ficha NÃO vaza ficha de outro aluno", !get("/api/aluno/me/fichas", token).body().contains("MARIA"));

        JsonArray pags = JsonParser.parseString(get("/api/aluno/me/pagamentos", token).body()).getAsJsonArray();
        check("pagamentos listados", pags.size() == 1 && pags.get(0).getAsJsonObject().get("status").getAsString().equals("PENDENTE"));

        JsonArray avs = JsonParser.parseString(get("/api/aluno/me/avaliacoes", token).body()).getAsJsonArray();
        check("avaliação com IMC calculado", avs.size() == 1 && avs.get(0).getAsJsonObject().get("imc").getAsBigDecimal().compareTo(new BigDecimal("24.8")) == 0);

        // outro aluno enxerga os próprios dados (TotalPass Premium = só o upgrade)
        String tMaria = json(send("POST", "/api/login", "{\"login\":\"maria\",\"senha\":\"abcdef\"}", null)).get("token").getAsString();
        JsonObject mm = json(get("/api/aluno/me", tMaria));
        check("outro aluno vê os dados dele", mm.get("nome").getAsString().equals("Maria Outra"));
        check("TotalPass Premium paga só o upgrade (49,90)", mm.get("valorMensalidade").getAsBigDecimal().compareTo(new BigDecimal("49.90")) == 0);
        check("Maria vê a ficha dela, não a do Pablo", get("/api/aluno/me/fichas", tMaria).body().contains("FICHA DA MARIA"));
    }

    // ---------- troca de senha ----------
    // Atenção: este teste ALTERA a senha do Pablo no banco falso (de 123456 para "novasenha"); os testes seguintes dependem disso.
    static void senha() throws Exception {
        String t = json(send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"123456\"}", null)).get("token").getAsString();
        check("trocar senha sem token = 401", send("POST", "/api/aluno/me/senha", "{}", null).statusCode() == 401);
        check("senha atual errada = 400", send("POST", "/api/aluno/me/senha", "{\"senhaAtual\":\"x\",\"novaSenha\":\"novasenha\"}", t).statusCode() == 400);
        var curta = send("POST", "/api/aluno/me/senha", "{\"senhaAtual\":\"123456\",\"novaSenha\":\"123\"}", t);
        check("senha nova curta = 400 com mensagem", curta.statusCode() == 400 && curta.body().contains("mínimo 6"));
        check("troca válida = 200", send("POST", "/api/aluno/me/senha", "{\"senhaAtual\":\"123456\",\"novaSenha\":\"novasenha\"}", t).statusCode() == 200);
        check("login com a senha nova funciona", send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"novasenha\"}", null).statusCode() == 200);
        check("login com a senha antiga falha", send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"123456\"}", null).statusCode() == 401);
    }

    // ---------- limite de tentativas de login ----------
    // Este grupo DEVE ser o último: todas as requisições do teste vêm do mesmo IP (localhost), então depois dele esse IP fica
    // bloqueado por 10 minutos e qualquer login seguinte receberia 429.
    static void forcaBruta() throws Exception {
        // O limite é de 5 falhas por IP em 10 minutos; com 8 tentativas erradas a última tem que ser barrada com 429.
        int ultimo = 0;
        for (int i = 0; i < 8; i++) ultimo = send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"chute" + i + "\"}", null).statusCode();
        check("após várias falhas o login é bloqueado (429)", ultimo == 429);
        check("mesmo com a senha certa, continua bloqueado", send("POST", "/api/login", "{\"login\":\"pablo\",\"senha\":\"novasenha\"}", null).statusCode() == 429);
    }

    // ---------- apoio ----------
    /** GET simples; {@code token} pode ser null para chamar sem login. */
    static HttpResponse<String> get(String caminho, String token) throws Exception { return send("GET", caminho, null, token); }

    /**
     * Faz uma requisição HTTP real à API de teste.
     *
     * @param corpo texto JSON a enviar, ou null para não enviar corpo
     * @param token token de login (vai em {@code Authorization: Bearer}), ou null
     */
    static HttpResponse<String> send(String metodo, String caminho, String corpo, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (corpo != null) b.header("Content-Type", "application/json");
        b.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Interpreta o corpo da resposta como um objeto JSON. */
    static JsonObject json(HttpResponse<String> r) { return JsonParser.parseString(r.body()).getAsJsonObject(); }

    /** Registra e imprime o resultado de uma verificação: "ok" se a condição for verdadeira, "FALHOU" se não. */
    static void check(String nome, boolean cond) {
        if (cond) { ok++; System.out.println("  ok   " + nome); }
        else { falhas++; System.out.println("  FALHOU  " + nome); }
    }

    @SuppressWarnings("unused") private static final String HASH = PasswordUtil.class.getName(); // garante a classe no classpath
}
