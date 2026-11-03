import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from resumir_auditoria_embalagens import analisar

class AuditoriaEmbalagensTest(unittest.TestCase):
    def dados(self, **campos):
        linha={'unidade_nota':'UN','unidade_estoque':'KG','descricao':'ARROZ 5KG','fator':1,
            'quantidade_entrada':2,'quantidade_movimentada':2,**campos}
        return {'itensNota':[linha],'entradasManuaisInconclusivas':[],'cadastrosEmUNComPeso':[]}
    def test_indicio_de_pacote_nao_e_correcao_automatica_de_saldo(self):
        item=analisar(self.dados())['suspeitos'][0]
        self.assertEqual('5',item['fator_indicado_pela_descricao_ou_unidade'])
        self.assertIn('não comprova',item['status'])
        self.assertNotIn('saldo_corrigido',item)
    def test_peso_direto_caixa_incerta_e_bebidas_nao_sao_multiplicados_pelo_nome(self):
        for campos in [{'unidade_nota':'KG'}, {'unidade_nota':'CX'},
                {'unidade_estoque':'UN','descricao':'H2O 500ML'}, {'descricao':'ARROZ 12X5KG'}]:
            self.assertEqual([],analisar(self.dados(**campos))['suspeitos'])
