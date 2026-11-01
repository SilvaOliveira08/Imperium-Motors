package br.com.dataflowauto.comum.web;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/** Transforma erros de validação e de regra de negócio em mensagens amigáveis na própria tela. */
@ControllerAdvice
public class TratadorErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);

    private final DataSize maximoPorArquivo;
    private final DataSize maximoPorEnvio;

    public TratadorErros(@Value("${spring.servlet.multipart.max-file-size:1MB}") DataSize maximoPorArquivo,
                         @Value("${spring.servlet.multipart.max-request-size:10MB}") DataSize maximoPorEnvio) {
        this.maximoPorArquivo = maximoPorArquivo;
        this.maximoPorEnvio = maximoPorEnvio;
    }

    @ExceptionHandler({ArquivoInvalidoException.class, RegraNegocioException.class})
    public String erroDeUsuario(RuntimeException e, HttpServletRequest request, RedirectAttributes atributos) {
        atributos.addFlashAttribute("erro", e.getMessage());
        return "redirect:" + voltar(request);
    }

    /** O envio passou do limite configurado: os números da mensagem vêm da mesma configuração. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String arquivoGrande(HttpServletRequest request, RedirectAttributes atributos) {
        atributos.addFlashAttribute("erro", "O envio passou do tamanho máximo permitido: até "
                + emMegabytes(maximoPorArquivo) + " por arquivo e " + emMegabytes(maximoPorEnvio) + " por envio.");
        return "redirect:" + voltar(request);
    }

    /**
     * Qualquer outra falha ao receber o upload (pasta temporária apagada, envio interrompido) não é
     * culpa do tamanho: vai para o log com a causa, e o usuário não recebe uma explicação falsa.
     */
    @ExceptionHandler(MultipartException.class)
    public String falhaNoEnvio(MultipartException e, HttpServletRequest request, RedirectAttributes atributos) {
        LOG.error("Falha ao receber arquivo enviado em {}", request.getRequestURI(), e);
        atributos.addFlashAttribute("erro", "Não foi possível receber o arquivo enviado. Tente de novo e, "
                + "se continuar, avise o responsável pelo sistema.");
        return "redirect:" + voltar(request);
    }

    private static String emMegabytes(DataSize tamanho) {
        return tamanho.toMegabytes() + " MB";
    }

    /** Duas pessoas gravaram o mesmo registro ao mesmo tempo ({@code @Version}): a segunda não sobrescreve a primeira. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String alteradoAoMesmoTempo(HttpServletRequest request, RedirectAttributes atributos) {
        atributos.addFlashAttribute("erro", "Outra pessoa alterou este registro ao mesmo tempo, e a sua alteração "
                + "não foi gravada. Confira como ficou e, se precisar, faça de novo.");
        return "redirect:" + voltar(request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String naoEncontrado(RecursoNaoEncontradoException e, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("mensagem", e.getMessage());
        return "error";
    }

    /** Volta para a página de onde veio o formulário, aceitando apenas caminhos internos. */
    static String voltar(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return "/";
            }
            String caminho = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            return uri.getRawQuery() == null ? caminho : caminho + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException ex) {
            return "/";
        }
    }
}
