import { traduzir } from "../lib/i18n";
import { mensagemErro } from "../lib/api";
import { useState } from "react";
import { useFetch } from "../lib/hooks";
import { api, downloadBlob } from "../lib/api";
import {
  PageHeader,
  Card,
  Button,
  DataTable,
  EmptyState,
  AlertBanner,
} from "../components/UI";
import { fmtDateTime, fmtBytes } from "../lib/format";
import type { BackupDTO } from "../lib/types";
import { useToast } from "../store/toast";

export function Backup() {
  const toast = useToast();
  const { data, loading, error, refresh } = useFetch<BackupDTO[]>("/api/backups");
  const [busy, setBusy] = useState<string | null>(null);

  const gerar = async () => {
    setBusy("novo");
    try {
      const { blob, filename } = await api.blobPost("/api/backups");
      downloadBlob(blob, filename);
      toast.success(traduzir("Backup gerado e baixado"));
      void refresh();
    } catch (e) {
      toast.error(traduzir("Não foi possível gerar o backup"), mensagemErro(e));
    } finally {
      setBusy(null);
    }
  };

  const baixar = async (nome: string) => {
    setBusy(nome);
    try {
      const blob = await api.blob(`/api/backups/${encodeURIComponent(nome)}`);
      downloadBlob(blob, nome);
    } catch (e) {
      toast.error(traduzir("Não foi possível baixar"), mensagemErro(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHeader
        title={traduzir("Backup completo da plataforma")}
        subtitle={traduzir("Cópia completa dos dados de todos os restaurantes, exclusiva da administração da plataforma.")}
        actions={
          <Button icon="download" loading={busy === "novo"} onClick={() => void gerar()}>
            {" "}{traduzir("Gerar backup agora")}{" "}</Button>
        }
      />

      <AlertBanner tone="info">
        <p>
          {" "}{traduzir("O backup é")}{" "}<strong>{" "}{traduzir("baixado automaticamente para sua máquina")}{" "}</strong> {" "}{traduzir("ao clicar em \"Gerar\". No Render free os arquivos no servidor são temporários (somem a cada reinício) — sempre baixe o dump na hora. Para restaurar, use o procedimento com")}{" "}
          <code>{" "}{traduzir("pg_restore")}{" "}</code> {" "}{traduzir("descrito no README do projeto.")}{" "}</p>
      </AlertBanner>

      <Card title={traduzir("Backups salvos")}>
        {error && <div className="alertbanner alertbanner--bad">{traduzir(error)}</div>}
        {loading && !data ? (
          <p className="muted">{" "}{traduzir("Carregando…")}{" "}</p>
        ) : data && data.length === 0 ? (
          <EmptyState
            title={traduzir("Nenhum backup ainda")}
            text={traduzir("Gere uma cópia completa para proteger os dados da plataforma.")}
            icon="archive"
            action={
              <Button variant="outline" icon="download" onClick={() => void gerar()}>
                {" "}{traduzir("Gerar backup")}{" "}</Button>
            }
          />
        ) : (
          <DataTable caption={traduzir("Backups do banco de dados")} headers={[traduzir("Arquivo"), traduzir("Tamanho"), traduzir("Gerado em"), ""]}>
            {data?.map((b) => (
              <tr key={b.nome}>
                <td>
                  <strong>{b.nome}</strong>
                </td>
                <td className="muted-cell">{fmtBytes(b.tamanhoBytes)}</td>
                <td className="muted-cell">{fmtDateTime(b.criadoEm)}</td>
                <td>
                  <Button
                    size="sm"
                    variant="outline"
                    icon="download"
                    loading={busy === b.nome}
                    onClick={() => void baixar(b.nome)}
                  >
                    {" "}{traduzir("Baixar")}{" "}</Button>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>
    </>
  );
}