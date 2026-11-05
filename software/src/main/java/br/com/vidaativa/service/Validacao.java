package br.com.vidaativa.service;

import java.util.regex.Pattern;

/**
 * Validações simples dos campos de formulário. Todas lançam {@link IllegalArgumentException} com mensagem pronta para
 * ser mostrada ao usuário, e a maioria devolve o valor já limpo (sem espaços nas pontas, e-mail em minúsculas), então o
 * uso normal é {@code email = Validacao.email(email);}.
 *
 * <p>Quem usa: os DAOs de cadastro ({@code AlunoDAO}, {@code FuncionarioDAO}, {@code ProfessorDAO}, {@code UsuarioDAO}, {@code FichaTreinoDAO}),
 * que validam ANTES de abrir transação no banco, e a troca de senha do aluno no site (a mensagem vira o erro 400 da API).
 *
 * <p>Limites conhecidos: são checagens de formato, não de existência. O documento com 11 dígitos é tratado como CPF (confere
 * os dígitos verificadores e é guardado com máscara); qualquer outro formato (RG) só exige 5+ letras/números. Um RG que
 * tenha exatamente 11 dígitos seria tratado como CPF. O telefone só exige 10+ dígitos (DDD + número) e é devolvido como
 * foi digitado (apenas sem espaços nas pontas), sem remover pontos e traços.
 */
public final class Validacao {
    // E-mail: algo@algo.algo, sem espaços. Propositalmente simples: o formato real de e-mail é complexo demais para uma regex.
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    // Usuário: só minúsculas (a entrada é convertida antes de comparar), números, ponto, hífen e sublinhado; 3 a 30 caracteres.
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");

    private Validacao() { }

    /**
     * Garante que o campo foi preenchido (não nulo e não só espaços).
     *
     * @param campo nome do campo, usado na mensagem de erro ("Preencha o campo ...")
     * @return o valor sem espaços nas pontas
     */
    public static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.trim().isEmpty()) throw new IllegalArgumentException("Preencha o campo \"" + campo + "\".");
        return valor.trim();
    }

    /** Obrigatório, no formato de e-mail; devolve em minúsculas e sem espaços nas pontas. */
    public static String email(String valor) {
        String v = obrigatorio(valor, "E-mail").toLowerCase();
        if (!EMAIL.matcher(v).matches()) throw new IllegalArgumentException("E-mail inválido.");
        return v;
    }

    /** Usuário em minúsculas, 3 a 30 caracteres: letras, números, ponto, hífen e sublinhado. */
    public static String username(String valor) {
        String v = obrigatorio(valor, "Usuário").toLowerCase();
        if (!USERNAME.matcher(v).matches())
            throw new IllegalArgumentException("Usuário deve ter de 3 a 30 caracteres: letras minúsculas, números, ponto, hífen ou sublinhado (sem espaços).");
        return v;
    }

    /** Mínimo de 6 caracteres. Devolve a senha SEM alteração (espaços contam e não são removidos). */
    public static String senha(String valor) {
        if (valor == null || valor.length() < 6) throw new IllegalArgumentException("A senha deve ter no mínimo 6 caracteres.");
        return valor;
    }

    /**
     * Documento do aluno: CPF ou RG.
     *
     * <ul>
     *   <li><b>CPF</b> (11 dígitos, com ou sem pontos, traço e espaços): confere os dois dígitos verificadores, recusa
     *       sequências repetidas (000.000.000-00, 111.111.111-11...) e devolve SEMPRE no formato {@code 000.000.000-00}.
     *       Guardar num formato único é o que permite à chave UNIQUE do banco recusar o mesmo CPF digitado de jeitos diferentes.</li>
     *   <li><b>Qualquer outra coisa</b> (RG, que pode ter letra e tamanhos variados): exige 5+ letras/números e devolve como digitado.</li>
     * </ul>
     *
     * @return o documento pronto para gravar (CPF com máscara; RG como digitado)
     * @throws IllegalArgumentException se estiver vazio, curto demais ou for um CPF com dígitos verificadores errados
     */
    public static String documento(String valor) {
        String v = obrigatorio(valor, "Documento (CPF ou RG)");
        // Tira só os separadores usados em CPF. Se sobrarem exatamente 11 dígitos, o aluno está informando um CPF.
        String compacto = v.replaceAll("[\\s.\\-]", "");
        if (compacto.matches("\\d{11}")) {
            if (!cpfValido(compacto)) throw new IllegalArgumentException("CPF inválido. Confira os 11 dígitos.");
            return compacto.substring(0, 3) + "." + compacto.substring(3, 6) + "." + compacto.substring(6, 9) + "-" + compacto.substring(9);
        }
        // Conta só letras e números porque o documento pode ser RG (que pode ter letra).
        String digitos = v.replaceAll("[^0-9A-Za-z]", "");
        if (digitos.length() < 5) throw new IllegalArgumentException("Documento inválido.");
        return v;
    }

    /**
     * Confere os dois dígitos verificadores de um CPF (algoritmo oficial: módulo 11).
     *
     * @param cpf exatamente 11 dígitos, sem máscara
     * @return {@code false} para sequências repetidas (que passam na conta, mas não existem) e para dígitos errados
     */
    static boolean cpfValido(String cpf) {
        if (cpf.chars().distinct().count() == 1) return false;
        int d1 = digitoVerificador(cpf, 9);
        int d2 = digitoVerificador(cpf, 10);
        return d1 == cpf.charAt(9) - '0' && d2 == cpf.charAt(10) - '0';
    }

    // Soma os n primeiros dígitos com pesos (n+1) até 2, e devolve o dígito verificador correspondente (resto 10 vira 0).
    private static int digitoVerificador(String cpf, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) soma += (cpf.charAt(i) - '0') * (n + 1 - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** Obrigatório e com pelo menos 10 dígitos (DDD + número). Devolve como digitado, com a máscara que vier. */
    public static String telefone(String valor) {
        String v = obrigatorio(valor, "Telefone");
        if (v.replaceAll("\\D", "").length() < 10) throw new IllegalArgumentException("Telefone inválido. Informe DDD + número.");
        return v;
    }

    /** Para campos opcionais: converte texto vazio ou só espaços em null (vira NULL no banco); senão devolve sem espaços nas pontas. */
    public static String opcional(String valor) {
        return valor == null || valor.trim().isEmpty() ? null : valor.trim();
    }
}
