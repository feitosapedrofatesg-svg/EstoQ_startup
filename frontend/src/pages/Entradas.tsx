import { traduzir } from "../lib/i18n";
import { useState, useEffect } from "react";
import { PageHeader, Card, Button, DataTable, AlertBanner, EmptyState } from "../components/UI";
import { RegisterEntrada } from "./Movimentacoes";
import { Etiquetas } from "../components/Etiquetas";
import { Paginacao } from "../components/Paginacao";
import { useFetch } from "../lib/hooks";
import type { Page,LoteDTO } from "../lib/types";
import { fmtDate } from "../lib/format";
export function Entradas() {
 const [aberto,setAberto]=useState(false);const [etiqueta,setEtiqueta]=useState<number|null>(null);const [pagina,setPagina]=useState(0);
 const [tamanho,setTamanho]=useState(25);
 const {data,error,loading,refresh}=useFetch<Page<LoteDTO>>(`/api/lotes/pagina?somenteComSaldo=false&page=${pagina}&size=${tamanho}`);
 useEffect(()=>{if(data&&pagina>0&&pagina>=data.totalPages)setPagina(Math.max(0,data.totalPages-1));},[data,pagina]);
 return <><PageHeader title={traduzir("Entradas")} subtitle={traduzir("Registre a entrada de itens no estoque.")} />
 <Card title={traduzir("Entrada de itens")}><Button icon="plus" onClick={()=>setAberto(true)}>{" "}{traduzir("Registrar entrada")}{" "}</Button></Card>
 <Card title={traduzir("Reimprimir etiquetas")}>{error&&<AlertBanner tone="bad">{error}</AlertBanner>}{loading&&!data&&<p>{traduzir("Carregando lotes…")}</p>}{data?.totalElements===0&&<EmptyState title={traduzir("Nenhum lote encontrado")} text={traduzir("Registre a entrada de itens no estoque.")} icon="box"/>}<DataTable caption={traduzir("Lotes salvos")} headers={[traduzir("Produto"),traduzir("Lote"),traduzir("Validade"),""]}>{data?.content.map(l=><tr key={l.id}><td>{l.produtoNome}</td><td>{l.codigo}</td><td>{fmtDate(l.dataValidade)}</td><td><Button size="sm" icon="printer" onClick={()=>setEtiqueta(l.id)}>{" "}{traduzir("Imprimir etiqueta")}{" "}</Button></td></tr>)}</DataTable><Paginacao dados={data} pagina={pagina} alterar={setPagina} itensPorPagina={tamanho} alterarTamanho={setTamanho}/></Card>
 {aberto && <RegisterEntrada onClose={()=>setAberto(false)} onDone={r=>{setAberto(false);setEtiqueta(r.movimentacoes[0]?.loteId??null);void refresh();}} />}
 {etiqueta && <Etiquetas loteId={etiqueta} onClose={()=>setEtiqueta(null)} />}</>;
}
