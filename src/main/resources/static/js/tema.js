// Tema claro/escuro. Carregado sem "defer" no <head> para aplicar o tema antes da página aparecer
// (sem piscar). O padrão é o claro; a escolha do visitante fica guardada neste navegador.
(function () {
    const CHAVE = 'dataflow-tema';
    const raiz = document.documentElement;

    function lerEscolha() {
        try {
            return localStorage.getItem(CHAVE) === 'dark' ? 'dark' : 'light';
        } catch (e) {
            return 'light';
        }
    }

    function aplicar(tema) {
        raiz.dataset.theme = tema;
        document.querySelectorAll('[data-alternar-tema]').forEach((botao) => {
            const escuro = tema === 'dark';
            const icone = botao.querySelector('.tema-icone');
            const rotulo = botao.querySelector('.tema-rotulo');
            if (icone) icone.textContent = escuro ? '☀️' : '🌙';
            if (rotulo) rotulo.textContent = botao.closest('.loja-topo')
                ? (escuro ? 'Claro' : 'Escuro')
                : (escuro ? 'Tema claro' : 'Tema escuro');
            botao.setAttribute('aria-pressed', String(escuro));
        });
    }

    aplicar(lerEscolha());

    document.addEventListener('DOMContentLoaded', () => aplicar(raiz.dataset.theme));
    document.addEventListener('click', (evento) => {
        if (!evento.target.closest('[data-alternar-tema]')) return;
        const novo = raiz.dataset.theme === 'dark' ? 'light' : 'dark';
        try {
            localStorage.setItem(CHAVE, novo);
        } catch (e) {
            // sem armazenamento (janela anônima): o tema vale só para esta página
        }
        aplicar(novo);
    });
})();
