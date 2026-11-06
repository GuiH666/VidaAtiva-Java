package br.com.vidaativa.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ícone do aplicativo (o logo da academia) para as janelas e a barra de tarefas.
 * <p>
 * A imagem fica em {@code src/main/resources/icone/vida-ativa.png} (256x256) e é lida do classpath, por isso funciona tanto
 * rodando pelo {@code rodar.bat} quanto dentro do aplicativo empacotado ({@code empacotar.bat}). O ícone do arquivo
 * {@code .exe} e do atalho é outro arquivo ({@code empacotamento/vida-ativa.ico}), usado só no empacotamento.
 * Se a imagem não puder ser lida, as janelas simplesmente ficam com o ícone padrão do Java: isso nunca impede o programa de abrir.
 */
public final class IconeApp {
    private static final String RECURSO = "/icone/vida-ativa.png";
    private static List<Image> imagens;

    private IconeApp() { }

    /** Define o ícone da janela (em vários tamanhos, para o Windows escolher o certo na barra de tarefas e no Alt+Tab). */
    public static void aplicar(Window janela) {
        List<Image> lista = imagens();
        if (!lista.isEmpty()) janela.setIconImages(lista);
    }

    /** Imagens 256, 64, 32 e 16 px (lista vazia se o recurso não existir). Lida uma única vez. */
    public static synchronized List<Image> imagens() {
        if (imagens != null) return imagens;
        List<Image> lista = new ArrayList<>();
        try (InputStream in = IconeApp.class.getResourceAsStream(RECURSO)) {
            if (in != null) {
                BufferedImage original = ImageIO.read(in);
                if (original != null) {
                    lista.add(original);
                    for (int tamanho : new int[]{64, 32, 16}) lista.add(reduzir(original, tamanho));
                }
            }
        } catch (Exception e) {
            System.err.println("[Ícone] Não foi possível carregar o ícone: " + e.getMessage());
        }
        imagens = List.copyOf(lista);
        return imagens;
    }

    private static BufferedImage reduzir(BufferedImage origem, int tamanho) {
        BufferedImage destino = new BufferedImage(tamanho, tamanho, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(origem, 0, 0, tamanho, tamanho, null);
        g.dispose();
        return destino;
    }
}
