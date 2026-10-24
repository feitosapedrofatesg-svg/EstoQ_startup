import importlib.util
from pathlib import Path
import unittest

arquivo = Path(__file__).resolve().parents[1] / 'medir_homologacao.py'
spec = importlib.util.spec_from_file_location('medir_homologacao', arquivo)
medir = importlib.util.module_from_spec(spec)
spec.loader.exec_module(medir)


class MedirHomologacaoTest(unittest.TestCase):
    def test_rejeita_producao_e_url_com_credenciais(self):
        for url in ['https://estoq-oficial.vercel.app', 'https://estoq-backend-va.onrender.com',
                    'http://homologacao.exemplo.com', 'https://usuario:senha@homologacao.exemplo.com',
                    'https://homologacao.exemplo.com/api', 'https://homologacao.exemplo.com?segredo=123']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                medir.validar_destino(url)

    def test_exige_marcador_de_homologacao_e_commit_exato(self):
        medir.validar_ambiente({'ambiente': 'homologacao', 'versao': 'abc'}, 'abc')
        for dados in [{'versao': 'abc'}, {'ambiente': 'producao', 'versao': 'abc'},
                      {'ambiente': 'homologacao', 'versao': 'antiga'}]:
            with self.assertRaises(ValueError):
                medir.validar_ambiente(dados, 'abc')

    def test_percentil_e_erros_nao_escondem_resposta_lenta(self):
        amostras = [{'status': 200, 'ms': n} for n in range(1, 20)] + [{'status': 503, 'ms': 900}]
        resumo = medir.resumir(amostras)
        self.assertEqual(1, resumo['erros'])
        self.assertEqual(19, resumo['p95_ms'])
        self.assertEqual(900, resumo['max_ms'])
        self.assertEqual(10.5, resumo['mediana_ms'])

    def test_html_com_status_200_conta_como_erro(self):
        resumo = medir.resumir([{'status': 200, 'ms': 1, 'json_valido': False}])
        self.assertEqual(1, resumo['erros'])
