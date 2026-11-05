package br.com.vidaativa.ui;

import javax.swing.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Campo de texto para digitar datas no formato dd/MM/aaaa. Deixe vazio para "sem data".
 * <p>
 * Quem usa: os formulários de aluno (data de nascimento), ficha de treino (início e revisão) e avaliação física.
 * O código da tela chama {@link #setData} para preencher e {@link #getData} ou {@link #getDataObrigatoria} ao salvar.
 * <p>
 * A conversão do texto só acontece ao ler ({@code getData}); enquanto o usuário digita, nada é validado.
 * Para EXIBIR datas em tabelas, use {@link UI#data}.
 */
public class DateField extends JTextField {
    // STRICT + "uuuu": rejeita datas que não existem (ex.: 31/02/2026), que o modo padrão corrigiria sozinho para 28/02.
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    // Nome do campo como aparece na tela; entra na mensagem de erro para o usuário saber qual campo corrigir.
    private final String nomeCampo;

    /**
     * @param nomeCampo nome do campo usado nas mensagens de erro (ex.: "Data de nascimento")
     */
    public DateField(String nomeCampo) {
        // 10 colunas de largura: cabe "dd/mm/aaaa".
        super(10);
        this.nomeCampo = nomeCampo;
        setToolTipText("dd/mm/aaaa");
    }

    /** Mostra a data no campo; {@code null} deixa o campo vazio. */
    public void setData(LocalDate d) { setText(d == null ? "" : FMT.format(d)); }

    /**
     * Data digitada, ou null se vazio (espaços nas pontas são ignorados).
     *
     * @throws IllegalArgumentException se o texto não for uma data válida em dd/mm/aaaa; a mensagem cita o nome do campo
     */
    public LocalDate getData() {
        String t = getText().trim();
        if (t.isEmpty()) return null;
        try {
            return LocalDate.parse(t, FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida em \"" + nomeCampo + "\". Use o formato dd/mm/aaaa (ex.: 25/12/2026).");
        }
    }

    /**
     * Como {@link #getData}, mas para campos que não podem ficar vazios.
     *
     * @throws IllegalArgumentException se o campo estiver vazio ou com formato inválido
     */
    public LocalDate getDataObrigatoria() {
        LocalDate d = getData();
        if (d == null) throw new IllegalArgumentException("Preencha o campo \"" + nomeCampo + "\".");
        return d;
    }
}
