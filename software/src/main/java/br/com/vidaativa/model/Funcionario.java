package br.com.vidaativa.model;

/**
 * Funcionário da academia (recepção, gerente...). Junta {@code funcionarios} (cargo, ativo) com {@code usuarios}
 * (nome, e-mail, username e perfil).
 *
 * <p>Quem usa: {@code FuncionarioDAO} e as telas de equipe do software.
 *
 * <p>Campos que não são óbvios: {@code id} é da tabela {@code funcionarios} e {@code usuarioId} é do login em
 * {@code usuarios}. {@code perfil} é {@code FUNCIONARIO} ou {@code GERENTE}: o gerente também tem uma linha na tabela
 * {@code funcionarios}, por isso aparece aqui. {@code cargo} é um texto livre (ex.: "Gerente da Academia").
 */
public record Funcionario(int id, int usuarioId, String nome, String email, String username,
                          String cargo, String perfil, boolean ativo) { }
