package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.FichaTreinoDAO;
import br.com.vidaativa.dao.ResumoDAO;
import br.com.vidaativa.model.FichaTreino;
import br.com.vidaativa.model.Resumo;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.ConteudoRolavel;
import br.com.vidaativa.ui.componentes.GradeFluida;
import br.com.vidaativa.ui.componentes.KpiCard;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Painel inicial simples (item "Painel" do menu) dos perfis FUNCIONARIO e PROFESSOR. O gerente tem o dashboard
 * completo em {@link GerentePainel}.
 * <p>
 * Os cartões mudam conforme o perfil do usuário logado:
 * <ul>
 *   <li>FUNCIONARIO: alunos ativos, cobranças pendentes e em atraso;</li>
 *   <li>PROFESSOR (e qualquer outro perfil): alunos ativos, fichas ativas e professores; o professor também vê a lista
 *       de fichas com revisão vencida ou marcada para os próximos 15 dias.</li>
 * </ul>
 * Os números vêm do {@link ResumoDAO}. Veja em {@link Resumo} o que cada contagem considera.
 */
public class ResumoPanel extends PainelBase {
    private final Usuario usuario;
    private final ConteudoRolavel corpo = new ConteudoRolavel(new BorderLayout());

    /**
     * @param usuario usuário logado: o nome aparece na saudação e o perfil define quais cartões aparecem
     */
    public ResumoPanel(Usuario usuario) {
        super("Painel inicial", "Olá, " + usuario.nome() + "! Resumo da academia.");
        this.usuario = usuario;
        add(ConteudoRolavel.embrulhar(corpo), BorderLayout.CENTER);
    }

    /**
     * Refaz os cartões com os números atuais do banco.
     * Se o banco falhar, mostra a mensagem de erro e um único cartão vermelho "Sem conexão".
     */
    @Override
    public void recarregar() {
        JPanel conteudo = new JPanel();
        conteudo.setOpaque(false);
        conteudo.setLayout(new BoxLayout(conteudo, BoxLayout.Y_AXIS));
        JPanel cartoes = new JPanel(new GradeFluida(220, 3, 16));
        cartoes.setOpaque(false);
        cartoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        conteudo.add(cartoes);
        try {
            // O carregar() também marca como ATRASADO as cobranças pendentes já vencidas, antes de contar.
            Resumo r = new ResumoDAO().carregar();
            switch (usuario.perfil().toUpperCase()) {
                case "FUNCIONARIO" -> {
                    cartoes.add(new KpiCard("Alunos ativos", Icones.Tipo.ALUNOS, Theme.GREEN).valor(String.valueOf(r.alunosAtivos()), Theme.TEXT).detalhe("cadastrados e ativos"));
                    cartoes.add(new KpiCard("Cobranças pendentes", Icones.Tipo.RELOGIO, Theme.WARN).valor(String.valueOf(r.pagamentosPendentes()), Theme.WARN).detalhe("aguardando pagamento"));
                    // Atrasos ficam vermelhos para chamar atenção; com zero atrasos, ficam verdes.
                    boolean atraso = r.pagamentosAtrasados() > 0;
                    cartoes.add(new KpiCard("Cobranças em atraso", Icones.Tipo.ALERTA, atraso ? Theme.DANGER : Theme.GREEN)
                            .valor(String.valueOf(r.pagamentosAtrasados()), atraso ? Theme.DANGER : Theme.GREEN).detalhe(atraso ? "cobrar na Recepção" : "tudo em dia"));
                }
                // Professor e qualquer outro perfil.
                default -> {
                    cartoes.add(new KpiCard("Alunos ativos", Icones.Tipo.ALUNOS, Theme.GREEN).valor(String.valueOf(r.alunosAtivos()), Theme.TEXT).detalhe("cadastrados e ativos"));
                    cartoes.add(new KpiCard("Fichas ativas", Icones.Tipo.FICHAS, Theme.INFO).valor(String.valueOf(r.fichasAtivas()), Theme.TEXT).detalhe("visíveis aos alunos no site"));
                    cartoes.add(new KpiCard("Professores", Icones.Tipo.EQUIPE, Theme.ROXO).valor(String.valueOf(r.professoresAtivos()), Theme.TEXT).detalhe("na equipe"));
                    if ("PROFESSOR".equalsIgnoreCase(usuario.perfil())) {
                        conteudo.add(Box.createVerticalStrut(18));
                        conteudo.add(cartaoRevisoes());
                    }
                }
            }
        } catch (Exception e) {
            UI.erro(this, e);
            cartoes.add(new KpiCard("Banco de dados", Icones.Tipo.ALERTA, Theme.DANGER).valor("Sem conexão", Theme.DANGER));
        }
        corpo.removeAll();
        corpo.add(conteudo, BorderLayout.NORTH);
        corpo.revalidate();
        corpo.repaint();
    }

    /** Fichas ativas com revisão vencida ou para os próximos 15 dias: o que o professor precisa rever. */
    private JComponent cartaoRevisoes() throws java.sql.SQLException {
        LocalDate hoje = LocalDate.now();
        List<FichaTreino> fichas = new FichaTreinoDAO().listar().stream()
                .filter(f -> "ATIVA".equals(f.status()) && f.dataRevisao() != null && !f.dataRevisao().isAfter(hoje.plusDays(15)))
                .sorted(Comparator.comparing(FichaTreino::dataRevisao)).toList();
        JPanel lista = GerentePainel.listaVertical();
        if (fichas.isEmpty()) lista.add(GerentePainel.vazio("Nenhuma ficha para revisar nos próximos 15 dias.", true));
        for (int i = 0; i < Math.min(6, fichas.size()); i++) {
            FichaTreino f = fichas.get(i);
            long dias = f.dataRevisao().toEpochDay() - hoje.toEpochDay();
            boolean vencida = dias < 0;
            lista.add(GerentePainel.item(f.alunoNome(), f.nome() + " · " + (vencida ? "revisão venceu há " + (-dias) + (dias == -1 ? " dia" : " dias") : dias == 0 ? "revisão é hoje" : "revisão em " + dias + (dias == 1 ? " dia" : " dias")),
                    UI.data(f.dataRevisao()), vencida ? Theme.DANGER : Theme.WARN, vencida ? Theme.DANGER : Theme.TEXT));
        }
        if (fichas.size() > 6) lista.add(GerentePainel.maisLinha("+ " + (fichas.size() - 6) + " outras em Fichas de treino"));
        JComponent c = GerentePainel.cartao("Fichas para revisar", "fichas ativas com revisão vencida ou nos próximos 15 dias", lista);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}
