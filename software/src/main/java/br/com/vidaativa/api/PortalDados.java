package br.com.vidaativa.api;

import br.com.vidaativa.model.*;

import java.util.List;

/**
 * Tudo que a API precisa buscar/gravar, em uma interface. A {@code ApiServer} só conhece esta interface, nunca o banco:
 * assim a API pode ser testada sem MySQL.
 *
 * <p>Implementações: {@link DadosBanco} (a real, sobre os DAOs do software) e {@code TestesApi.DadosFalsos} (em memória,
 * só para os testes).
 *
 * <p>Convenção importante: os métodos que recebem {@code alunoId} devolvem SÓ dados daquele aluno. Quem decide de qual
 * aluno se trata é a {@code ApiServer}, a partir do token, e nunca o cliente. Todos podem lançar {@code Exception} (em
 * geral {@code SQLException}); a API converte isso em erro 500.
 */
public interface PortalDados {
    /**
     * Usuário se login+senha conferem e o usuário está ativo; senão null. Aceita QUALQUER perfil: quem restringe a
     * ALUNO é a {@code ApiServer}.
     *
     * @param login e-mail ou username
     */
    Usuario autenticar(String login, String senha) throws Exception;

    /**
     * Aluno ativo ligado ao usuário, ou null (usuário que não é aluno, aluno inexistente ou desativado).
     *
     * @param usuarioId id da tabela {@code usuarios} (é o que o token guarda), NÃO o id do aluno
     */
    Aluno alunoPorUsuario(int usuarioId) throws Exception;

    /** Planos ativos, para a página pública de Planos. */
    List<Plano> planos() throws Exception;

    /** Só as fichas com status ATIVA do aluno (rascunhos e inativas não podem aparecer no site). */
    List<FichaTreino> fichasAtivas(int alunoId) throws Exception;

    /** Mensalidades do aluno, da mais recente para a mais antiga. */
    List<Pagamento> pagamentos(int alunoId) throws Exception;

    /** Avaliações físicas do aluno. */
    List<AvaliacaoFisica> avaliacoes(int alunoId) throws Exception;

    /**
     * Troca a senha do usuário (login do aluno).
     *
     * @param usuarioId id da tabela {@code usuarios}
     * @return false se a senha atual estiver errada. Lança IllegalArgumentException se a nova for inválida.
     */
    boolean alterarSenha(int usuarioId, String senhaAtual, String novaSenha) throws Exception;
}
