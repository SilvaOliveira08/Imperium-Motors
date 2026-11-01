package br.com.dataflowauto.planilhas.web;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Planilha em edição pelo usuário nesta sessão, com o histórico de etapas.
 * <p>
 * Desfazer não guarda uma cópia da planilha por etapa (eram até 21 cópias inteiras por sessão):
 * guarda a planilha de partida e cada etapa como uma operação que pode ser refeita (padrão Command).
 * Desfazer é reaplicar, a partir da partida, as etapas que sobraram. Em memória ficam só duas
 * planilhas: a de partida e a atual.
 * <p>
 * Cada planilha aberta ganha um identificador que vai nos formulários da tela. Se outra aba do mesmo
 * navegador abrir outra planilha, os formulários da aba antiga deixam de valer, em vez de aplicar
 * as regras (ou exportar) a planilha errada.
 */
@Component
@SessionScope
public class AreaTrabalho implements Serializable {

    private static final int MAX_DESFAZER = 20;

    /** Uma etapa de edição: a descrição mostrada e a operação que a refaz sobre a planilha anterior. */
    public record Etapa(String descricao, UnaryOperator<Planilha> operacao) {
    }

    private String id;
    /** Planilha antes da primeira etapa que ainda pode ser desfeita. */
    private transient Planilha partida;
    private transient Planilha atual;
    private transient List<Etapa> desfaziveis = new ArrayList<>();
    private final List<String> etapas = new ArrayList<>();

    public void iniciar(Planilha planilha) {
        id = UUID.randomUUID().toString();
        partida = planilha;
        atual = planilha;
        desfaziveis = new ArrayList<>();
        etapas.clear();
        etapas.add("Planilha carregada: " + planilha.getNome());
    }

    /** Registra uma etapa já aplicada: {@code nova} é o resultado de {@code etapa} sobre a planilha atual. */
    public void registrar(Planilha nova, Etapa etapa) {
        exigirPlanilha();
        desfaziveis.add(etapa);
        if (desfaziveis.size() > MAX_DESFAZER) {
            // a etapa mais antiga deixa de poder ser desfeita e passa a fazer parte da partida
            partida = desfaziveis.remove(0).operacao().apply(partida);
        }
        atual = nova;
        etapas.add(etapa.descricao());
    }

    public void desfazer() {
        if (!podeDesfazer()) {
            throw new RegraNegocioException("Não há etapa para desfazer.");
        }
        desfaziveis.remove(desfaziveis.size() - 1);
        etapas.remove(etapas.size() - 1);
        Planilha refeita = partida;
        for (Etapa etapa : desfaziveis) {
            refeita = etapa.operacao().apply(refeita);
        }
        atual = refeita;
    }

    /**
     * Confere se o formulário veio da planilha que está aberta agora. O identificador muda sempre que
     * uma planilha é aberta, então uma aba antiga não altera nem exporta a planilha aberta em outra aba.
     */
    public void conferir(String idFormulario) {
        exigirPlanilha();
        if (!Objects.equals(id, idFormulario)) {
            throw new RegraNegocioException("Esta página ficou desatualizada: a planilha \"" + atual.getNome()
                    + "\" foi aberta em outra aba. Nada foi alterado. Abra a área de trabalho de novo antes de continuar.");
        }
    }

    public boolean possuiPlanilha() {
        return atual != null;
    }

    public boolean podeDesfazer() {
        return desfaziveis != null && !desfaziveis.isEmpty();
    }

    public String getId() {
        return id;
    }

    public Planilha getAtual() {
        exigirPlanilha();
        return atual;
    }

    public List<String> getEtapas() {
        return List.copyOf(etapas);
    }

    private void exigirPlanilha() {
        if (atual == null) {
            throw new RegraNegocioException("Nenhuma planilha em edição. Escolha uma planilha para começar.");
        }
    }
}
