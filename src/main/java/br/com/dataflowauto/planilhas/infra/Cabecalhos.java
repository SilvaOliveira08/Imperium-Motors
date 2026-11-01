package br.com.dataflowauto.planilhas.infra;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Regras comuns aos leitores: cabeçalhos válidos e únicos, e o teto de linhas. */
final class Cabecalhos {

    private Cabecalhos() {
    }

    static ArquivoInvalidoException linhasDemais(String nomePlanilha, int maximoLinhas) {
        return new ArquivoInvalidoException("A planilha \"" + nomePlanilha + "\" tem mais de " + maximoLinhas
                + " linhas, o máximo aceito. Divida o arquivo em partes menores.");
    }

    static List<String> normalizar(List<String> brutos) {
        List<String> resultado = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        for (int i = 0; i < brutos.size(); i++) {
            String nome = brutos.get(i) == null ? "" : brutos.get(i).replace('﻿', ' ').trim();
            if (nome.isEmpty()) {
                nome = "Coluna " + (i + 1);
            }
            String candidato = nome;
            int sufixo = 2;
            while (!usados.add(candidato)) {
                candidato = nome + " " + sufixo++;
            }
            resultado.add(candidato);
        }
        return resultado;
    }
}
