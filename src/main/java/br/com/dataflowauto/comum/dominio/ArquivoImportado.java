package br.com.dataflowauto.comum.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/** Metadados de um arquivo enviado pelo usuário. O conteúdo fica no armazenamento de arquivos. */
@Entity
@Table(name = "arquivo_importado")
public class ArquivoImportado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_original", nullable = false)
    private String nomeOriginal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoArquivo tipo;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "caminho_armazenamento", nullable = false)
    private String caminhoArmazenamento;

    @Column(name = "importado_em", nullable = false)
    private LocalDateTime importadoEm;

    protected ArquivoImportado() {
        // exigido pelo JPA
    }

    public ArquivoImportado(String nomeOriginal, TipoArquivo tipo, long tamanhoBytes, String caminhoArmazenamento) {
        this.nomeOriginal = Objects.requireNonNull(nomeOriginal);
        this.tipo = Objects.requireNonNull(tipo);
        this.tamanhoBytes = tamanhoBytes;
        this.caminhoArmazenamento = Objects.requireNonNull(caminhoArmazenamento);
        this.importadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public TipoArquivo getTipo() {
        return tipo;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getCaminhoArmazenamento() {
        return caminhoArmazenamento;
    }

    public LocalDateTime getImportadoEm() {
        return importadoEm;
    }

    public String getTamanhoFormatado() {
        if (tamanhoBytes < 1024) {
            return tamanhoBytes + " B";
        }
        if (tamanhoBytes < 1024 * 1024) {
            return String.format("%.1f KB", tamanhoBytes / 1024.0);
        }
        return String.format("%.1f MB", tamanhoBytes / (1024.0 * 1024));
    }
}
