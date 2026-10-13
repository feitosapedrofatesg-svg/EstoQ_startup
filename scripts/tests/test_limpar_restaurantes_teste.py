"""Proteções do utilitário antes de qualquer operação destrutiva."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('limpeza', Path(__file__).parents[1] / 'limpar_restaurantes_teste.py')
limpeza = importlib.util.module_from_spec(spec)
spec.loader.exec_module(limpeza)


class ProtecoesLimpezaTest(unittest.TestCase):
    def setUp(self):
        self.pasta = tempfile.TemporaryDirectory()
        self.addCleanup(self.pasta.cleanup)
        self.raiz = Path(self.pasta.name)
        self.manifesto = self.raiz / 'manifesto.json'
        self.backup = self.raiz / 'antes.dump'
        self.copia = self.raiz / 'copia.dump'
        self.prova = self.raiz / 'ensaio.json'
        self.backup.write_bytes(b'backup de teste')
        self.copia.write_bytes(self.backup.read_bytes())
        self.dados = dict(banco='origem', usuario='postgres', schema='public',
            servico='producao', host='', porta='', restaurantes=[dict(id=1,nome='Teste',criacao='2026-10-12')])
        self.manifesto.write_text(json.dumps(self.dados))
        self.prova.write_text(json.dumps(dict(backup=limpeza.hash_arquivo(self.backup),
            manifesto=limpeza.hash_arquivo(self.manifesto))))
        self.argumentos = ['limpeza', 'executar', '--manifesto', str(self.manifesto),
            '--backup', str(self.backup), '--copia-backup', str(self.copia),
            '--comprovante', str(self.prova)]
        self.ambiente = patch.dict(os.environ, dict(PGSERVICE='producao',PGHOST='',PGPORT=''))
        self.ambiente.start()
        self.addCleanup(self.ambiente.stop)

    def test_backup_diferente_nao_chega_ao_banco(self):
        self.copia.write_bytes(b'outra copia')
        with patch('sys.argv', self.argumentos), patch.object(limpeza,'consultar') as consulta:
            with self.assertRaisesRegex(ValueError,'diferentes'): limpeza.main()
            consulta.assert_not_called()

    def test_manifesto_alterado_invalida_ensaio(self):
        self.manifesto.write_text(json.dumps(dict(self.dados,restaurantes=[])))
        with patch('sys.argv',self.argumentos), patch.object(limpeza,'consultar') as consulta:
            with self.assertRaisesRegex(ValueError,'divergem'): limpeza.main()
            consulta.assert_not_called()

    def test_conexao_errada_nao_executa_limpeza(self):
        with patch('sys.argv',self.argumentos), patch.object(limpeza,'identidade',return_value=dict(banco='outro',usuario='postgres')), patch.object(limpeza,'limpeza') as executar:
            with self.assertRaisesRegex(ValueError,'conexão'): limpeza.main()
            executar.assert_not_called()

    def test_ensaio_recusa_banco_com_mesmo_nome(self):
        argumentos=['limpeza','ensaiar','--manifesto',str(self.manifesto),'--backup',str(self.backup),
            '--comprovante',str(self.prova),'--servico-restauracao','alias_da_origem']
        with patch('sys.argv',argumentos), patch.object(limpeza,'identidade',return_value=dict(banco='origem',usuario='outro')), patch.object(limpeza.subprocess,'run') as restaurar:
            with self.assertRaisesRegex(ValueError,'outro nome'): limpeza.main()
            restaurar.assert_not_called()

    def test_url_https_da_data_api_e_recusada(self):
        arquivo = self.raiz / 'conexao.txt'
        arquivo.write_text('https://exemplo.neon.tech/neondb/rest/v1')
        with self.assertRaisesRegex(ValueError, 'PostgreSQL'):
            limpeza.carregar_conexao(arquivo)

    def test_senha_da_url_fica_no_ambiente_sem_comando(self):
        arquivo = self.raiz / 'conexao.txt'
        arquivo.write_text('postgresql://responsavel:teste%40seguro@db.example/neondb?sslmode=require&channel_binding=require')
        with patch.object(limpeza.subprocess, 'run') as cliente:
            limpeza.carregar_conexao(arquivo)
            self.assertEqual('teste@seguro', os.environ['PGPASSWORD'])
            self.assertEqual('db.example', os.environ['PGHOST'])
            self.assertNotIn('PGSERVICE', os.environ)
            cliente.assert_not_called()

    def test_manifesto_existente_nao_e_sobrescrito(self):
        with self.assertRaises(FileExistsError): limpeza.salvar(self.manifesto,{})
        self.assertEqual(self.dados,json.loads(self.manifesto.read_text()))


if __name__ == '__main__':
    unittest.main()
