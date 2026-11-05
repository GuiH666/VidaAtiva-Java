package br.com.vidaativa.api;

import br.com.vidaativa.dao.*;
import br.com.vidaativa.model.*;

import java.util.List;

/**
 * Implementação de {@link PortalDados} sobre o MySQL (os mesmos DAOs do software). É por isso que o que a recepção e os
 * professores cadastram no software aparece no site sem nenhuma cópia de dados.
 *
 * <p>Quem usa: {@code ApiServer.iniciarEmSegundoPlano} e {@code ApiServer.main}. É só uma "cola": a lógica está nos DAOs.
 * A única regra própria é {@link #alunoPorUsuario}, que descarta alunos desativados.
 */
public class DadosBanco implements PortalDados {
    // Estes dois DAOs são guardados em campos; os demais são criados a cada chamada (não guardam estado, então tanto faz).
    private final UsuarioDAO usuarios = new UsuarioDAO();
    private final AlunoDAO alunos = new AlunoDAO();

    // O login também migra o hash de senha antigo (SHA-256) para PBKDF2, dentro de UsuarioDAO.autenticar.
    @Override public Usuario autenticar(String login, String senha) throws Exception { return usuarios.autenticar(login, senha); }

    @Override public Aluno alunoPorUsuario(int usuarioId) throws Exception {
        // O DAO devolve também alunos desativados (para o software poder listá-los); aqui eles são tratados como inexistentes.
        Aluno a = alunos.buscarPorUsuarioId(usuarioId);
        return a != null && a.ativo() ? a : null;
    }

    @Override public List<Plano> planos() throws Exception { return new PlanoDAO().listar(); }
    @Override public List<FichaTreino> fichasAtivas(int alunoId) throws Exception { return new FichaTreinoDAO().listarAtivasDoAluno(alunoId); }
    @Override public List<Pagamento> pagamentos(int alunoId) throws Exception { return new PagamentoDAO().listarPorAluno(alunoId); }
    @Override public List<AvaliacaoFisica> avaliacoes(int alunoId) throws Exception { return new AvaliacaoDAO().listarPorAluno(alunoId); }

    // Exige a senha atual e valida o tamanho mínimo da nova (IllegalArgumentException); detalhes em UsuarioDAO.alterarSenha.
    @Override public boolean alterarSenha(int usuarioId, String senhaAtual, String novaSenha) throws Exception {
        return usuarios.alterarSenha(usuarioId, senhaAtual, novaSenha);
    }
}
