package br.com.dataflowauto.comum.dominio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Tipos de arquivo aceitos pelo sistema. */
public enum TipoArquivo {
    XLSX("xlsx"),
    CSV("csv"),
    PDF("pdf"),
    TXT("txt");

    private final String extensao;

    TipoArquivo(String extensao) {
        this.extensao = extensao;
    }

    public String getExtensao() {
        return extensao;
    }

    public boolean ehPlanilha() {
        return this == XLSX || this == CSV;
    }

    public static Optional<TipoArquivo> porNomeArquivo(String nomeArquivo) {
        if (nomeArquivo == null || !nomeArquivo.contains(".")) {
            return Optional.empty();
        }
        String ext = nomeArquivo.substring(nomeArquivo.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extensao.equals(ext)).findFirst();
    }
}
