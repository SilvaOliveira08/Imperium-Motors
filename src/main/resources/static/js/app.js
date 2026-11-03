// Interações simples. Tudo funciona sem JavaScript; aqui só deixamos a tela mais prática.
document.addEventListener('DOMContentLoaded', () => {
    configurarOrdemColunas();
    configurarMarcarTodas();
    configurarCombinacao();
    configurarConfirmarTodos();
    configurarPortaMalasSeteLugares();
    configurarCopiar();
});

// Divulgação: botões "Copiar" levam o texto do campo indicado em data-copiar para a área de transferência.
function configurarCopiar() {
    document.querySelectorAll('[data-copiar]').forEach((botao) => {
        botao.addEventListener('click', async () => {
            const campo = document.getElementById(botao.dataset.copiar);
            if (!campo) return;
            const original = botao.textContent;
            try {
                await navigator.clipboard.writeText(campo.value);
            } catch (e) {
                campo.select();
                document.execCommand('copy');
            }
            botao.textContent = 'Copiado!';
            botao.classList.add('copiado');
            setTimeout(() => { botao.textContent = original; botao.classList.remove('copiado'); }, 1800);
        });
    });
}

function configurarOrdemColunas() {
    const tabela = document.getElementById('tabela-colunas');
    if (!tabela) return;
    const corpo = tabela.querySelector('tbody');

    const renumerar = () => {
        corpo.querySelectorAll('tr').forEach((linha, i) => {
            linha.querySelector('input.ordem').value = i + 1;
        });
    };

    corpo.addEventListener('click', (evento) => {
        const botao = evento.target.closest('button.mover');
        if (!botao) return;
        const linha = botao.closest('tr');
        if (botao.dataset.direcao === '-1' && linha.previousElementSibling) {
            corpo.insertBefore(linha, linha.previousElementSibling);
        } else if (botao.dataset.direcao === '1' && linha.nextElementSibling) {
            corpo.insertBefore(linha.nextElementSibling, linha);
        }
        renumerar();
    });

    corpo.addEventListener('change', (evento) => {
        if (evento.target.classList.contains('incluir')) {
            evento.target.closest('tr').classList.toggle('excluida', !evento.target.checked);
        }
    });
}

function configurarMarcarTodas() {
    const mestre = document.getElementById('marcar-todas');
    if (!mestre) return;
    mestre.addEventListener('change', () => {
        document.querySelectorAll('#tabela-colunas input.incluir').forEach((caixa) => {
            caixa.checked = mestre.checked;
            caixa.closest('tr').classList.toggle('excluida', !mestre.checked);
        });
    });
}

function configurarCombinacao() {
    const seletor = document.getElementById('outra-planilha');
    const chave = document.getElementById('chave-outra');
    if (!seletor || !chave) return;

    seletor.addEventListener('change', async () => {
        chave.innerHTML = '';
        if (!seletor.value) {
            chave.append(new Option('Escolha a planilha primeiro', ''));
            return;
        }
        chave.append(new Option('Carregando…', ''));
        try {
            const resposta = await fetch(seletor.dataset.url + seletor.value + '/colunas');
            if (!resposta.ok) throw new Error();
            const colunas = await resposta.json();
            chave.innerHTML = '';
            const atual = document.querySelector('#form-combinar select[name=chaveAtual]').value;
            colunas.forEach((nome) => chave.append(new Option(nome, nome, false, nome === atual)));
        } catch (e) {
            chave.innerHTML = '';
            chave.append(new Option('Não foi possível ler as colunas', ''));
        }
    });
}

function configurarConfirmarTodos() {
    const botao = document.getElementById('confirmar-todos');
    if (!botao) return;
    botao.addEventListener('click', () => {
        document.querySelectorAll('input[name=confirmados]').forEach((caixa) => { caixa.checked = true; });
    });
}

// Revisão da ficha: o campo "porta-malas com 7 lugares" aparece quando o carro tem 7 lugares ou mais
// (ou quando já tem valor, para não esconder um dado preenchido).
function configurarPortaMalasSeteLugares() {
    const lugares = document.getElementById('valor-LUGARES');
    const bloco = document.querySelector('[data-porta-malas-7]');
    if (!lugares || !bloco) return;
    const campo = bloco.querySelector('input[type=text]');
    const atualizar = () => {
        const numero = parseInt(lugares.value.replace(/\D/g, ''), 10);
        bloco.hidden = !(numero >= 7) && campo.value.trim() === '';
    };
    lugares.addEventListener('input', atualizar);
    atualizar();
}

// Confirmação antes de enviar formulários com data-confirmar (no lugar de onsubmit, bloqueado pela CSP).
document.addEventListener('submit', (evento) => {
    const mensagem = evento.target.dataset && evento.target.dataset.confirmar;
    if (mensagem && !window.confirm(mensagem)) {
        evento.preventDefault();
    }
});

document.addEventListener('DOMContentLoaded', () => {
    configurarContatoLoja();
    configurarGaleria();
    configurarCompradorVenda();
});

// Na página do carro, mostra só os campos que fazem sentido para o tipo de contato escolhido.
function configurarContatoLoja() {
    const form = document.getElementById('form-contato');
    if (!form) return;
    const atualizar = () => {
        const tipo = (form.querySelector('input[name=tipo]:checked') || {}).value;
        form.querySelectorAll('[data-tipo]').forEach((campo) => {
            campo.hidden = campo.dataset.tipo !== tipo;
        });
    };
    form.addEventListener('change', atualizar);
    atualizar();
}

// Clicar numa miniatura troca a foto principal em vez de abrir a imagem.
function configurarGaleria() {
    const principal = document.querySelector('.galeria-principal img.galeria-foto');
    const fundo = document.querySelector('.galeria-principal img.foto-fundo');
    const miniaturas = document.querySelectorAll('.galeria-miniaturas .miniatura');
    if (!principal || miniaturas.length === 0) return;
    miniaturas.forEach((link) => {
        link.addEventListener('click', (evento) => {
            evento.preventDefault();
            principal.src = link.href;
            if (fundo) fundo.src = link.href;
            miniaturas.forEach((m) => m.classList.toggle('ativa', m === link));
        });
    });
}

// Ao escolher o interessado que comprou, preenche o nome do comprador.
function configurarCompradorVenda() {
    const select = document.getElementById('proposta-venda');
    const comprador = document.getElementById('comprador-venda');
    if (!select || !comprador) return;
    select.addEventListener('change', () => {
        const opcao = select.selectedOptions[0];
        if (opcao && opcao.dataset.nome) comprador.value = opcao.dataset.nome;
    });
}
