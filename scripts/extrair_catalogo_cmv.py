"""Extrai o catálogo de produtos da planilha de CMV Real (dez/2023) para CSV.

Fonte: abas 'CMV SEMANA 01..04' (movimento) + 'CONSUMO' (catálogo canônico).
Saída: documentacao/catalogo-cmv-dezembro-2023.csv

Este é um utilitário de apoio, versionado para que aextração seja
reproduzível a partir do arquivo .xls original.
Uso:
    pip install xlrd
    python scripts/extrair_catalogo_cmv.py "caminho/Planilha.xls"
"""
import csv
import re
import sys

try:
    import xlrd
except ImportError:
    sys.exit("Instale a dependência: pip install xlrd")


def normalize(s):
    """Colapsa espaços (a planilha tem espaços duplos e não separáveis)."""
    return re.sub(r"\s+", " ", s.replace("\xa0", " ")).strip()


def num(v):
    return float(v) if isinstance(v, (int, float)) else 0.0


MEDIDA = re.compile(r"(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l)\b", re.I)

# Ordenadas por especificidade: a primeira regra que casar vence.
# As categorias "genéricas" (hortifruti, grãos) ficam no fim de propósito --
# se viessem antes, "Macarrão ESP 8" cairia em hortifruti porque "macarrão"
# contém "maca", e "Pimentao verde" cairia em temperos porque "pimentao"
# contém "pimenta". Os \b (limites de palavra) evitam o mesmo problema em
# casos como "Pão de forma" x "chocolate em barra".
CAT_RULES = [
    ("Peixes e Frutos do Mar", (
        r"bacalhau|camar[aã]o|\bpeixe\b|dourada|pangasius|salm[aã]o|atum|tubar[aã]o|"
        r"marisco|\blula\b|\bpolvo\b|ostra|\bcarpa\b|pacu"
    )),
    ("Ovos", r"\bovos?\b"),
    ("Laticínios", (
        r"\bleite\b|creme de leite|queijo|mussarela|iogurte|manteiga|margarina|"
        r"requeij[aã]o|\bnata\b|danone|cream cheese|ricota|parmes[aã]o"
    )),
    ("Padaria e Confeitaria", (
        r"p[aã]o|biscoito|bolo|rosca|torrada|panet|m[ãa]o de|chocolate|crepioca"
    )),
    ("Congelados", r"congelad|sorvete|\blasanha\b|nugget|empanad|palito"),
    ("Massas e Macarrão", (
        r"macarr|espaguete|\blasanha\b|ninh[oa]|parafuso|fura[cç][aã]o|penne|\bmassa\b"
    )),
    ("Descartáveis e Limpeza", (
        r"sach[eê]|sab[aã]o|deterg|\balcool\b|limpa|guardanapa|toalha|"
        r"pl[aá]stico|\bsaco\b|descart"
    )),
    ("Molhos e Temperos", (
        r"\bmolho\b|maionese|ketchup|\bextrato\b|\bcaldo\b|\bsal |sal$|"
        r"pimenta(?!o)|canela|louro|a[cç][aá]fr[aã]o|colorau|colorante|oregano|"
        r"ado[cç]ante|lemon|tempero|\bervas?\b|bicarbonato|achocolatado|gergelim|"
        r"p[aá]prica|mostarda|barbecue|fonte de|whey|prote[ií]na"
    )),
    ("Enlatados e Conservas", (
        r"enlatad|conserva|\batum\b|sardinha|leite de coco|geleia|azeitona|palmito"
    )),
    ("Polpas e Doces", r"\bpolpa\b"),
    ("Bebidas", (
        r"caf[eé]|refrigerante|guaran[aá]|\bsuco\b|cerveja|vinho|itamb|"
        r"\b[aá]gua\b|gasosa|t[oó]nica|red bull|\benergy\b|\bgelo\b"
    )),
    ("Carnes e Proteínas", (
        r"carne|carpac|capa de|contra ?fil|fil[eé]|picanha|maminha|cox[aã]o|coxinha|"
        r"sobrecoxa|frango|caipira|\basa\b|fraldinha|lagarto|\blombo\b|cupim|"
        r"\bpeito\b|ma[cç]a do peito|toucinho|presunto|apresuntado|lingui|bacon|"
        r"salame|sassami|pernil|chulet|costela|costelinha|moela|rabada|figado|"
        r"ac[eé]m|cora[cç][aã]o|dobradinha|hondashi|alcatra|\bfl[aá]o\b|pancetta|"
        r"ossobuco|banquet|\bkibe\b|feijoada"
    )),
    ("Hortifruti", (
        r"abacaxi|ab[oó]bora|beringela|beterraba|chuchu|banana|\bmanga\b|"
        r"\bma[cç][aã]s?\b|morango|\buva\b|laranja|lim[aã]o|mam[aã]o|abacate|"
        r"guariroba|\bjilo\b|quiabo|vagem|piment[aã]o|pepino|tomate|batata|"
        r"\bcebola\b|alho|cenoura|alface|repolho|brocolis|couve|abobrinha|"
        r"nabo|rabanete|verdura|salada|alecrim|salsinha|manjeric[aã]o|coentro|"
        r"hortel[aã]o|cebolinha|cogumelo|pequi|p[eé]ssego|kiwi|melancia"
    )),
    ("Não-perecíveis", (
        r"azeite|[oó]leo|\bvinagre\b|\bsopa\b|polvilho|\bgelatina\b"
    )),
    ("Grãos e Farinhas", (
        r"arroz|a[cç][uú]car|feij[aã]o|farinha|fub[aá]|f[uü]ba|amido|"
        r"gr[aã]o de bico|\bmilho\b|aveia|cevada|[kq]uibe|\btrigo\b|tapioca|"
        r"mandioca|g[oó]ji|castanha|amendoim|coco ralado"
    )),
]


def categorizar(nome):
    n = " " + nome.lower() + " "
    for cat, padrao in CAT_RULES:
        if re.search(padrao, n, re.IGNORECASE):
            return cat
    return "Outros"

def unidade_estoque(nome, unid_plan):
    """Embalagem com medida no nome (ex.: 'Arroz 5 Kg') conta como UN,
    pois a compra é por pacote/saco — não por quilo solto.

    A conversão de peso para KG/UN fica a cargo do operador na tela de
    entrada; aqui só registramos a unidade "natural" do produto.
    """
    up = (unid_plan or "").lower().strip()
    med = MEDIDA.search(nome)
    if up in ("cartela", "pacote", "pct", "und", "un", "um", "cx", "caixa"):
        return "UN"
    if up in ("kg",):
        return "UN" if med else "KG"
    if up in ("l", "ml"):
        return "UN" if med else "L"
    return "UN"


def principal(path):
    b = xlrd.open_workbook(path)

    # --- catálogo canônico (aba CONSUMO): A=produto, F=estoque mínimo ---
    canonico = {}
    cons = b.sheet_by_name("CONSUMO")
    for r in range(1, cons.nrows):
        nome = cons.cell_value(r, 0)
        if isinstance(nome, str) and nome.strip():
            canonico[normalize(nome)] = cons.cell_value(r, 5) or 0

    # --- movimento das 4 semanas ---
    # B=produto C=unidade D/E/F=est.inicial(qtd,preco,total)
    # G/H/I=compras(qtd,preco,total) J/K/L=est.final M=consumo
    mov = {}
    for name in b.sheet_names():
        if not name.startswith("CMV SEMANA"):
            continue
        s = b.sheet_by_name(name)
        for r in range(6, s.nrows):
            raw = s.cell_value(r, 1)
            if not (isinstance(raw, str) and raw.strip()):
                continue
            nome = normalize(raw)
            d = mov.setdefault(nome, {
                "est_ini": 0.0, "preco_ini": 0.0, "unid_plan": "",
                "compras": 0.0, "val_compras": 0.0, "consumo": 0.0, "semanas": 0,
            })
            d["unid_plan"] = str(s.cell_value(r, 2) or "").strip()
            d["semanas"] += 1
            d["est_ini"] += num(s.cell_value(r, 3))
            d["preco_ini"] += num(s.cell_value(r, 4))
            d["compras"] += num(s.cell_value(r, 6))
            d["val_compras"] += num(s.cell_value(r, 8))
            d["consumo"] += num(s.cell_value(r, 12))

    # --- monta as linhas, casando canônico x movimento ---
    # Chave de casamento: nome normalizado, sem espaços. A planilha tem
    # grafias divergentes entre abas ("Açucar" x "Açúcar", espaços duplos).
    mov_por_chave = {}
    for nome, d in mov.items():
        mov_por_chave.setdefault(re.sub(r"[^a-z0-9]", "", nome.lower()), d)

    rows = []
    sem_movimento = []
    for nome in canonico:
        chave = re.sub(r"[^a-z0-9]", "", nome.lower())
        d = mov_por_chave.get(chave)
        if d is None:
            sem_movimento.append(nome)
            d = {"est_ini": 0.0, "preco_ini": 0.0, "unid_plan": "",
                 "compras": 0.0, "val_compras": 0.0, "consumo": 0.0, "semanas": 0}

        med = MEDIDA.search(nome)
        preco_medio = (round(d["val_compras"] / d["compras"], 6)
                       if d["compras"] else round(d["preco_ini"], 6))
        rows.append({
            "nome": nome,
            "categoria": categorizar(nome),
            "unidade_estoque": unidade_estoque(nome, d["unid_plan"]),
            "medida_embutida": med.group(0) if med else "",
            "peso_por_embalagem": med.group(1).replace(",", ".") if med else "",
            "unidade_medida": med.group(2).lower() if med else "",
            "unidade_planilha": d["unid_plan"],
            "qtd_por_embalagem": med.group(1).replace(",", ".") if med else "1",
            "estoque_inicial": round(d["est_ini"], 3),
            "preco_unitario_inicial": round(d["preco_ini"], 6),
            "compras_4_semanas": round(d["compras"], 3),
            "valor_compras_4_semanas": round(d["val_compras"], 2),
            "preco_medio_compra": preco_medio,
            "consumo_4_semanas": round(d["consumo"], 3),
            "semanas_presente": d["semanas"],
            "estoque_minimo_planilha": canonico[nome],
        })

    rows.sort(key=lambda r: (r["categoria"], r["nome"]))
    return rows, sem_movimento


if __name__ == "__main__":
    origem = sys.argv[1] if len(sys.argv) > 1 else "origem.xls"
    destino = (sys.argv[2] if len(sys.argv) > 2
               else "documentacao/catalogo-cmv-dezembro-2023.csv")
    rows, sem_mov = principal(origem)

    with open(destino, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    from collections import Counter
    print(f"produtos: {len(rows)}")
    print(f"com movimento: {sum(1 for r in rows if r['semanas_presente'] > 0)}")
    print(f"com estoque inicial: {sum(1 for r in rows if r['estoque_inicial'] > 0)}")
    print(f"sem movimento (só catálogo): {len(sem_mov)}")
    for n in sem_mov:
        print(f"    sem movimento: {n}")
    print("\npor categoria:")
    for cat, n in sorted(Counter(r["categoria"] for r in rows).items(),
                         key=lambda x: (-x[1], x[0])):
        print(f"  {cat:30} {n}")
    print("\npor unidade:")
    for u, n in sorted(Counter(r["unidade_estoque"] for r in rows).items()):
        print(f"  {u}: {n}")
    print(f"\n-> {destino}")
