import random, sys, datetime as dt
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.table import Table, TableStyleInfo, TableFormula
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.formatting.rule import FormulaRule, CellIsRule, ColorScaleRule
from openpyxl.chart import BarChart, LineChart, Reference
from openpyxl.chart.label import DataLabelList
from openpyxl.comments import Comment

OUT = sys.argv[1]
WITH_DATA = sys.argv[2] == "exemplo"

F = "Arial"
NAVY, BLUE, LBLUE = "1F3A5F", "2E75B6", "DDEBF7"
GREEN, LGREEN = "1E8449", "C6EFCE"
RED, LRED = "C0392B", "FFC7CE"
AMBER, LAMBER = "9C6500", "FFEB9C"
GRAY, LGRAY, WHITE = "595959", "F2F2F2", "FFFFFF"
BRL = '"R$" #,##0.00;-"R$" #,##0.00;"R$" 0,00'.replace('"R$" 0,00', '"R$" 0.00')
BRL_SIGN = '+"R$" #,##0.00;-"R$" #,##0.00;"R$" 0.00'
PCT_ARROW = '"▲ "0.0%;"▼ "0.0%;0.0%'
DATE = "dd/mm/yyyy"
KMF = '#,##0" km"'
thin = Side(style="thin", color="D9D9D9")
BOX = Border(left=thin, right=thin, top=thin, bottom=thin)
BOTTOM = Border(bottom=Side(style="thin", color="BFBFBF"))

def fill(c): return PatternFill("solid", start_color=c, end_color=c)
def font(**k):
    k.setdefault("name", F); k.setdefault("size", 10)
    return Font(**k)

CATEGORIAS = [
    ("Combustível", "Combustível"), ("Manutenção preventiva", "Manutenção"),
    ("Manutenção corretiva", "Manutenção"), ("Troca de óleo", "Manutenção"),
    ("Pneus", "Manutenção"), ("Seguro", "Custos fixos"),
    ("IPVA e licenciamento", "Custos fixos"), ("Pedágios", "Operacional"),
    ("Estacionamento", "Operacional"), ("Lavagem", "Operacional"),
    ("Multas", "Multas"), ("Outros", "Outros")]
GRUPOS = ["Combustível", "Manutenção", "Custos fixos", "Operacional", "Multas", "Outros"]
FORMAS = ["PIX", "Dinheiro", "Cartão de crédito", "Cartão de débito", "Cartão combustível",
          "Boleto", "Transferência", "Faturado"]
STATUS_PG = ["Pago", "Pendente", "Agendado", "Atrasado"]
STATUS_VE = ["Ativo", "Em manutenção", "Inativo", "Vendido"]
MESES = ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto",
         "Setembro", "Outubro", "Novembro", "Dezembro"]
MESES_AB = ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"]
ANOS = list(range(2023, 2036))
SLOTS = 25  # veículos acompanhados nos cálculos/gráficos

wb = Workbook()
ws_ins = wb.active; ws_ins.title = "Instruções"
ws_d = wb.create_sheet("Dashboard")
ws_v = wb.create_sheet("Veículos")
ws_e = wb.create_sheet("Despesas")
ws_r = wb.create_sheet("Resumo")
ws_c = wb.create_sheet("Cálculos")
ws_l = wb.create_sheet("Listas")

def name(n, ref):
    wb.defined_names[n] = DefinedName(n, attr_text=ref)

def title(ws, text, sub, width_cols):
    ws["A1"] = text; ws["A1"].font = font(size=18, bold=True, color=WHITE)
    ws["A2"] = sub; ws["A2"].font = font(size=10, italic=True, color="DDEBF7")
    for r in (1, 2):
        for c in range(1, width_cols + 1):
            ws.cell(r, c).fill = fill(NAVY)
    ws.row_dimensions[1].height = 30; ws.row_dimensions[2].height = 18

def header_row(ws, row, headers, auto_from=None):
    for i, h in enumerate(headers, 1):
        c = ws.cell(row, i, h)
        auto = auto_from and i >= auto_from
        c.font = font(bold=True, color=WHITE)
        c.fill = fill(GRAY if auto else BLUE)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
    ws.row_dimensions[row].height = 32

# ---------------------------------------------------------------- Listas
ws = ws_l
title(ws, "Listas e parâmetros", "Itens das listas suspensas. Edite aqui para incluir novas opções.", 12)
heads = ["Categoria", "Grupo", "", "Forma de pagamento", "", "Status do pagamento", "",
         "Status do veículo", "", "Mês", "Mês abrev.", "Ano"]
for i, h in enumerate(heads, 1):
    if h:
        c = ws.cell(4, i, h); c.font = font(bold=True, color=WHITE); c.fill = fill(BLUE)
for i, (cat, g) in enumerate(CATEGORIAS):
    ws.cell(5 + i, 1, cat); ws.cell(5 + i, 2, g)
for i, v in enumerate(FORMAS): ws.cell(5 + i, 4, v)
for i, v in enumerate(STATUS_PG): ws.cell(5 + i, 6, v)
for i, v in enumerate(STATUS_VE): ws.cell(5 + i, 8, v)
for i, v in enumerate(MESES): ws.cell(5 + i, 10, v); ws.cell(5 + i, 11, MESES_AB[i])
ws.cell(5, 12, "Todos")
for i, v in enumerate(ANOS): ws.cell(6 + i, 12, v)
ws["N4"] = "Parâmetro"; ws["O4"] = "Valor"
for c in ("N4", "O4"): ws[c].font = font(bold=True, color=WHITE); ws[c].fill = fill(BLUE)
ws["N5"] = "Alerta de gasto elevado (por lançamento)"; ws["O5"] = 1000
ws["O5"].number_format = BRL; ws["O5"].font = font(color="0000FF"); ws["O5"].fill = fill("FFFF00")
ws["O5"].comment = Comment("Valor de referência definido como exemplo (R$ 1.000). Ajuste conforme a realidade da frota: lançamentos acima dele ficam em vermelho.", "Planilha")
ws["N6"] = "Faixa de tolerância (verde/vermelho)"; ws["O6"] = 0.10
ws["O6"].number_format = "0%"; ws["O6"].font = font(color="0000FF"); ws["O6"].fill = fill("FFFF00")
ws["O6"].comment = Comment("Diferença mínima em relação à média para colorir indicadores (10% por padrão).", "Planilha")
for row in ws.iter_rows(min_row=5, max_row=20):
    for c in row:
        if c.font.color is None or c.font.color.rgb in (None, "FF000000"):
            c.font = font()
ws["O5"].font = font(color="0000FF"); ws["O6"].font = font(color="0000FF")
for col, w in zip("ABCDEFGHIJKLMNO", [24, 14, 2, 20, 2, 20, 2, 18, 2, 12, 11, 8, 2, 38, 14]):
    ws.column_dimensions[col].width = w
ws.sheet_view.showGridLines = False
n_cat = len(CATEGORIAS)
name("lstCategorias", f"Listas!$A$5:$A${4 + n_cat}")
name("lstGrupoCat", f"Listas!$B$5:$B${4 + n_cat}")
name("lstFormas", f"Listas!$D$5:$D${4 + len(FORMAS) + 4}")
name("lstStatusPg", f"Listas!$F$5:$F${4 + len(STATUS_PG)}")
name("lstStatusVe", f"Listas!$H$5:$H${4 + len(STATUS_VE)}")
name("lstMeses", "Listas!$J$5:$J$16")
name("lstMesAbrev", "Listas!$K$5:$K$16")
name("lstAnos", f"Listas!$L$5:$L${5 + len(ANOS)}")
name("pAlerta", "Listas!$O$5")
name("pTol", "Listas!$O$6")
ws_l["P4"] = ""
# listas de filtro com "Todos"
ws["Q4"] = "Filtro mês"; ws["R4"] = "Filtro categoria"
for c in ("Q4", "R4"): ws[c].font = font(bold=True, color=WHITE); ws[c].fill = fill(BLUE)
ws["Q5"] = "Todos"; ws["R5"] = "Todos"
for i, v in enumerate(MESES): ws.cell(6 + i, 17, v).font = font()
for i, (cat, _) in enumerate(CATEGORIAS): ws.cell(6 + i, 18, cat).font = font()
ws["Q5"].font = font(); ws["R5"].font = font()
ws.column_dimensions["Q"].width = 14; ws.column_dimensions["R"].width = 24
name("lstFiltroMes", "Listas!$Q$5:$Q$17")
name("lstFiltroCat", f"Listas!$R$5:$R${5 + n_cat}")

# ---------------------------------------------------------------- dados de exemplo
veiculos = [
    ("V001", "ABC1D23", "Fiat Strada 1.3", 2022, "Carlos Souza", "Ativo", "Utilitário de entregas"),
    ("V002", "BRA2E19", "Volkswagen Gol 1.0", 2020, "Ana Lima", "Ativo", "Carro de apoio administrativo"),
    ("V003", "XYZ9876", "Chevrolet S10 2.8", 2021, "Marcos Pereira", "Em manutenção", "Revisão do câmbio em andamento"),
]
despesas = []
if WITH_DATA:
    random.seed(42)
    profile = {"ABC1D23": (38000, 2600, 6.2, 48), "BRA2E19": (61000, 1500, 6.0, 40),
               "XYZ9876": (45000, 3200, 6.4, 75)}
    start, end = dt.date(2025, 1, 2), dt.date(2026, 10, 20)
    for (cod, placa, mod, ano, resp, st, obs) in veiculos:
        km, km_mes, preco, tanque = profile[placa]
        d = start; last_oil = km; last_prev = start
        while d <= end:
            km += int(km_mes / 3 * random.uniform(0.8, 1.2))
            litros = round(tanque * random.uniform(0.7, 0.95), 1)
            despesas.append([d, placa, "Combustível", f"Abastecimento {litros} L", round(litros * preco * random.uniform(0.97, 1.05), 2), km, "Cartão combustível", "Pago", resp, ""])
            if km - last_oil > 10000:
                despesas.append([d + dt.timedelta(days=1), placa, "Troca de óleo", "Troca de óleo e filtro", round(random.uniform(280, 420), 2), km + 15, "PIX", "Pago", resp, ""]); last_oil = km
            if (d - last_prev).days > 180:
                despesas.append([d + dt.timedelta(days=2), placa, "Manutenção preventiva", "Revisão periódica", round(random.uniform(700, 1400), 2), km + 30, "Boleto", "Pago", resp, ""]); last_prev = d
            d += dt.timedelta(days=random.randint(9, 11))
        for m in range(22):
            y, mo = 2025 + m // 12, m % 12 + 1
            md = dt.date(y, mo, random.randint(3, 26))
            if md > end: continue
            despesas.append([md, placa, "Pedágios", "Pedágios do mês", round(random.uniform(60, 220), 2), None, "Cartão de crédito", "Pago", resp, ""])
            if random.random() < 0.7:
                despesas.append([md + dt.timedelta(days=1), placa, "Lavagem", "Lavagem completa", round(random.uniform(45, 90), 2), None, "Dinheiro", "Pago", resp, ""])
            if random.random() < 0.5:
                despesas.append([md + dt.timedelta(days=2), placa, "Estacionamento", "Estacionamento centro", round(random.uniform(25, 120), 2), None, "Cartão de débito", "Pago", resp, ""])
        despesas.append([dt.date(2025, 1, 14), placa, "Seguro", "Renovação anual do seguro", {"ABC1D23": 2990.0, "BRA2E19": 2150.0, "XYZ9876": 4620.0}[placa], None, "Boleto", "Pago", resp, "Apólice 2025"])
        despesas.append([dt.date(2025, 2, 18), placa, "IPVA e licenciamento", "IPVA 2025 + licenciamento", {"ABC1D23": 2610.0, "BRA2E19": 1390.0, "XYZ9876": 5380.0}[placa], None, "PIX", "Pago", resp, "Cota única"])
        despesas.append([dt.date(2026, 1, 15), placa, "Seguro", "Renovação anual do seguro", {"ABC1D23": 3150.0, "BRA2E19": 2280.0, "XYZ9876": 4890.0}[placa], None, "Boleto", "Pago", resp, "Apólice 2026"])
        despesas.append([dt.date(2026, 2, 20), placa, "IPVA e licenciamento", "IPVA 2026 + licenciamento", {"ABC1D23": 2480.0, "BRA2E19": 1310.0, "XYZ9876": 5120.0}[placa], None, "PIX", "Pago", resp, "Cota única"])
    despesas += [
        [dt.date(2026, 3, 9), "XYZ9876", "Manutenção corretiva", "Troca do alternador", 1860.0, None, "Cartão de crédito", "Pago", "Marcos Pereira", ""],
        [dt.date(2026, 5, 22), "ABC1D23", "Pneus", "4 pneus novos + alinhamento", 2340.0, None, "Cartão de crédito", "Pago", "Carlos Souza", "Parcelado 3x"],
        [dt.date(2026, 6, 4), "BRA2E19", "Multas", "Excesso de velocidade (média)", 130.16, None, "Boleto", "Pago", "Ana Lima", "Auto de infração exemplo"],
        [dt.date(2026, 8, 18), "XYZ9876", "Multas", "Estacionamento proibido", 195.23, None, "Boleto", "Atrasado", "Marcos Pereira", "Vencida"],
        [dt.date(2026, 9, 30), "XYZ9876", "Manutenção corretiva", "Reparo no câmbio", 3480.0, None, "Faturado", "Agendado", "Marcos Pereira", "Pagamento em 30 dias"],
        [dt.date(2025, 11, 12), "BRA2E19", "Manutenção corretiva", "Troca da bateria", 620.0, None, "PIX", "Pago", "Ana Lima", ""],
        [dt.date(2026, 10, 14), "ABC1D23", "Outros", "Kit de emergência e extintor", 185.9, None, "PIX", "Pendente", "Carlos Souza", ""],
    ]
    despesas.sort(key=lambda r: r[0])
    for r in despesas:
        if r[0] >= dt.date(2026, 10, 15) and r[2] == "Combustível":
            r[7] = "Pendente"

# ---------------------------------------------------------------- Veículos
ws = ws_v
vh = ["Código", "Placa", "Marca e modelo", "Ano", "Responsável", "Quilometragem atual", "Status",
      "Observações", "Total gasto (geral)", "Nº de lançamentos", "KM inicial registrado",
      "KM final registrado", "KM percorrido", "Custo por km (geral)", "Último lançamento"]
title(ws, "Cadastro de veículos", "Cadastre cada veículo uma vez. As colunas cinza são calculadas automaticamente a partir da aba Despesas.", len(vh))
header_row(ws, 4, vh, auto_from=9)
T = "tblVeiculos"; TD = "tblDespesas"
vcalc = {
    9: f"SUMIFS({TD}[Valor],{TD}[Placa],{T}[[#This Row],[Placa]])",
    10: f"COUNTIFS({TD}[Placa],{T}[[#This Row],[Placa]],{TD}[Valor],\">0\")",
    11: f"IF(COUNTIFS({TD}[Placa],{T}[[#This Row],[Placa]],{TD}[KM no lançamento],\">0\")=0,\"\",_xlfn.MINIFS({TD}[KM no lançamento],{TD}[Placa],{T}[[#This Row],[Placa]],{TD}[KM no lançamento],\">0\"))",
    12: f"IF(COUNTIFS({TD}[Placa],{T}[[#This Row],[Placa]],{TD}[KM no lançamento],\">0\")=0,\"\",MAX(_xlfn.MAXIFS({TD}[KM no lançamento],{TD}[Placa],{T}[[#This Row],[Placa]]),N({T}[[#This Row],[Quilometragem atual]])))",
    13: f"IF(OR({T}[[#This Row],[KM inicial registrado]]=\"\",{T}[[#This Row],[KM final registrado]]=\"\"),\"\",IF({T}[[#This Row],[KM final registrado]]-{T}[[#This Row],[KM inicial registrado]]>0,{T}[[#This Row],[KM final registrado]]-{T}[[#This Row],[KM inicial registrado]],\"\"))",
    14: f"IF(N({T}[[#This Row],[KM percorrido]])=0,\"n/d\",{T}[[#This Row],[Total gasto (geral)]]/{T}[[#This Row],[KM percorrido]])",
    15: f"IF({T}[[#This Row],[Placa]]=\"\",\"\",IF(COUNTIFS({TD}[Placa],{T}[[#This Row],[Placa]])=0,\"\",_xlfn.MAXIFS({TD}[Data],{TD}[Placa],{T}[[#This Row],[Placa]])))",
}
rows_v = veiculos if True else []
for i, (cod, placa, mod, ano, resp, st, obs) in enumerate(rows_v):
    r = 5 + i
    kms = [d[5] for d in despesas if d[1] == placa and d[5]]
    vals = [cod, placa, mod, ano, resp, (max(kms) if kms else None), st, obs]
    if not WITH_DATA:
        vals = [None] * 8
    for c, v in enumerate(vals, 1):
        ws.cell(r, c, v)
    for c, f in vcalc.items():
        ws.cell(r, c, "=" + f)
    if not WITH_DATA:
        break
nv_last = 5 + (len(rows_v) - 1 if WITH_DATA else 0)
fmt_v = {6: KMF, 9: BRL, 10: "0", 11: KMF, 12: KMF, 13: KMF, 14: '"R$" #,##0.000', 15: DATE, 4: "0"}
for r in range(5, nv_last + 1):
    for c in range(1, len(vh) + 1):
        cell = ws.cell(r, c); cell.font = font(); cell.border = BOX
        if c in fmt_v: cell.number_format = fmt_v[c]
        if c >= 9: cell.fill = fill(LGRAY)
tv = Table(displayName=T, ref=f"A4:{chr(64 + len(vh))}{nv_last}")
tv.tableStyleInfo = TableStyleInfo(name="TableStyleLight9", showRowStripes=True)
tv._initialise_columns()
for c, f in vcalc.items():
    tv.tableColumns[c - 1].calculatedColumnFormula = TableFormula(attr_text=f)
ws.add_table(tv)
for col, w in zip("ABCDEFGHIJKLMNO", [9, 11, 22, 7, 17, 13, 15, 30, 15, 11, 13, 13, 13, 13, 13]):
    ws.column_dimensions[col].width = w
ws.freeze_panes = "C5"; ws.sheet_view.showGridLines = False
dv = DataValidation(type="list", formula1="=lstStatusVe", allow_blank=True, showErrorMessage=True,
                    errorTitle="Status inválido", error="Escolha um status da lista.")
dv.add("G5:G1000"); ws.add_data_validation(dv)
dv = DataValidation(type="whole", operator="between", formula1="1980", formula2="2100", allow_blank=True,
                    showErrorMessage=True, errorTitle="Ano inválido", error="Informe o ano com 4 dígitos (1980 a 2100).")
dv.add("D5:D1000"); ws.add_data_validation(dv)
dv = DataValidation(type="whole", operator="greaterThanOrEqual", formula1="0", allow_blank=True,
                    showErrorMessage=True, errorTitle="KM inválida", error="A quilometragem deve ser um número inteiro maior ou igual a zero.")
dv.add("F5:F1000"); ws.add_data_validation(dv)
dv = DataValidation(type="custom", formula1='=COUNTIF($B$5:$B$1000,B5)<=1', allow_blank=True,
                    showErrorMessage=True, errorTitle="Placa repetida", error="Esta placa já está cadastrada.")
dv.add("B5:B1000"); ws.add_data_validation(dv)
ws.conditional_formatting.add("A5:O1000", FormulaRule(formula=['$G5="Em manutenção"'], fill=fill(LAMBER), font=Font(color=AMBER)))
ws.conditional_formatting.add("A5:O1000", FormulaRule(formula=['OR($G5="Inativo",$G5="Vendido")'], font=Font(color="808080", italic=True)))
ws.conditional_formatting.add("N5:N1000", FormulaRule(formula=['AND(ISNUMBER($N5),ISNUMBER(kmFrotaGeral),$N5>kmFrotaGeral*(1+pTol))'], font=Font(color=RED, bold=True), fill=fill(LRED)))
ws.conditional_formatting.add("N5:N1000", FormulaRule(formula=['AND(ISNUMBER($N5),ISNUMBER(kmFrotaGeral),$N5<kmFrotaGeral*(1-pTol))'], font=Font(color=GREEN, bold=True), fill=fill(LGREEN)))
ws.auto_filter.ref = None
name("lstPlacas", f"{T}[Placa]")

# ---------------------------------------------------------------- Despesas
ws = ws_e
eh = ["Data", "Placa", "Categoria", "Descrição", "Valor", "KM no lançamento", "Forma de pagamento",
      "Status do pagamento", "Responsável", "Observações", "Grupo", "NoFiltro", "ValorFiltro"]
title(ws, "Lançamento de despesas", "Inclua novas despesas na primeira linha vazia abaixo da tabela: formatação, validações e fórmulas se estendem sozinhas.", len(eh))
header_row(ws, 4, eh, auto_from=11)
ecalc = {
    11: f"IFERROR(INDEX(lstGrupoCat,MATCH({TD}[[#This Row],[Categoria]],lstCategorias,0)),\"\")",
    12: f"IF(AND(ISNUMBER({TD}[[#This Row],[Data]]),{TD}[[#This Row],[Data]]>=pIni,{TD}[[#This Row],[Data]]<=pFim,OR(fVeic=\"Todos\",{TD}[[#This Row],[Placa]]=fVeic),OR(fCat=\"Todos\",{TD}[[#This Row],[Categoria]]=fCat)),1,0)",
    13: f"IF({TD}[[#This Row],[NoFiltro]]=1,N({TD}[[#This Row],[Valor]]),\"\")",
}
rows_e = despesas if WITH_DATA else [[None] * 10]
for i, d in enumerate(rows_e):
    r = 5 + i
    for c, v in enumerate(d, 1):
        if v is not None and v != "":
            ws.cell(r, c, v)
    for c, f in ecalc.items():
        ws.cell(r, c, "=" + f)
ne_last = 4 + len(rows_e)
fmt_e = {1: DATE, 5: BRL, 6: KMF, 13: BRL}
for r in range(5, ne_last + 1):
    for c in range(1, len(eh) + 1):
        cell = ws.cell(r, c); cell.font = font(); cell.border = BOX
        if c in fmt_e: cell.number_format = fmt_e[c]
        if c >= 11: cell.fill = fill(LGRAY); cell.font = font(color="808080")
te = Table(displayName=TD, ref=f"A4:M{ne_last}")
te.tableStyleInfo = TableStyleInfo(name="TableStyleLight9", showRowStripes=True)
te._initialise_columns()
for c, f in ecalc.items():
    te.tableColumns[c - 1].calculatedColumnFormula = TableFormula(attr_text=f)
ws.add_table(te)
for col, w in zip("ABCDEFGHIJKLM", [12, 11, 22, 32, 14, 14, 19, 15, 17, 26, 13, 9, 13]):
    ws.column_dimensions[col].width = w
ws.freeze_panes = "B5"; ws.sheet_view.showGridLines = True
ws["L4"].comment = Comment("1 = o lançamento entra nos filtros do Dashboard (período, veículo e categoria). Não edite.", "Planilha")
ws["K4"].comment = Comment("Grupo da categoria, buscado na aba Listas. Não edite.", "Planilha")
for rng, kw in [
    ("A5:A5000", dict(type="date", operator="between", formula1="DATE(2000,1,1)", formula2="DATE(2100,12,31)",
                      errorTitle="Data inválida", error="Informe uma data válida no formato dd/mm/aaaa.")),
    ("B5:B5000", dict(type="list", formula1="=lstPlacas", errorTitle="Veículo não cadastrado", error="Escolha uma placa cadastrada na aba Veículos.")),
    ("C5:C5000", dict(type="list", formula1="=lstCategorias", errorTitle="Categoria inválida", error="Escolha uma categoria da lista.")),
    ("E5:E5000", dict(type="decimal", operator="between", formula1="0.01", formula2="10000000", errorTitle="Valor inválido", error="O valor deve ser maior que zero.")),
    ("F5:F5000", dict(type="whole", operator="greaterThanOrEqual", formula1="0", errorTitle="KM inválida", error="A quilometragem deve ser um número inteiro maior ou igual a zero.")),
    ("G5:G5000", dict(type="list", formula1="=lstFormas", errorTitle="Forma inválida", error="Escolha uma forma de pagamento da lista.")),
    ("H5:H5000", dict(type="list", formula1="=lstStatusPg", errorTitle="Status inválido", error="Escolha um status da lista.")),
]:
    dv = DataValidation(allow_blank=True, showErrorMessage=True, **kw); dv.add(rng); ws.add_data_validation(dv)
ws.conditional_formatting.add("A5:J5000", FormulaRule(formula=['$C5="Multas"'], fill=fill(LRED), font=Font(color=RED)))
ws.conditional_formatting.add("H5:H5000", FormulaRule(formula=['$H5="Atrasado"'], fill=fill(RED), font=Font(color=WHITE, bold=True)))
ws.conditional_formatting.add("H5:H5000", FormulaRule(formula=['OR($H5="Pendente",$H5="Agendado")'], fill=fill(LAMBER), font=Font(color=AMBER, bold=True)))
ws.conditional_formatting.add("H5:H5000", FormulaRule(formula=['$H5="Pago"'], fill=fill(LBLUE), font=Font(color=BLUE)))
ws.conditional_formatting.add("E5:E5000", FormulaRule(formula=['AND(ISNUMBER($E5),$E5>=pAlerta)'], font=Font(color=RED, bold=True)))

# ---------------------------------------------------------------- Cálculos
ws = ws_c
title(ws, "Cálculos de apoio", "Fórmulas que alimentam o Dashboard e os gráficos. Não é preciso editar esta aba.", 14)
calc = [
    ("Filtro: ano", "=Dashboard!$C$5", "fAno", None),
    ("Filtro: mês", "=Dashboard!$F$5", "fMes", None),
    ("Filtro: veículo", "=Dashboard!$I$5", "fVeic", None),
    ("Filtro: categoria", "=Dashboard!$L$5", "fCat", None),
    ("Nº do mês selecionado (0 = todos)", '=IF(fMes="Todos",0,MATCH(fMes,lstMeses,0))', "pMesNum", "0"),
    ("Primeira data lançada", f"=IF(COUNT({TD}[Data])=0,0,MIN({TD}[Data]))", "dMin", DATE),
    ("Última data lançada", f"=IF(COUNT({TD}[Data])=0,0,MAX({TD}[Data]))", "dMax", DATE),
    ("Ano efetivo do período", '=IF(fAno="Todos",IF(pMesNum>0,YEAR(dMax),"Todos"),fAno)', "pAno", None),
    ("Início do período", '=IF(pAno="Todos",dMin,IF(pMesNum=0,DATE(pAno,1,1),DATE(pAno,pMesNum,1)))', "pIni", DATE),
    ("Fim do período", '=IF(pAno="Todos",dMax,IF(pMesNum=0,DATE(pAno,12,31),EOMONTH(pIni,0)))', "pFim", DATE),
    ("Existe período anterior comparável? (1/0)", '=IF(pAno="Todos",0,1)', "pTemAnt", "0"),
    ("Início do período anterior", '=IF(pTemAnt=0,0,IF(pMesNum=0,DATE(pAno-1,1,1),EDATE(pIni,-1)))', "aIni", DATE),
    ("Fim do período anterior", '=IF(pTemAnt=0,0,IF(pMesNum=0,IF(OR(dMax=0,dMax<pIni,dMax>=pFim),DATE(pAno-1,12,31),EOMONTH(dMax,-12)),EOMONTH(aIni,0)))', "aFim", DATE),
    ("Critério de veículo", '=IF(fVeic="Todos","*",fVeic)', "cVeic", None),
    ("Critério de categoria", '=IF(fCat="Todos","*",fCat)', "cCat", None),
    ("Total do período", f"=SUMIFS({TD}[Valor],{TD}[NoFiltro],1)", "kTotal", BRL),
    ("Lançamentos no período", f"=COUNTIFS({TD}[NoFiltro],1,{TD}[Valor],\">0\")", "kQtd", "0"),
    ("Total do período anterior", f'=IF(pTemAnt=0,0,SUMIFS({TD}[Valor],{TD}[Data],">="&aIni,{TD}[Data],"<="&aFim,{TD}[Placa],cVeic,{TD}[Categoria],cCat))', "kAnt", BRL),
    ("Variação em R$", '=IF(pTemAnt=0,"s/ comparação",kTotal-kAnt)', "kVarRS", BRL_SIGN),
    ("Variação %", '=IF(pTemAnt=0,"s/ comparação",IF(kAnt=0,"s/ dados anteriores",(kTotal-kAnt)/kAnt))', "kVarPct", PCT_ARROW),
    ("Última data no filtro", f"=IF(kQtd=0,0,_xlfn.MAXIFS({TD}[Data],{TD}[NoFiltro],1))", "kUlt", DATE),
    ("Mês de referência: início", '=IF(pMesNum>0,pIni,IF(kUlt=0,0,DATE(YEAR(kUlt),MONTH(kUlt),1)))', "mIni", DATE),
    ("Mês de referência: fim", '=IF(mIni=0,0,EOMONTH(mIni,0))', "mFim", DATE),
    ("Total do mês de referência", f'=IF(mIni=0,0,SUMIFS({TD}[Valor],{TD}[Data],">="&mIni,{TD}[Data],"<="&mFim,{TD}[Placa],cVeic,{TD}[Categoria],cCat))', "kMes", BRL),
    ("Rótulo do mês de referência", '=IF(mIni=0,"sem dados",INDEX(lstMesAbrev,MONTH(mIni))&"/"&YEAR(mIni))', "kMesRot", None),
    ("Meses considerados na média", '=IF(OR(dMax=0,MIN(pFim,dMax)<pIni),0,(YEAR(MIN(pFim,dMax))-YEAR(pIni))*12+MONTH(MIN(pFim,dMax))-MONTH(pIni)+1)', "kMeses", "0"),
    ("Média mensal", '=IF(kMeses=0,"s/ dados",kTotal/kMeses)', "kMedia", BRL),
    ("Total combustível", f'=SUMIFS({TD}[Valor],{TD}[NoFiltro],1,{TD}[Grupo],"Combustível")', "kComb", BRL),
    ("Total manutenção", f'=SUMIFS({TD}[Valor],{TD}[NoFiltro],1,{TD}[Grupo],"Manutenção")', "kManut", BRL),
    ("Maior despesa (valor)", f"=IF(kQtd=0,0,_xlfn.MAXIFS({TD}[Valor],{TD}[NoFiltro],1))", "kMaior", BRL),
    ("Maior despesa (linha)", f"=IF(kMaior=0,0,MATCH(kMaior,{TD}[ValorFiltro],0))", "kMaiorLin", "0"),
    ("Maior despesa: placa", f'=IF(kMaiorLin=0,"—",INDEX({TD}[Placa],kMaiorLin))', "kMaiorPlaca", None),
    ("Maior despesa: categoria", f'=IF(kMaiorLin=0,"",INDEX({TD}[Categoria],kMaiorLin))', "kMaiorCat", None),
    ("Maior despesa: data", f'=IF(kMaiorLin=0,"",INDEX({TD}[Data],kMaiorLin))', "kMaiorData", DATE),
    ("Veículo mais caro: placa", '=IF(MAX(vTotal)<=0,"—",INDEX(vPlaca,MATCH(MAX(vScore),vScore,0)))', "kTopPlaca", None),
    ("Veículo mais caro: valor", '=IF(MAX(vTotal)<=0,0,MAX(vTotal))', "kTopValor", BRL),
    ("Veículos com despesas no filtro", '=COUNTIF(vTotal,">0")', "kQtdVeic", "0"),
    ("KM percorrida (veículos com 2+ leituras)", '=SUM(vKm)', "kKm", KMF),
    ("Custo por km da frota", '=IF(kKm=0,"n/d",SUMIFS(vTotal,vKm,">0")/kKm)', "kCustoKm", '"R$" #,##0.000'),
    ("Custo por km geral (todo o histórico)", f'=IF(SUM({T}[KM percorrido])=0,"n/d",SUMIFS({T}[Total gasto (geral)],{T}[KM percorrido],">0")/SUM({T}[KM percorrido]))', "kmFrotaGeral", '"R$" #,##0.000'),
    ("Ano exibido nos gráficos mensais", '=IF(pAno="Todos",IF(dMax=0,YEAR(TODAY()),YEAR(dMax)),pAno)', "gAno", "0"),
    ("Descrição do período", None, "kPeriodo", None),
]
ws["A4"] = "Indicador"; ws["B4"] = "Valor"; ws["C4"] = "Nome definido"
for c in ("A4", "B4", "C4"): ws[c].font = font(bold=True, color=WHITE); ws[c].fill = fill(BLUE)
def dtxt(n): return f'RIGHT("0"&DAY({n}),2)&"/"&RIGHT("0"&MONTH({n}),2)&"/"&YEAR({n})'
for i, (lab, f, nm, fmt) in enumerate(calc):
    r = 5 + i
    if nm == "kPeriodo":
        f = ('=IF(OR(pIni=0,pFim=0),"Período analisado: sem lançamentos",'
             f'"Período analisado: "&{dtxt("pIni")}&" a "&{dtxt("pFim")}&'
             '"   |   Veículo: "&fVeic&"   |   Categoria: "&fCat)')
    ws.cell(r, 1, lab).font = font()
    c = ws.cell(r, 2, f); c.font = font(); c.border = BOTTOM
    if fmt: c.number_format = fmt
    ws.cell(r, 3, nm).font = font(color="808080", italic=True)
    name(nm, f"'Cálculos'!$B${r}")
last_calc = 4 + len(calc)

# bloco por veículo
vb = last_calc + 3
ws.cell(vb - 1, 1, "Totais por veículo no filtro atual").font = font(bold=True, size=11, color=NAVY)
vbh = ["#", "Placa", "Total no filtro", "Leituras de KM", "KM inicial", "KM final", "KM percorrida",
       "Custo por km", "Chave de ordenação", "Ranking: placa", "Ranking: total"]
for i, h in enumerate(vbh, 1):
    c = ws.cell(vb, i, h); c.font = font(bold=True, color=WHITE); c.fill = fill(BLUE)
    c.alignment = Alignment(wrap_text=True, horizontal="center")
v0, v1 = vb + 1, vb + SLOTS
for k in range(1, SLOTS + 1):
    r = vb + k
    ws.cell(r, 1, k)
    ws.cell(r, 2, f'=IFERROR(INDEX({T}[Placa],A{r})&"","")')
    ws.cell(r, 3, f'=IF(B{r}="","",SUMIFS({TD}[Valor],{TD}[NoFiltro],1,{TD}[Placa],B{r}))')
    ws.cell(r, 4, f'=IF(B{r}="","",COUNTIFS({TD}[Placa],B{r},{TD}[Data],">="&pIni,{TD}[Data],"<="&pFim,{TD}[KM no lançamento],">0"))')
    ws.cell(r, 5, f'=IF(N(D{r})<2,"",_xlfn.MINIFS({TD}[KM no lançamento],{TD}[Placa],B{r},{TD}[Data],">="&pIni,{TD}[Data],"<="&pFim,{TD}[KM no lançamento],">0"))')
    ws.cell(r, 6, f'=IF(N(D{r})<2,"",_xlfn.MAXIFS({TD}[KM no lançamento],{TD}[Placa],B{r},{TD}[Data],">="&pIni,{TD}[Data],"<="&pFim))')
    ws.cell(r, 7, f'=IF(OR(E{r}="",N(C{r})=0),0,F{r}-E{r})')
    ws.cell(r, 8, f'=IF(N(G{r})=0,"n/d",C{r}/G{r})')
    ws.cell(r, 9, f'=IF(B{r}="",-1,C{r}-A{r}/1000000)')
    ws.cell(r, 10, f'=IF(LARGE($I${v0}:$I${v1},A{r})<=0,"",INDEX($B${v0}:$B${v1},MATCH(LARGE($I${v0}:$I${v1},A{r}),$I${v0}:$I${v1},0)))')
    ws.cell(r, 11, f'=IF(J{r}="",0,SUMIFS($C${v0}:$C${v1},$B${v0}:$B${v1},J{r}))')
    for c, fm in {3: BRL, 5: KMF, 6: KMF, 7: KMF, 8: '"R$" #,##0.000', 11: BRL}.items():
        ws.cell(r, c).number_format = fm
    for c in range(1, 12): ws.cell(r, c).font = font()
name("vPlaca", f"'Cálculos'!$B${v0}:$B${v1}")
name("vTotal", f"'Cálculos'!$C${v0}:$C${v1}")
name("vKm", f"'Cálculos'!$G${v0}:$G${v1}")
name("vScore", f"'Cálculos'!$I${v0}:$I${v1}")
# lista de filtro de veículo
ws.cell(vb, 13, "Lista: filtro de veículo").font = font(bold=True, color=WHITE); ws.cell(vb, 13).fill = fill(BLUE)
ws.cell(v0, 13, "Todos").font = font()
for k in range(1, SLOTS + 1):
    ws.cell(v0 + k, 13, f"=B{vb + k}").font = font()
name("lstFiltroVeic", f"OFFSET('Cálculos'!$M${v0},0,0,COUNTIF('Cálculos'!$M${v0}:$M${v1 + 1},\"?*\"),1)")
for col, w in zip("ABCDEFGHIJKLM", [44, 18, 14, 10, 12, 12, 13, 12, 12, 14, 14, 2, 22]):
    ws.column_dimensions[col].width = w
ws.sheet_view.showGridLines = False

# ---------------------------------------------------------------- Resumo
ws = ws_r
title(ws, "Resumo financeiro da frota", "Tabelas consolidadas que seguem os filtros do Dashboard. Base dos gráficos.", 14)
ws["A3"] = "=kPeriodo"; ws["A3"].font = font(italic=True, color=BLUE)
crit_vc = f'{TD}[Placa],cVeic,{TD}[Categoria],cCat'
# 1) mensal
r0 = 5
ws.cell(r0, 1, '="Total mensal: "&gAno&" comparado a "&(gAno-1)&" (segue os filtros de veículo e categoria)"').font = font(bold=True, size=11, color=NAVY)
mh = ["Mês", "Ano atual", "Ano anterior", "Diferença (R$)", "Variação %", "Combustível", "Manutenção", "Lançamentos"]
for i, h in enumerate(mh, 1):
    c = ws.cell(r0 + 1, i, h); c.font = font(bold=True, color=WHITE); c.fill = fill(BLUE); c.alignment = Alignment(horizontal="center", wrap_text=True)
ws.cell(r0 + 1, 2, '=gAno&""'); ws.cell(r0 + 1, 3, '=(gAno-1)&""')
m0 = r0 + 2
for m in range(1, 13):
    r = m0 + m - 1
    a = f'DATE(gAno,{m},1)'; b = f'EOMONTH(DATE(gAno,{m},1),0)'
    pa = f'DATE(gAno-1,{m},1)'; pb = f'EOMONTH(DATE(gAno-1,{m},1),0)'
    ws.cell(r, 1, MESES_AB[m - 1])
    ws.cell(r, 2, f'=SUMIFS({TD}[Valor],{TD}[Data],">="&{a},{TD}[Data],"<="&{b},{crit_vc})')
    ws.cell(r, 3, f'=SUMIFS({TD}[Valor],{TD}[Data],">="&{pa},{TD}[Data],"<="&{pb},{crit_vc})')
    ws.cell(r, 4, f'=IF(AND(B{r}=0,C{r}=0),"",B{r}-C{r})')
    ws.cell(r, 5, f'=IF(OR(B{r}=0,C{r}=0),"",(B{r}-C{r})/C{r})')
    ws.cell(r, 6, f'=SUMIFS({TD}[Valor],{TD}[Data],">="&{a},{TD}[Data],"<="&{b},{crit_vc},{TD}[Grupo],"Combustível")')
    ws.cell(r, 7, f'=SUMIFS({TD}[Valor],{TD}[Data],">="&{a},{TD}[Data],"<="&{b},{crit_vc},{TD}[Grupo],"Manutenção")')
    ws.cell(r, 8, f'=COUNTIFS({TD}[Data],">="&{a},{TD}[Data],"<="&{b},{crit_vc},{TD}[Valor],">0")')
m1 = m0 + 11
rt = m1 + 1
ws.cell(rt, 1, "Total")
for c, L in zip(range(2, 9), "BCDEFGH"):
    if c == 4: ws.cell(rt, c, f"=IF(AND(B{rt}=0,C{rt}=0),\"\",B{rt}-C{rt})")
    elif c == 5: ws.cell(rt, c, f'=IF(OR(B{rt}=0,C{rt}=0),"",(B{rt}-C{rt})/C{rt})')
    else: ws.cell(rt, c, f"=SUM({L}{m0}:{L}{m1})")
for r in range(m0, rt + 1):
    for c in range(1, 9):
        cell = ws.cell(r, c); cell.font = font(bold=(r == rt)); cell.border = BOX
        cell.number_format = {5: PCT_ARROW, 4: BRL_SIGN, 8: "0"}.get(c, BRL if c > 1 else "General")
        if r == rt: cell.fill = fill(LBLUE)
for rng in (f"D{m0}:E{rt}",):
    pass
ws.conditional_formatting.add(f"D{m0}:D{rt}", CellIsRule(operator="greaterThan", formula=["0"], font=Font(color=RED, bold=True)))
ws.conditional_formatting.add(f"D{m0}:D{rt}", CellIsRule(operator="lessThan", formula=["0"], font=Font(color=GREEN, bold=True)))
ws.conditional_formatting.add(f"E{m0}:E{rt}", FormulaRule(formula=[f'AND(ISNUMBER(E{m0}),E{m0}>0)'], fill=fill(LRED), font=Font(color=RED, bold=True)))
ws.conditional_formatting.add(f"E{m0}:E{rt}", FormulaRule(formula=[f'AND(ISNUMBER(E{m0}),E{m0}<0)'], fill=fill(LGREEN), font=Font(color=GREEN, bold=True)))

# 2) categorias
c0 = rt + 3
ws.cell(c0, 1, "Gasto por categoria no período selecionado").font = font(bold=True, size=11, color=NAVY)
ch = ["Categoria", "Grupo", "Total", "% do total", "Lançamentos"]
for i, h in enumerate(ch, 1):
    c = ws.cell(c0 + 1, i, h); c.font = font(bold=True, color=WHITE); c.fill = fill(BLUE); c.alignment = Alignment(horizontal="center")
k0 = c0 + 2
for i, (cat, g) in enumerate(CATEGORIAS):
    r = k0 + i
    ws.cell(r, 1, f"=INDEX(lstCategorias,{i + 1})"); ws.cell(r, 2, f"=INDEX(lstGrupoCat,{i + 1})")
    ws.cell(r, 3, f'=SUMIFS({TD}[Valor],{TD}[NoFiltro],1,{TD}[Categoria],A{r})')
    ws.cell(r, 4, f'=IF(kTotal=0,"",C{r}/kTotal)')
    ws.cell(r, 5, f'=COUNTIFS({TD}[NoFiltro],1,{TD}[Categoria],A{r},{TD}[Valor],">0")')
k1 = k0 + n_cat - 1
ws.cell(k1 + 1, 1, "Total"); ws.cell(k1 + 1, 3, f"=SUM(C{k0}:C{k1})"); ws.cell(k1 + 1, 4, f'=IF(kTotal=0,"",C{k1 + 1}/kTotal)'); ws.cell(k1 + 1, 5, f"=SUM(E{k0}:E{k1})")
for r in range(k0, k1 + 2):
    for c in range(1, 6):
        cell = ws.cell(r, c); cell.font = font(bold=(r == k1 + 1)); cell.border = BOX
        cell.number_format = {3: BRL, 4: "0.0%", 5: "0"}.get(c, "General")
        if r == k1 + 1: cell.fill = fill(LBLUE)
ws.conditional_formatting.add(f"A{k0}:E{k1}", FormulaRule(formula=[f'AND($A{k0}="Multas",$C{k0}>0)'], fill=fill(LRED), font=Font(color=RED, bold=True)))
ws.conditional_formatting.add(f"C{k0}:C{k1}", ColorScaleRule(start_type="min", start_color="FFFFFF", end_type="max", end_color="F4B6B0"))

# 3) comparativo por grupo
g0 = k1 + 4
ws.cell(g0, 1, "Período atual x período anterior, por grupo de despesa").font = font(bold=True, size=11, color=NAVY)
gh = ["Grupo", "Período atual", "Período anterior", "Diferença (R$)", "Variação %"]
for i, h in enumerate(gh, 1):
    c = ws.cell(g0 + 1, i, h); c.font = font(bold=True, color=WHITE); c.fill = fill(BLUE); c.alignment = Alignment(horizontal="center")
q0 = g0 + 2
for i, g in enumerate(GRUPOS):
    r = q0 + i
    ws.cell(r, 1, g)
    ws.cell(r, 2, f'=SUMIFS({TD}[Valor],{TD}[NoFiltro],1,{TD}[Grupo],A{r})')
    ws.cell(r, 3, f'=IF(pTemAnt=0,0,SUMIFS({TD}[Valor],{TD}[Data],">="&aIni,{TD}[Data],"<="&aFim,{crit_vc},{TD}[Grupo],A{r}))')
    ws.cell(r, 4, f'=IF(pTemAnt=0,"",B{r}-C{r})')
    ws.cell(r, 5, f'=IF(OR(pTemAnt=0,C{r}=0),"",(B{r}-C{r})/C{r})')
q1 = q0 + len(GRUPOS) - 1
ws.cell(q1 + 1, 1, "Total")
ws.cell(q1 + 1, 2, f"=SUM(B{q0}:B{q1})"); ws.cell(q1 + 1, 3, f"=SUM(C{q0}:C{q1})")
ws.cell(q1 + 1, 4, f'=IF(pTemAnt=0,"",B{q1 + 1}-C{q1 + 1})'); ws.cell(q1 + 1, 5, f'=IF(OR(pTemAnt=0,C{q1 + 1}=0),"",(B{q1 + 1}-C{q1 + 1})/C{q1 + 1})')
for r in range(q0, q1 + 2):
    for c in range(1, 6):
        cell = ws.cell(r, c); cell.font = font(bold=(r == q1 + 1)); cell.border = BOX
        cell.number_format = {2: BRL, 3: BRL, 4: BRL_SIGN, 5: PCT_ARROW}.get(c, "General")
        if r == q1 + 1: cell.fill = fill(LBLUE)
ws.conditional_formatting.add(f"D{q0}:E{q1 + 1}", FormulaRule(formula=[f'AND(ISNUMBER(D{q0}),D{q0}>0)'], fill=fill(LRED), font=Font(color=RED, bold=True)))
ws.conditional_formatting.add(f"D{q0}:E{q1 + 1}", FormulaRule(formula=[f'AND(ISNUMBER(D{q0}),D{q0}<0)'], fill=fill(LGREEN), font=Font(color=GREEN, bold=True)))

# 4) matriz veículo x categoria
x0 = q1 + 4
ws.cell(x0, 1, "Gasto por veículo e categoria no período selecionado").font = font(bold=True, size=11, color=NAVY)
ws.cell(x0 + 1, 1, "Placa")
for i, (cat, _) in enumerate(CATEGORIAS):
    ws.cell(x0 + 1, 2 + i, f"=INDEX(lstCategorias,{i + 1})")
ws.cell(x0 + 1, 2 + n_cat, "Total")
for c in range(1, 3 + n_cat):
    cell = ws.cell(x0 + 1, c); cell.font = font(bold=True, color=WHITE); cell.fill = fill(BLUE)
    cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
ws.row_dimensions[x0 + 1].height = 32
y0 = x0 + 2
for k in range(SLOTS):
    r = y0 + k
    ws.cell(r, 1, f"=INDEX(vPlaca,{k + 1})")
    for i in range(n_cat):
        col = ws.cell(x0 + 1, 2 + i).column_letter
        ws.cell(r, 2 + i, f'=IF($A{r}="","",SUMIFS({TD}[Valor],{TD}[NoFiltro],1,{TD}[Placa],$A{r},{TD}[Categoria],{col}${x0 + 1}))')
    last_col = ws.cell(r, 1 + n_cat).column_letter
    ws.cell(r, 2 + n_cat, f'=IF($A{r}="","",SUM(B{r}:{last_col}{r}))')
    for c in range(1, 3 + n_cat):
        cell = ws.cell(r, c); cell.font = font(bold=(c == 2 + n_cat)); cell.border = BOX
        if c > 1: cell.number_format = '"R$" #,##0.00;-"R$" #,##0.00;"-"'
y1 = y0 + SLOTS - 1
ws.conditional_formatting.add(f"B{y0}:{ws.cell(y1, 1 + n_cat).column_letter}{y1}", ColorScaleRule(start_type="num", start_value=0, start_color="FFFFFF", end_type="max", end_color="F4B6B0"))
ws.column_dimensions["A"].width = 16
for i in range(2, 3 + n_cat):
    ws.column_dimensions[ws.cell(1, i).column_letter].width = 14
ws.sheet_view.showGridLines = False
ws.freeze_panes = "B4"
ws.print_area = f"A1:{ws.cell(1, 2 + n_cat).column_letter}{y1}"
ws.page_setup.orientation = "landscape"; ws.page_setup.paperSize = ws.PAPERSIZE_A4
ws.page_setup.fitToWidth = 1; ws.page_setup.fitToHeight = 0; ws.sheet_properties.pageSetUpPr.fitToPage = True

# ---------------------------------------------------------------- Dashboard
ws = ws_d
NC = 16  # A..P
title(ws, "Dashboard de despesas da frota", "Escolha ano, mês, veículo e categoria nas células amarelas. Todos os indicadores e gráficos se atualizam.", NC)
ws["A3"] = "=kPeriodo"; ws["A3"].font = font(italic=True, color=BLUE, size=10)
widths = {"A": 2, "B": 14, "C": 14, "D": 2, "E": 14, "F": 14, "G": 2, "H": 14, "I": 14, "J": 2, "K": 14, "L": 14, "M": 2, "N": 14, "O": 14, "P": 2}
for k, v in widths.items(): ws.column_dimensions[k].width = v
# filtros (linha 5)
filtros = [("B5", "Ano", "C5", "lstAnos", (2026 if WITH_DATA else "Todos")),
           ("E5", "Mês", "F5", "lstFiltroMes", "Todos"),
           ("H5", "Veículo", "I5", "lstFiltroVeic", "Todos"),
           ("K5", "Categoria", "L5", "lstFiltroCat", "Todos")]
for lab_c, lab, val_c, lst, default in filtros:
    ws[lab_c] = lab; ws[lab_c].font = font(bold=True, color=NAVY); ws[lab_c].alignment = Alignment(horizontal="right")
    ws[val_c] = default; ws[val_c].font = font(bold=True, color="0000FF"); ws[val_c].fill = fill("FFF2CC")
    ws[val_c].border = Border(*(Side(style="thin", color="BF9000"),) * 4); ws[val_c].alignment = Alignment(horizontal="center")
    dv = DataValidation(type="list", formula1=f"={lst}", allow_blank=False, showErrorMessage=True,
                        errorTitle="Filtro inválido", error="Escolha uma opção da lista.")
    dv.add(val_c); ws.add_data_validation(dv)
ws.row_dimensions[5].height = 20
ws["N5"] = "Dica: use “Todos” para remover um filtro."; ws["N5"].font = font(size=8, italic=True, color=GRAY)

cards = [
    # (col, row, título, fórmula valor, formato, fórmula sub, formato sub)
    ("B", 7, '="Total do mês ("&kMesRot&")"', "=kMes", BRL, '=IF(ISNUMBER(kMedia),"Média mensal: "&"R$ "&FIXED(kMedia,2,FALSE),"")', None),
    ("E", 7, "Total do período", "=kTotal", BRL, '=kQtd&" lançamentos"', None),
    ("H", 7, "Variação x período anterior", "=kVarPct", PCT_ARROW, "=kVarRS", BRL_SIGN),
    ("K", 7, "Média mensal", "=kMedia", BRL, '=IF(kMeses=0,"",kMeses&IF(kMeses=1," mês"," meses")&" no período")', None),
    ("N", 7, "Maior despesa", '=IF(kMaior=0,"—",kMaior)', BRL, '=IF(kMaiorLin=0,"",kMaiorPlaca&" · "&kMaiorCat)', None),
    ("B", 12, "Combustível", "=kComb", BRL, '=IF(kTotal=0,"",FIXED(kComb/kTotal*100,1,FALSE)&"% do total")', None),
    ("E", 12, "Manutenção", "=kManut", BRL, '=IF(kTotal=0,"",FIXED(kManut/kTotal*100,1,FALSE)&"% do total")', None),
    ("H", 12, "Veículo com maior custo", "=kTopPlaca", None, '=IF(kTopValor=0,"",kTopValor)', BRL),
    ("K", 12, "Veículos com despesas", "=kQtdVeic", "0", f'="de "&COUNTIF(vPlaca,"?*")&" cadastrados"', None),
    ("N", 12, "Custo por km", "=kCustoKm", '"R$" #,##0.000', '=IF(kKm=0,"Requer 2+ leituras de KM",FIXED(kKm,0,FALSE)&" km percorridos")', None),
]
from openpyxl.utils import column_index_from_string, get_column_letter
for col, row, tit, val, fmt, sub, subfmt in cards:
    c1 = column_index_from_string(col); c2 = c1 + 1
    for r in range(row, row + 4):
        for c in (c1, c2):
            cell = ws.cell(r, c); cell.fill = fill(WHITE)
            cell.border = Border(left=Side(style="thin", color="BDD7EE") if c == c1 else None,
                                 right=Side(style="thin", color="BDD7EE") if c == c2 else None,
                                 top=Side(style="medium", color=BLUE) if r == row else None,
                                 bottom=Side(style="thin", color="BDD7EE") if r == row + 3 else None)
            cell.alignment = Alignment(horizontal="centerContinuous", vertical="center")
    t = ws.cell(row, c1, tit); t.font = font(size=9, bold=True, color=BLUE)
    v = ws.cell(row + 1, c1, val); v.font = font(size=18, bold=True, color=NAVY)
    if fmt: v.number_format = fmt
    ws.merge_cells(start_row=row + 1, start_column=c1, end_row=row + 2, end_column=c2)
    v.alignment = Alignment(horizontal="center", vertical="center")
    s = ws.cell(row + 3, c1, sub); s.font = font(size=8, color=GRAY)
    if subfmt: s.number_format = subfmt
for r in (7, 12): ws.row_dimensions[r].height = 18
for r in (8, 9, 13, 14): ws.row_dimensions[r].height = 16
for r in (10, 15): ws.row_dimensions[r].height = 16
# formatação condicional dos cartões
ws.conditional_formatting.add("H8", FormulaRule(formula=['AND(ISNUMBER($H$8),$H$8>0)'], font=Font(color=RED, bold=True), fill=fill(LRED)))
ws.conditional_formatting.add("H8", FormulaRule(formula=['AND(ISNUMBER($H$8),$H$8<0)'], font=Font(color=GREEN, bold=True), fill=fill(LGREEN)))
ws.conditional_formatting.add("H10", FormulaRule(formula=['AND(ISNUMBER($H$10),$H$10>0)'], font=Font(color=RED)))
ws.conditional_formatting.add("H10", FormulaRule(formula=['AND(ISNUMBER($H$10),$H$10<0)'], font=Font(color=GREEN)))
ws.conditional_formatting.add("B8", FormulaRule(formula=['AND(ISNUMBER(kMedia),kMes>kMedia*(1+pTol))'], font=Font(color=RED, bold=True), fill=fill(LRED)))
ws.conditional_formatting.add("B8", FormulaRule(formula=['AND(ISNUMBER(kMedia),kMes>0,kMes<kMedia*(1-pTol))'], font=Font(color=GREEN, bold=True), fill=fill(LGREEN)))
ws.conditional_formatting.add("N8", FormulaRule(formula=['AND(ISNUMBER($N$8),$N$8>=pAlerta)'], font=Font(color=RED, bold=True), fill=fill(LRED)))
ws.conditional_formatting.add("N13", FormulaRule(formula=['AND(ISNUMBER(kCustoKm),ISNUMBER(kmFrotaGeral),kCustoKm>kmFrotaGeral*(1+pTol))'], font=Font(color=RED, bold=True), fill=fill(LRED)))
ws.conditional_formatting.add("N13", FormulaRule(formula=['AND(ISNUMBER(kCustoKm),ISNUMBER(kmFrotaGeral),kCustoKm<kmFrotaGeral*(1-pTol))'], font=Font(color=GREEN, bold=True), fill=fill(LGREEN)))
# legenda de cores
ws["B17"] = "Legenda:"; ws["B17"].font = font(bold=True, size=8, color=GRAY)
leg = [("C17", "Favorável / redução", LGREEN, GREEN), ("E17", "Aumento / alerta", LRED, RED),
       ("H17", "Atenção / pendência", LAMBER, AMBER), ("K17", "Informação neutra", LBLUE, BLUE)]
for ref, txt, bg, fg in leg:
    ws[ref] = txt; ws[ref].font = font(size=8, bold=True, color=fg); ws[ref].fill = fill(bg)
    ws[ref].alignment = Alignment(horizontal="center")

def style_chart(ch, w=17.5, h=7.2):
    ch.width = w; ch.height = h; ch.legend.position = "b"
    return ch

# gráfico 1: evolução mensal (linha)
c1 = LineChart(); c1.title = "Evolução mensal das despesas"; c1.y_axis.title = "R$"
c1.add_data(Reference(ws_r, min_col=2, max_col=3, min_row=m0 - 1, max_row=m1), titles_from_data=True)
c1.set_categories(Reference(ws_r, min_col=1, min_row=m0, max_row=m1))
c1.series[0].graphicalProperties.line.solidFill = BLUE; c1.series[0].graphicalProperties.line.width = 28000
c1.series[1].graphicalProperties.line.solidFill = "A6A6A6"; c1.series[1].graphicalProperties.line.dashStyle = "dash"
c1.y_axis.numFmt = '"R$" #,##0'; c1.y_axis.majorGridlines = None
c1.x_axis.delete = False; c1.y_axis.delete = False
for sr in c1.series: sr.smooth = False
ws.add_chart(style_chart(c1), "B19")
# gráfico 2: categorias (barra horizontal)
c2 = BarChart(); c2.type = "bar"; c2.title = "Despesas por categoria (período)"
c2.add_data(Reference(ws_r, min_col=3, min_row=k0 - 1, max_row=k1), titles_from_data=True)
c2.set_categories(Reference(ws_r, min_col=1, min_row=k0, max_row=k1))
c2.series[0].graphicalProperties.solidFill = BLUE; c2.legend = None
c2.x_axis.scaling.orientation = "maxMin"; c2.y_axis.numFmt = '"R$" #,##0'; c2.y_axis.majorGridlines = None
c2.x_axis.tickLblSkip = 1; c2.x_axis.delete = False; c2.y_axis.delete = False
c2.width = 17.5; c2.height = 7.2
ws.add_chart(c2, "I19")
# gráfico 3: veículos (top 10)
c3 = BarChart(); c3.type = "col"; c3.title = "Gastos por veículo (top 10)"
c3.add_data(Reference(ws_c, min_col=11, min_row=vb, max_row=vb + 10), titles_from_data=True)
c3.set_categories(Reference(ws_c, min_col=10, min_row=v0, max_row=vb + 10))
c3.series[0].graphicalProperties.solidFill = NAVY; c3.legend = None
c3.y_axis.numFmt = '"R$" #,##0'; c3.y_axis.majorGridlines = None
c3.dataLabels = DataLabelList(); c3.dataLabels.showVal = True; c3.dataLabels.showSerName = False; c3.dataLabels.showCatName = False; c3.dataLabels.showLegendKey = False; c3.dataLabels.position = "outEnd"; c3.dataLabels.numFmt = '"R$" #,##0;;'
c3.x_axis.delete = False; c3.y_axis.delete = False
c3.width = 17.5; c3.height = 7.2
ws.add_chart(c3, "B34")
# gráfico 4: combustível x manutenção
c4 = BarChart(); c4.type = "col"; c4.grouping = "clustered"; c4.title = "Combustível x manutenção por mês"
c4.add_data(Reference(ws_r, min_col=6, max_col=7, min_row=m0 - 1, max_row=m1), titles_from_data=True)
c4.set_categories(Reference(ws_r, min_col=1, min_row=m0, max_row=m1))
c4.series[0].graphicalProperties.solidFill = BLUE; c4.series[1].graphicalProperties.solidFill = "E67E22"
c4.y_axis.numFmt = '"R$" #,##0'; c4.y_axis.majorGridlines = None
c4.x_axis.delete = False; c4.y_axis.delete = False
ws.add_chart(style_chart(c4), "I34")
# gráfico 5: período atual x anterior por grupo
c5 = BarChart(); c5.type = "col"; c5.grouping = "clustered"; c5.title = "Período atual x período anterior, por grupo"
c5.add_data(Reference(ws_r, min_col=2, max_col=3, min_row=q0 - 1, max_row=q1), titles_from_data=True)
c5.set_categories(Reference(ws_r, min_col=1, min_row=q0, max_row=q1))
c5.series[0].graphicalProperties.solidFill = NAVY; c5.series[1].graphicalProperties.solidFill = "A6A6A6"
c5.y_axis.numFmt = '"R$" #,##0'; c5.y_axis.majorGridlines = None
c5.x_axis.delete = False; c5.y_axis.delete = False
ws.add_chart(style_chart(c5, w=35.5), "B49")
ws.sheet_view.showGridLines = False
ws.print_area = "A1:P64"
ws.page_setup.orientation = "landscape"; ws.page_setup.paperSize = ws.PAPERSIZE_A4
ws.page_setup.fitToWidth = 1; ws.page_setup.fitToHeight = 1; ws.sheet_properties.pageSetUpPr.fitToPage = True
ws.print_options.horizontalCentered = True
ws.page_margins.left = ws.page_margins.right = 0.4; ws.page_margins.top = ws.page_margins.bottom = 0.5
for s in (ws_d, ws_r):
    s.oddHeader.center.text = "Controle de Despesas da Frota"; s.oddHeader.center.font = "Arial,Bold"
    s.oddFooter.left.text = "Emitido em &D"; s.oddFooter.right.text = "Página &P de &N"
for s in (ws_v, ws_e):
    s.page_setup.orientation = "landscape"; s.page_setup.fitToWidth = 1; s.page_setup.fitToHeight = 0
    s.sheet_properties.pageSetUpPr.fitToPage = True; s.print_title_rows = "4:4"

# ---------------------------------------------------------------- Instruções
ws = ws_ins
title(ws, "Controle de Despesas da Frota", "Planilha de gestão de despesas veiculares · DataFlow Auto", 3)
ws.column_dimensions["A"].width = 3; ws.column_dimensions["B"].width = 28; ws.column_dimensions["C"].width = 100
lines = [
    ("h", "Como usar"),
    ("1. Veículos", "Cadastre cada veículo (código, placa, modelo, ano, responsável, KM atual, status). As colunas cinza são automáticas."),
    ("2. Despesas", "Lance cada gasto na primeira linha vazia logo abaixo da tabela. Placa, categoria, forma e status de pagamento são listas suspensas."),
    ("3. Dashboard", "Escolha Ano, Mês, Veículo e Categoria nas células amarelas. Use “Todos” para remover um filtro. Cartões, Resumo e gráficos seguem os filtros."),
    ("4. Resumo", "Total mensal (ano x ano anterior), gasto por categoria, comparação por grupo e matriz veículo x categoria."),
    ("5. Listas", "Categorias, grupos, formas de pagamento, status e parâmetros de alerta (células amarelas). Pode incluir itens novos no fim de cada lista."),
    ("Células para editar", "Somente as colunas azuis das abas Veículos e Despesas, os filtros amarelos do Dashboard e os parâmetros amarelos da aba Listas. Não edite colunas cinza nem a aba Cálculos."),
    ("h", "Regras de cálculo"),
    ("Período", "Ano + mês = aquele mês. Só ano = 01/01 a 31/12. Ano “Todos” = do primeiro ao último lançamento (sem período anterior para comparar)."),
    ("Período anterior", "Mês selecionado → mês anterior. Ano selecionado → mesmo intervalo do ano anterior (ex.: jan–out/2026 x jan–out/2025, se out/2026 for o último mês lançado). Variação % só aparece quando o período anterior tem gastos (evita erro de divisão por zero)."),
    ("Total do mês", "Mês selecionado no filtro ou, se o mês for “Todos”, o último mês com lançamento dentro do filtro."),
    ("Média mensal", "Total do período ÷ meses do período até o último lançamento. Meses sem gasto contam como meses reais, mas meses futuros não entram."),
    ("Custo por km", "Por veículo: gastos no filtro ÷ (maior KM − menor KM lançados no período). Só entra veículo com 2 ou mais leituras de KM; sem isso aparece “n/d”. O da frota soma só esses veículos."),
    ("Manutenção", "Grupo Manutenção = preventiva + corretiva + troca de óleo + pneus. Os grupos podem ser alterados na aba Listas."),
    ("Maior despesa / veículo mais caro", "Maior lançamento individual e veículo com maior soma dentro dos filtros atuais."),
    ("h", "Cores"),
    ("Verde", "Redução de gastos em relação ao período anterior ou à média, custo/km abaixo da média da frota."),
    ("Vermelho", "Aumento de gastos, valores acima do alerta (aba Listas), multas, pagamentos atrasados e custo/km acima da média."),
    ("Âmbar", "Pagamentos pendentes ou agendados e veículos em manutenção."),
    ("Azul", "Informação neutra. Despesa “Paga” fica em azul, não em verde: pagar uma conta não é economia."),
    ("h", "Compatibilidade e limitações"),
    ("Versão do Excel", "Excel 2019, 2021 ou Microsoft 365 (usa MÁXIMOSES e MÍNIMOSES). Funciona também no Excel para a Web. No Excel 2016 ou anterior esses cálculos mostram #NOME?."),
    ("Sem macros", "Tudo usa recursos nativos (tabelas estruturadas, fórmulas, validação, formatação condicional e gráficos). Não há VBA, por isso o arquivo é .xlsx."),
    ("Tabela dinâmica", "Os resumos já são feitos por fórmulas. Para uma tabela dinâmica com segmentações: clique na tabela de Despesas › Inserir › Tabela Dinâmica › tblDespesas; depois Inserir › Segmentação de Dados (Placa, Categoria, Data). Atualize com Dados › Atualizar Tudo."),
    ("Limites", f"Até {SLOTS} veículos nos cálculos e na matriz do Resumo; o gráfico de veículos mostra os 10 maiores. Gráficos mensais mostram o ano do filtro (ou o último ano com dados)."),
    ("Exportar PDF", "Dashboard e Resumo já têm área de impressão, orientação paisagem e ajuste à página: Arquivo › Exportar › PDF."),
]
r = 4
for k, v in lines:
    if k == "h":
        r += 1
        c = ws.cell(r, 2, v); c.font = font(bold=True, size=12, color=NAVY)
        for cc in (2, 3): ws.cell(r, cc).border = Border(bottom=Side(style="medium", color=BLUE))
    else:
        ws.cell(r, 2, k).font = font(bold=True)
        c = ws.cell(r, 3, v); c.font = font(); c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.cell(r, 2).alignment = Alignment(vertical="top")
        ws.row_dimensions[r].height = 28 if len(v) > 105 else 15
    r += 1
if WITH_DATA:
    r += 1
    c = ws.cell(r, 2, "Dados de exemplo"); c.font = font(bold=True, color=WHITE); c.fill = fill(AMBER)
    c = ws.cell(r, 3, "Este arquivo traz 3 veículos e lançamentos fictícios (jan/2025 a out/2026) só para demonstração. Para usar com dados reais, use o arquivo “modelo” em branco ou apague as linhas das tabelas (selecione as linhas › Excluir linhas da tabela).")
    c.font = font(); c.alignment = Alignment(wrap_text=True); ws.row_dimensions[r].height = 28
    for cc in ("Q",): pass
color_rows = {"Verde": (LGREEN, GREEN), "Vermelho": (LRED, RED), "Âmbar": (LAMBER, AMBER), "Azul": (LBLUE, BLUE)}
for row in ws.iter_rows(min_row=4, max_row=r, min_col=2, max_col=2):
    for c in row:
        if c.value in color_rows:
            bg, fg = color_rows[c.value]; c.fill = fill(bg); c.font = font(bold=True, color=fg)
ws.sheet_view.showGridLines = False

wb.active = 1
ws_d.sheet_view.tabSelected = True; ws_ins.sheet_view.tabSelected = False
for s, color in [(ws_ins, "808080"), (ws_d, NAVY), (ws_v, BLUE), (ws_e, BLUE), (ws_r, "1E8449"), (ws_c, "BFBFBF"), (ws_l, "BFBFBF")]:
    s.sheet_properties.tabColor = color
wb.calculation.fullCalcOnLoad = True
wb.save(OUT)
print("ok", OUT, len(despesas))
