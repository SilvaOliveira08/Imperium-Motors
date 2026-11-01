package br.com.dataflowauto.fichas.web;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Valores editados e campos marcados como confirmados na tela de revisão. */
public class RevisaoForm {

    private Map<String, String> valores = new HashMap<>();
    private List<String> confirmados = new ArrayList<>();
    private String acao;

    public Map<CampoTecnico, String> valoresPorCampo() {
        Map<CampoTecnico, String> mapa = new EnumMap<>(CampoTecnico.class);
        valores.forEach((chave, valor) -> mapa.put(CampoTecnico.valueOf(chave), valor));
        return mapa;
    }

    public Set<CampoTecnico> camposConfirmados() {
        Set<CampoTecnico> set = EnumSet.noneOf(CampoTecnico.class);
        confirmados.forEach(c -> set.add(CampoTecnico.valueOf(c)));
        return set;
    }

    public boolean querConcluir() {
        return "concluir".equals(acao);
    }

    public Map<String, String> getValores() {
        return valores;
    }

    public void setValores(Map<String, String> valores) {
        this.valores = valores;
    }

    public List<String> getConfirmados() {
        return confirmados;
    }

    public void setConfirmados(List<String> confirmados) {
        this.confirmados = confirmados;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }
}
