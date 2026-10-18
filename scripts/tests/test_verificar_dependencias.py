import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

arquivo = Path(__file__).parents[1] / "verificar_dependencias.py"
spec = importlib.util.spec_from_file_location("verificar_dependencias", arquivo)
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class DependenciasTest(unittest.TestCase):
    def test_arquivo_vazio_nao_pode_ser_considerado_seguro(self):
        with self.assertRaises(ValueError):
            scanner.ler_dependencias("The following files have been resolved:\n")

    def test_le_versoes_resolvidas_inclusive_com_classifier(self):
        pacotes = scanner.ler_dependencias("  org.exemplo:driver:jar:42.0:runtime\n"
            "  org.exemplo:nativo:jar:linux:1.2:runtime\n  org.exemplo:teste:jar:1.0:test")
        self.assertEqual([("org.exemplo:driver", "42.0"), ("org.exemplo:nativo", "1.2")], pacotes)

    def test_paginacao_preserva_avisos_da_segunda_pagina(self):
        with patch.object(scanner, "consultar", side_effect=[
            [{"vulns": [{"id": "AVISO-1"}], "next_page_token": "pagina2"}],
            [{"vulns": [{"id": "AVISO-2"}]}],
        ]):
            self.assertEqual(2, len(scanner.verificar([("org.exemplo:driver", "1.0")])) )
