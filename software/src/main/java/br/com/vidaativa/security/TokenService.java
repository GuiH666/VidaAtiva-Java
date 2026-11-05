package br.com.vidaativa.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Token de sessão assinado (HMAC-SHA256) usado pela API do site do aluno. É o que o aluno recebe no login e envia
 * nas próximas chamadas, no cabeçalho {@code Authorization: Bearer <token>}.
 *
 * <p>Quem usa: {@code ApiServer} ({@code emitir} no login, {@code validar} em toda rota que exige login) e os testes.
 *
 * <p>Formato: {@code base64url("usuarioId|expiraEmEpochSegundos") + "." + base64url(assinatura)}.
 * A primeira parte (payload) só diz QUEM é o usuário e QUANDO o token expira, e não é secreta (qualquer um consegue
 * decodificar o Base64). A segurança está na assinatura: HMAC-SHA256 do payload com o segredo do servidor. Sem o
 * segredo ninguém consegue forjar um token nem trocar o id ou a data dentro de um token existente, pois a assinatura
 * deixaria de bater. A API usa validade de 8 horas.
 *
 * <p>Pontos de atenção:
 * <ul>
 *   <li>O token é "sem estado": o servidor não guarda lista de tokens, então não há como revogar um token individual
 *       (nem ao trocar a senha ou "sair"); ele vale até expirar ou até o segredo mudar.</li>
 *   <li>Segredo ({@code api.secret}) vazio ou com menos de 16 caracteres: o construtor gera um segredo aleatório que dura
 *       só enquanto o programa roda. Funciona, mas todos os logins do site caem a cada reinicialização.</li>
 * </ul>
 */
public final class TokenService {
    private final byte[] segredo;
    private final long validadeSegundos;

    /**
     * @param segredo texto secreto usado para assinar (use 16+ caracteres; veja {@code api.secret} no config.properties)
     * @param validadeSegundos por quantos segundos um token emitido por {@link #emitir(int)} continua válido
     */
    public TokenService(String segredo, long validadeSegundos) {
        if (segredo == null || segredo.length() < 16) {
            byte[] aleatorio = new byte[32];
            new SecureRandom().nextBytes(aleatorio);
            this.segredo = aleatorio;
            System.err.println("[API] api.secret não configurado (ou curto demais): usando um segredo aleatório. "
                    + "Os logins do site serão invalidados quando a API reiniciar. Defina api.secret no config.properties.");
        } else {
            this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        }
        this.validadeSegundos = validadeSegundos;
    }

    public long validadeSegundos() { return validadeSegundos; }

    /** Emite um token para o usuário, válido a partir de agora por {@link #validadeSegundos()} segundos. */
    public String emitir(int usuarioId) {
        return emitir(usuarioId, Instant.now().getEpochSecond() + validadeSegundos);
    }

    /**
     * Emite um token com data de expiração explícita. Existe principalmente para os testes criarem tokens já expirados.
     *
     * @param expiraEm instante de expiração, em segundos desde 1970 (epoch); depois dele o token é recusado
     */
    public String emitir(int usuarioId, long expiraEm) {
        String payload = b64(usuarioId + "|" + expiraEm);
        return payload + "." + assinar(payload);
    }

    /**
     * Valida o token e devolve o id do usuário (da tabela {@code usuarios}).
     * Primeiro confere a assinatura e só depois lê o conteúdo; qualquer defeito (nulo, sem ponto, assinatura que não
     * bate, Base64 ou números ilegíveis, expirado) devolve -1 em vez de lançar exceção.
     *
     * @return o id do usuário, ou -1 se o token for inválido ou estiver expirado
     */
    public int validar(String token) {
        if (token == null) return -1;
        int ponto = token.indexOf('.');
        if (ponto <= 0 || ponto == token.length() - 1) return -1;
        String payload = token.substring(0, ponto);
        String assinatura = token.substring(ponto + 1);
        // Recalcula a assinatura esperada com o NOSSO segredo e compara em tempo constante (MessageDigest.isEqual), para
        // o tempo de resposta não revelar quantos caracteres da assinatura o atacante acertou. Só depois de a assinatura
        // bater é que o payload é decodificado: nunca se confia no conteúdo de um token não verificado.
        byte[] esperado = assinar(payload).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperado, assinatura.getBytes(StandardCharsets.UTF_8))) return -1;
        try {
            String texto = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            String[] partes = texto.split("\\|");
            if (partes.length != 2) return -1;
            long expira = Long.parseLong(partes[1]);
            if (Instant.now().getEpochSecond() > expira) return -1;
            return Integer.parseInt(partes[0]);
        } catch (IllegalArgumentException e) {
            // Inclui NumberFormatException e Base64 inválido (ambas são IllegalArgumentException).
            return -1;
        }
    }

    /** HMAC-SHA256 do payload, codificado em Base64 "url-safe" sem o preenchimento "=". */
    private String assinar(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC não disponível", e);
        }
    }

    /** Base64 "url-safe" sem preenchimento: só usa letras, números, '-' e '_', então não atrapalha o separador '.' do token. */
    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
