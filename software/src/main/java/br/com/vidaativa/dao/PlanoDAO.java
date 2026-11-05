package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Plano;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso (somente leitura) à tabela {@code planos}: Básico, Plus e Premium.
 *
 * <p>Quem usa: o formulário de aluno ({@code AlunosPanel}, combo de planos) e a API do site ({@code DadosBanco}, endpoint
 * público {@code GET /api/planos}, página de Planos). Os planos são criados por database/schema.sql e seed.sql; não há tela
 * nem método para cadastrá-los ou editá-los.
 *
 * <p>Atenção: a regra de cobrança e o que cada plano inclui ({@code Precificacao}) identificam o plano pelo NOME
 * ("Básico", "Plus", "Premium"). Renomear um plano no banco muda esse comportamento.
 */
public class PlanoDAO {
    /**
     * Lista os planos ativos ({@code ativo = 1}), do mais barato para o mais caro (empate resolvido pelo id).
     */
    public List<Plano> listar() throws SQLException {
        List<Plano> lista = new ArrayList<>();
        String sql = "SELECT id, nome, valor_mensal, descricao FROM planos WHERE ativo = 1 ORDER BY valor_mensal, id";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Plano(rs.getInt("id"), rs.getString("nome"), rs.getBigDecimal("valor_mensal"), rs.getString("descricao")));
            }
        }
        return lista;
    }
}
