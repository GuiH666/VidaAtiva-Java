package br.com.vidaativa.service;

import br.com.vidaativa.model.Pagamento;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Mensagem pronta para avisar o aluno sobre uma mensalidade em atraso ou prestes a vencer, e os links para enviá-la
 * por WhatsApp ou e-mail.
 * <p>
 * É lógica pura (sem tela e sem banco), com testes em {@code TestesLogica}. Quem usa: o diálogo "Avisar aluno" da tela
 * Pagamentos ({@code AvisoCobrancaDialog}). O software não envia nada sozinho: ele só abre o WhatsApp (wa.me) ou o programa
 * de e-mail do computador com o texto já preenchido, e a recepção confere e envia.
 *
 * <p><b>Regras:</b>
 * <ul>
 *   <li>Só cobranças em aberto geram aviso: {@code ATRASADO} (inclui pendente já vencida) e {@code PENDENTE};
 *       paga ou cancelada não ({@link #podeAvisar}).</li>
 *   <li>O texto muda conforme o caso: em atraso (com os dias), vence hoje, vence amanhã ou vence em N dias.</li>
 *   <li>Telefone brasileiro: aceita DDD + número (10 ou 11 dígitos) e acrescenta o 55; aceita também o número já com 55.
 *       Qualquer outro formato não gera link de WhatsApp.</li>
 * </ul>
 */
public final class MensagemCobranca {
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Nome da academia que assina a mensagem. */
    public static final String ACADEMIA = "Academia Vida Ativa";

    private MensagemCobranca() { }

    /** Verdadeiro se a cobrança está em aberto (pendente ou atrasada) e portanto faz sentido avisar o aluno. */
    public static boolean podeAvisar(Pagamento p, LocalDate hoje) {
        String s = Indicadores.situacao(p, hoje);
        return s.equals("PENDENTE") || s.equals("ATRASADO");
    }

    /**
     * Texto do aviso, com saudação pelo primeiro nome, mês, vencimento e valor.
     *
     * @throws IllegalArgumentException se a cobrança já foi paga ou cancelada, ou se não tem vencimento
     */
    public static String texto(String nomeAluno, Pagamento p, LocalDate hoje) {
        if (!podeAvisar(p, hoje)) throw new IllegalArgumentException("Só é possível avisar cobranças em aberto (pendentes ou atrasadas).");
        if (p.vencimento() == null) throw new IllegalArgumentException("A cobrança não tem data de vencimento.");
        String mes = mesPorExtenso(p.referencia(), p.vencimento());
        String valor = moeda(p.valor());
        String venc = DATA.format(p.vencimento());
        long dias = hoje.toEpochDay() - p.vencimento().toEpochDay();   // positivo = atrasada

        String corpo;
        if (Indicadores.situacao(p, hoje).equals("ATRASADO")) {
            corpo = "Passando para lembrar que a mensalidade de " + mes + " (vencimento em " + venc + "), no valor de " + valor
                    + ", está em aberto há " + dias + (dias == 1 ? " dia" : " dias") + ".";
        } else if (dias == 0) {
            corpo = "Passando para lembrar que a mensalidade de " + mes + ", no valor de " + valor + ", vence hoje (" + venc + ").";
        } else if (dias == -1) {
            corpo = "Passando para lembrar que a mensalidade de " + mes + ", no valor de " + valor + ", vence amanhã (" + venc + ").";
        } else {
            corpo = "Passando para lembrar que a mensalidade de " + mes + ", no valor de " + valor + ", vence em " + (-dias) + " dias (" + venc + ").";
        }
        return "Olá, " + primeiroNome(nomeAluno) + "! Tudo bem? Aqui é da " + ACADEMIA + ".\n\n"
                + corpo + "\n\n"
                + "Se você já pagou, pode desconsiderar esta mensagem. Qualquer dúvida, é só responder por aqui. Obrigado!\n\n"
                + ACADEMIA;
    }

    /** Assunto do e-mail: muda entre "em aberto" (atrasada) e "lembrete de vencimento". */
    public static String assunto(Pagamento p, LocalDate hoje) {
        return (Indicadores.situacao(p, hoje).equals("ATRASADO") ? "Mensalidade em aberto" : "Lembrete de vencimento") + " - " + ACADEMIA;
    }

    /**
     * Link do WhatsApp com o texto preenchido, ou null se o telefone não for um número brasileiro válido
     * (veja as regras na descrição da classe).
     */
    public static String linkWhatsApp(String telefone, String texto) {
        String numero = numeroWhatsApp(telefone);
        return numero == null ? null : "https://wa.me/" + numero + "?text=" + codificar(texto);
    }

    /** Só dígitos com o 55 na frente (ex.: "(11) 99999-1234" vira "5511999991234"); null se não der para formar um número válido. */
    public static String numeroWhatsApp(String telefone) {
        if (telefone == null) return null;
        String d = telefone.replaceAll("\\D", "");
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) return d;
        if (d.length() == 10 || d.length() == 11) return "55" + d;
        return null;
    }

    /** Link {@code mailto:} com assunto e texto preenchidos, ou null se o e-mail estiver vazio ou sem "@". */
    public static String linkEmail(String email, String assunto, String texto) {
        if (email == null || !email.contains("@") || email.isBlank()) return null;
        return "mailto:" + email.trim() + "?subject=" + codificar(assunto) + "&body=" + codificar(texto);
    }

    // ------------------------------------------------------------------ auxiliares

    /** Codifica para URL usando %20 no lugar de "+" (alguns programas de e-mail mostram o "+" literalmente). */
    private static String codificar(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "";
        return nome.trim().split("\\s+")[0];
    }

    private static String moeda(BigDecimal v) {
        // O formato do Java usa espaço "não separável" depois do R$; troca por espaço comum para o texto ficar limpo.
        return NumberFormat.getCurrencyInstance(PT_BR).format(v == null ? BigDecimal.ZERO : v).replace(' ', ' ');
    }

    /** "2026-11" vira "novembro de 2026"; se a referência estiver fora do padrão, usa o mês do vencimento. */
    private static String mesPorExtenso(String referencia, LocalDate vencimento) {
        int ano = vencimento.getYear(), mes = vencimento.getMonthValue();
        if (referencia != null && referencia.matches("\\d{4}-\\d{2}")) {
            ano = Integer.parseInt(referencia.substring(0, 4));
            mes = Integer.parseInt(referencia.substring(5));
        }
        return Month.of(Math.max(1, Math.min(12, mes))).getDisplayName(TextStyle.FULL, PT_BR) + " de " + ano;
    }
}
