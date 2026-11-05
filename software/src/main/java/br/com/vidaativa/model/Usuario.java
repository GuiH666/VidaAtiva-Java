package br.com.vidaativa.model;

/**
 * Usuário autenticado, SEM a senha (tabela {@code usuarios}, o login de todos os perfis).
 *
 * <p>Quem usa: {@code UsuarioDAO.autenticar} devolve este record; o login do software ({@code AutenticacaoService})
 * e o login do site ({@code ApiServer}) decidem o que fazer a partir do {@code perfil}.
 *
 * <p>Campos que não são óbvios: {@code perfil} é {@code GERENTE}, {@code FUNCIONARIO}, {@code PROFESSOR} ou
 * {@code ALUNO}. Alunos só entram pelo site; os demais perfis só pelo software. O hash da senha fica de propósito
 * fora do record, para não circular pelo sistema.
 */
public record Usuario(int id, String nome, String email, String perfil, boolean ativo) { }
