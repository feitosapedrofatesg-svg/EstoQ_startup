"""Resume JSON da auditoria JDBC. Não conecta nem altera bancos; todos os indícios exigem revisão."""
import argparse
import json
import re
from decimal import Decimal

MEDIDA = re.compile(r'(\d+(?:[.,]\d+)?)\s*(KG|ML|G|L)\b', re.I)
UNIDADES = {'KG': ('peso', Decimal(1000)), 'G': ('peso', Decimal(1)), 'L': ('volume', Decimal(1000)), 'ML': ('volume', Decimal(1))}
AVULSAS = {'UN','UN1','UND','UNID','UNIDADE','UNIDADES','PC','PC1','PCA'}

def analisar(dados):
    suspeitos, inconclusivos = [], []
    for item in dados['itensNota']:
        origem, destino = item['unidade_nota'].upper().strip(), item['unidade_estoque']
        medidas = MEDIDA.findall(item['descricao'])
        esperado = None
        if origem == 'KG1': origem = 'KG'
        if origem in UNIDADES and destino in UNIDADES and UNIDADES[origem][0] == UNIDADES[destino][0]:
            esperado = UNIDADES[origem][1] / UNIDADES[destino][1]
        elif origem in AVULSAS and len(medidas) == 1 and not re.search(r'\d\s*[xX×]\s*\d', item['descricao']):
            numero, unidade = medidas[0]; unidade = unidade.upper()
            if destino in UNIDADES and UNIDADES[unidade][0] == UNIDADES[destino][0]:
                esperado = Decimal(numero.replace(',','.')) * UNIDADES[unidade][1] / UNIDADES[destino][1]
        divergencia = item['quantidade_entrada'] != item['quantidade_movimentada']
        if divergencia or (esperado is not None and item['fator'] is not None and Decimal(str(item['fator'])) != esperado):
            suspeitos.append({**item,'fator_indicado_pela_descricao_ou_unidade':str(esperado) if esperado is not None else None,
                'status':'Indício para conferência; não comprova saldo incorreto.'})
        else:
            inconclusivos.append(item)
    return {'suspeitos':suspeitos,'demais_itens_para_conferencia':inconclusivos,
            'manuais_inconclusivos':dados['entradasManuaisInconclusivas'], 'cadastros_UN_com_peso':dados['cadastrosEmUNComPeso']}

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('arquivo');args=parser.parse_args()
    with open(args.arquivo,encoding='utf-8') as f: dados=json.load(f,parse_float=Decimal)
    print(json.dumps(analisar(dados),ensure_ascii=False,indent=2,default=str))
