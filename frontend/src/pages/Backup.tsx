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
      toast.success("Backup gerado e baixado");
      void refresh();
    } catch (e) {
      toast.error("Não foi possível gerar o backup", (e as Error).message);
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
      toast.error("Não foi possível baixar", (e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Backup"
        subtitle="Cópias de segurança do banco, geradas com o comando pg_dump."
        actions={
          <Button icon="download" loading={busy === "novo"} onClick={() => void gerar()}>
            Gerar backup agora
          </Button>
        }
      />

      <AlertBanner tone="info">
        <p>
          O backup é <strong>baixado automaticamente para sua máquina</strong> ao clicar em "Gerar".
          No Render free os arquivos no servidor são temporários (somem a cada reinício) — sempre
          baixe o dump na hora. Para restaurar, use o procedimento com{" "}
          <code>pg_restore</code> descrito no README do projeto.
        </p>
      </AlertBanner>

      <Card title="Backups salvos">
        {error && <div className="alertbanner alertbanner--bad">{error}</div>}
        {loading && !data && <p className="muted">Carregando…</p>}
        {data && data.length === 0 ? (
          <EmptyState
            title="Nenhum backup ainda"
            text="Gere o primeiro backup para proteger os dados da cozinha."
            icon="archive"
            action={
              <Button variant="outline" icon="download" onClick={() => void gerar()}>
                Gerar backup
              </Button>
            }
          />
        ) : (
          <DataTable caption="Backups do banco de dados" headers={["Arquivo", "Tamanho", "Gerado em", ""]}>
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
                    Baixar
                  </Button>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>
    </>
  );
}