package br.com.vidaativa.service;

import br.com.vidaativa.dao.UsuarioDAO;
import br.com.vidaativa.model.Usuario;

import java.sql.SQLException;

/**
 * Serviço de login do SOFTWARE desktop (Swing). Fica entre a tela de login e o {@code UsuarioDAO}, que faz a conferência
 * da senha de verdade (hash PBKDF2, usuário ativo e migração do hash antigo, veja {@code PasswordUtil}).
 *
 * <p>Quem usa: {@code LoginFrame}. O login do site do aluno NÃO passa por aqui: a {@code ApiServer} chama
 * {@code PortalDados.autenticar} direto.
 *
 * <p>Regra importante: perfil ALUNO não entra no software; a área do aluno é o site. Já os perfis GERENTE, FUNCIONARIO e
 * PROFESSOR entram, e a tela decide o que cada um enxerga.
 */
public class AutenticacaoService {
    private final UsuarioDAO dao = new UsuarioDAO();

    /**
     * Retorna o usuário se login e senha conferem; null se estiverem errados. Lança SQLException se o banco estiver fora do ar.
     * Atenção: aceita qualquer perfil, inclusive ALUNO. Quem chama deve checar {@link #perfilPodeUsarSoftware} em seguida.
     *
     * @param login e-mail ou username
     */
    public Usuario login(String login, String senha) throws SQLException {
        return dao.autenticar(login, senha);
    }

    /**
     * Login do software: aluno não entra aqui (a área do aluno é o site).
     *
     * @return {@code true} para GERENTE, FUNCIONARIO e PROFESSOR; {@code false} para ALUNO ou usuário nulo
     */
    public boolean perfilPodeUsarSoftware(Usuario u) {
        return u != null && !"ALUNO".equalsIgnoreCase(u.perfil());
    }
}
