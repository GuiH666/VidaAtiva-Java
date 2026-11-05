package br.com.vidaativa.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de senhas: transforma a senha digitada em um texto que pode ser gravado em {@code usuarios.senha_hash} sem
 * que ninguém consiga recuperar a senha original. A senha em texto NUNCA vai para o banco.
 *
 * <p>Quem usa: {@code UsuarioDAO} (grava o hash ao criar usuário/trocar senha e confere no login), e os testes.
 * Também pode ser executada na linha de comando para gerar um hash (veja {@link #main}), útil para escrever o seed.sql.
 *
 * <p>Formato novo, o único que esta classe GERA:
 * <pre>pbkdf2$ITERACOES$SALT_BASE64$HASH_BASE64</pre>
 * Exemplo: {@code pbkdf2$120000$bVfz...==$WK+I...=}. São quatro partes separadas por {@code $}: o prefixo fixo
 * {@code pbkdf2}, quantas iterações foram usadas (120.000 hoje), o salt aleatório de 16 bytes e o hash de 32 bytes,
 * ambos em Base64. O algoritmo é PBKDF2-HMAC-SHA256. O salt é novo a cada senha, então duas pessoas com a mesma senha
 * têm hashes diferentes. O número de iterações fica GRAVADO no próprio hash: por isso dá para aumentar a constante
 * {@code ITERACOES} no futuro sem invalidar as senhas já gravadas (a conferência lê as iterações do hash guardado).
 *
 * <p>Formato antigo (de versões anteriores do sistema): SHA-256 puro, em hexadecimal (64 caracteres), sem salt.
 * Ele ainda é ACEITO na conferência, para ninguém perder o acesso, e é migrado de forma silenciosa: no primeiro login
 * bem-sucedido, {@code UsuarioDAO.autenticar} pergunta {@link #precisaAtualizar} e, se for o formato antigo, regrava a
 * senha no formato novo (é o único momento em que se tem a senha em texto para recalcular o hash).
 * Hashes que já estão no formato novo NÃO são regravados, mesmo que tenham menos iterações que o valor atual.
 */
public final class PasswordUtil {
    // O prefixo é o que diferencia o formato novo do antigo (SHA-256 em hexa nunca contém o caractere "$").
    private static final String PREFIXO = "pbkdf2";
    private static final int ITERACOES = 120_000;
    // Tamanhos em bytes. O hash de 32 bytes = 256 bits, o mesmo tamanho da saída do HMAC-SHA256.
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_HASH = 32;
    // SecureRandom (e não Random) porque o salt precisa ser imprevisível; a instância é reaproveitada, é thread-safe.
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() { }

    /**
     * Gera o hash seguro (com salt) para gravar no banco, no formato {@code pbkdf2$iterações$salt$hash}.
     * O resultado tem bem menos de 255 caracteres, cabendo em {@code senha_hash VARCHAR(255)}.
     */
    public static String hash(String senha) {
        byte[] salt = new byte[TAMANHO_SALT];
        RANDOM.nextBytes(salt);
        byte[] h = pbkdf2(senha, salt, ITERACOES);
        return PREFIXO + "$" + ITERACOES + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(h);
    }

    /**
     * Confere a senha digitada contra o hash gravado (novo ou antigo).
     * Nunca lança exceção por causa de um hash malformado: nesse caso apenas devolve {@code false}.
     *
     * @param senha senha em texto, como digitada
     * @param armazenado valor da coluna {@code senha_hash}
     * @return {@code true} se conferem; {@code false} se não conferem, se algum argumento for nulo ou se o hash estiver corrompido
     */
    public static boolean verificar(String senha, String armazenado) {
        if (senha == null || armazenado == null) return false;
        if (armazenado.startsWith(PREFIXO + "$")) {
            String[] partes = armazenado.split("\\$");
            if (partes.length != 4) return false;
            try {
                int it = Integer.parseInt(partes[1]);
                byte[] salt = Base64.getDecoder().decode(partes[2]);
                byte[] esperado = Base64.getDecoder().decode(partes[3]);
                // MessageDigest.isEqual compara em tempo constante: um == comum para no primeiro byte diferente, e o
                // tempo de resposta poderia revelar, byte a byte, quanto do hash o atacante acertou.
                return MessageDigest.isEqual(esperado, pbkdf2(senha, salt, it));
            } catch (IllegalArgumentException e) {
                // Base64 inválido ou número de iterações ilegível/inválido: hash corrompido, trata como senha errada.
                return false;
            }
        }
        // Formato antigo: recalcula o SHA-256 da senha digitada e compara com o gravado. O toLowerCase() torna a
        // comparação indiferente a maiúsculas/minúsculas, caso o hexa tenha sido gravado em maiúsculas.
        return MessageDigest.isEqual(
                sha256(senha).getBytes(StandardCharsets.UTF_8),
                armazenado.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * True se o hash ainda está no formato antigo (SHA-256 puro) e deve ser regravado no formato novo.
     * Só olha o prefixo {@code pbkdf2$}; não compara o número de iterações. Hash nulo devolve {@code false}.
     */
    public static boolean precisaAtualizar(String armazenado) {
        return armazenado != null && !armazenado.startsWith(PREFIXO + "$");
    }

    /**
     * SHA-256 puro, em hexadecimal minúsculo (64 caracteres). NÃO use para gravar senhas novas (sem salt e rápido
     * demais, fácil de quebrar por força bruta); existe só para reconhecer senhas do formato antigo e para os testes.
     */
    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    /** Calcula o PBKDF2-HMAC-SHA256 da senha com o salt e o número de iterações dados; devolve os 32 bytes do hash. */
    private static byte[] pbkdf2(String senha, byte[] salt, int iteracoes) {
        try {
            // O 4º argumento do PBEKeySpec é o tamanho da chave em BITS, daí a multiplicação por 8.
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, TAMANHO_HASH * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 não disponível", e);
        }
    }

    /**
     * Utilitário de linha de comando: imprime o hash da senha recebida (útil para criar usuários direto em SQL).
     * Uso: {@code java -cp target/classes br.com.vidaativa.security.PasswordUtil minhaSenha}
     */
    public static void main(String[] args) {
        if (args.length != 1) { System.out.println("Uso: PasswordUtil <senha>"); return; }
        System.out.println(hash(args[0]));
    }
}
