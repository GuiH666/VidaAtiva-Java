package br.com.vidaativa.ui;

import br.com.vidaativa.api.ApiServer;
import br.com.vidaativa.config.AppConfig;

import javax.swing.*;

/**
 * Ponto de entrada do software desktop da academia (é esta classe que se executa para abrir o programa).
 * <p>
 * Fluxo de telas: {@code Main} abre o {@link LoginFrame}; depois do login, o {@link DashboardFrame}
 * mostra o menu conforme o perfil do usuário.
 * <p>
 * A API do site sobe junto com o software quando a configuração {@code api.autostart} é verdadeira
 * (veja {@link AppConfig}). Para subir só a API, sem janela, execute {@code ApiServer.main}.
 */
public class Main {
    /**
     * Inicia a API (se configurada) e abre a janela de login.
     *
     * @param args não usado
     */
    public static void main(String[] args) {
        // A API roda em threads próprias; se a porta estiver ocupada ela só avisa no console e o software abre normalmente.
        if (AppConfig.apiAutostart()) ApiServer.iniciarEmSegundoPlano();
        // Todo código de interface Swing deve rodar na thread de eventos (EDT), por isso o invokeLater.
        SwingUtilities.invokeLater(() -> {
            // O tema precisa ser aplicado antes de criar qualquer componente, senão a janela nasce com o visual padrão.
            Theme.apply();
            new LoginFrame().setVisible(true);
        });
    }
}
