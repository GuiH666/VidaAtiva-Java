package br.com.vidaativa.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configurações do sistema (banco, API e segurança), lidas UMA vez, quando a classe é carregada pela primeira vez.
 * Por isso, alterar o config.properties ou uma variável de ambiente só tem efeito depois de reiniciar o programa.
 *
 * <p>Quem usa: {@code Database} (url, usuário e senha do MySQL), {@code ApiServer} e {@code Main} (porta, início
 * automático, segredo dos tokens e origem permitida no CORS).
 *
 * Ordem de prioridade (a primeira que existir vence):
 *   1. Variáveis de ambiente (VIDAATIVA_DB_URL, VIDAATIVA_DB_USER, VIDAATIVA_DB_PASSWORD,
 *      VIDAATIVA_API_PORT, VIDAATIVA_API_SECRET, VIDAATIVA_CORS_ORIGIN, VIDAATIVA_API_AUTOSTART)
 *   2. Arquivo config.properties (na pasta onde o programa roda, em ./software ou no caminho
 *      informado em -Dvidaativa.config=...)
 *   3. Valores padrão abaixo
 *
 * A senha do banco NUNCA deve ficar escrita no código. Copie config.properties.example
 * para config.properties e preencha lá (esse arquivo está no .gitignore).
 *
 * <p>Chaves do arquivo e seus padrões: {@code db.url} (MySQL local, banco vida_ativa), {@code db.user} (root),
 * {@code db.password} (vazia), {@code api.port} (8080), {@code api.autostart} (true: a API sobe junto com o software),
 * {@code api.secret} (vazio: veja {@code TokenService}) e {@code api.cors.origin} (* = qualquer site).
 */
public final class AppConfig {
    // Carregado uma única vez (inicialização estática); todos os métodos públicos abaixo só leem este objeto.
    private static final Properties PROPS = carregar();

    private AppConfig() { }

    /**
     * Monta as propriedades na ordem: padrões, depois config.properties (se existir), depois variáveis de ambiente.
     * Se o arquivo existir mas não puder ser lido, só avisa no console e segue com os padrões.
     */
    private static Properties carregar() {
        // Os padrões vêm primeiro de propósito: assim todo getProperty abaixo sempre encontra valor, mesmo sem arquivo.
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://localhost:3306/vida_ativa?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true&connectTimeout=5000&characterEncoding=UTF-8");
        p.setProperty("db.user", "root");
        p.setProperty("db.password", "");
        p.setProperty("api.port", "8080");
        p.setProperty("api.autostart", "true");
        p.setProperty("api.secret", "");
        p.setProperty("api.cors.origin", "*");

        // Se -Dvidaativa.config foi informado, SÓ esse caminho é tentado (não cai nos locais padrão). Se o arquivo
        // não existir, nada é avisado e os padrões são usados.
        String custom = System.getProperty("vidaativa.config");
        Path[] candidatos = custom != null && !custom.isBlank()
                ? new Path[] { Path.of(custom) }
                : new Path[] { Path.of("config.properties"), Path.of("software", "config.properties") };
        for (Path c : candidatos) {
            if (Files.isRegularFile(c)) {
                try (InputStream in = Files.newInputStream(c)) {
                    // Lê explicitamente em UTF-8 (Properties.load(InputStream) usaria ISO-8859-1 e quebraria acentos).
                    Properties lido = new Properties();
                    lido.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    // putAll sobrescreve os padrões, inclusive com valor vazio (ex.: "api.secret=" no arquivo zera o padrão).
                    p.putAll(lido);
                    break;
                } catch (IOException e) {
                    System.err.println("Não foi possível ler " + c + ": " + e.getMessage());
                }
            }
        }
        // Variáveis de ambiente têm a maior prioridade (útil para servidor/CI sem deixar senha em arquivo).
        sobrescrever(p, "db.url", "VIDAATIVA_DB_URL");
        sobrescrever(p, "db.user", "VIDAATIVA_DB_USER");
        sobrescrever(p, "db.password", "VIDAATIVA_DB_PASSWORD");
        sobrescrever(p, "api.port", "VIDAATIVA_API_PORT");
        sobrescrever(p, "api.autostart", "VIDAATIVA_API_AUTOSTART");
        sobrescrever(p, "api.secret", "VIDAATIVA_API_SECRET");
        sobrescrever(p, "api.cors.origin", "VIDAATIVA_CORS_ORIGIN");
        return p;
    }

    /** Troca o valor da chave pelo da variável de ambiente, mas só se ela existir e não estiver vazia. */
    private static void sobrescrever(Properties p, String chave, String env) {
        String v = System.getenv(env);
        if (v != null && !v.isEmpty()) p.setProperty(chave, v);
    }

    // Getters: devolvem o valor já resolvido. Os textos passam por trim() para ignorar espaços acidentais no arquivo;
    // a senha do banco NÃO é aparada, pois espaço pode fazer parte dela. apiPort() lança NumberFormatException se
    // api.port não for um número.
    public static String dbUrl() { return PROPS.getProperty("db.url").trim(); }
    public static String dbUser() { return PROPS.getProperty("db.user").trim(); }
    public static String dbPassword() { return PROPS.getProperty("db.password"); }
    public static int apiPort() { return Integer.parseInt(PROPS.getProperty("api.port").trim()); }
    public static boolean apiAutostart() { return Boolean.parseBoolean(PROPS.getProperty("api.autostart").trim()); }
    public static String apiSecret() { return PROPS.getProperty("api.secret").trim(); }
    public static String corsOrigin() { return PROPS.getProperty("api.cors.origin").trim(); }
}
