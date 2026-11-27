import { traduzir } from "../lib/i18n";
import { useEffect, useRef, useState } from "react";
import { Modal, Button, NumberField, AlertBanner } from "./UI";
import { useFetch } from "../lib/hooks";
import { fmtDate } from "../lib/format";
import type { LoteDTO } from "../lib/types";
import { t } from "../lib/i18n";

export interface MedidasEtiqueta { largura:number; altura:number; margem:number; quantidade:number }
export function montarEtiquetas(doc:Document,lote:LoteDTO,m:MedidasEtiqueta) {
  if(!lote.codigo || !lote.produtoNome)throw new Error(t("Produto ou lote não informado. Atualize o registro antes de imprimir."));
  if(!Number.isInteger(m.quantidade)||m.quantidade<1||m.quantidade>500||m.largura<30||m.largura>200||m.altura<20||m.altura>200||m.margem<0||m.largura-2*m.margem<20||m.altura-2*m.margem<15)
    throw new Error(t("Confira quantidade e dimensões da etiqueta."));
  doc.open();doc.write('<!doctype html><html><head></head><body></body></html>');doc.close();
  doc.title=t("Etiquetas");doc.documentElement.lang=document.documentElement.lang;
  const css=doc.createElement("style");css.textContent=`@page{size:${m.largura}mm ${m.altura}mm;margin:0}html,body{margin:0;padding:0;color:#000;background:#fff;font-family:Arial,sans-serif;font-size:10pt}.etiqueta{box-sizing:border-box;width:${m.largura}mm;height:${m.altura}mm;padding:${m.margem}mm;break-after:page;page-break-after:always;overflow:hidden}.etiqueta:last-child{break-after:auto;page-break-after:auto}.conteudo{width:100%;height:100%;overflow-wrap:anywhere;line-height:1.2}strong{display:block;font-size:1.15em;margin-bottom:1mm}p{margin:0 0 1mm}@media screen{.etiqueta{outline:1px dashed #888;margin-bottom:2mm}}`;
  doc.head.append(css);
  for(let n=0;n<m.quantidade;n++) {
    const etiqueta=doc.createElement("section");etiqueta.className="etiqueta";
    const conteudo=doc.createElement("div");conteudo.className="conteudo";etiqueta.append(conteudo);
    for(const [tag,valor] of [["strong",lote.produtoNome],["p",`${t("Lote")}: ${lote.codigo}${lote.loteFabricante?` · ${lote.loteFabricante}`:""}`],["p",`${t("Validade")}: ${lote.dataValidade?fmtDate(lote.dataValidade):t("Não informada")}`]]) {
      const el=doc.createElement(tag);el.textContent=valor;conteudo.append(el);
    }
    doc.body.append(etiqueta);
  }
}
export function ajustarEtiquetas(doc:Document) {
  for(const conteudo of doc.querySelectorAll<HTMLElement>(".conteudo")) {
    let fonte=10;
    while((conteudo.scrollHeight>conteudo.clientHeight+1||conteudo.scrollWidth>conteudo.clientWidth+1)&&fonte>5)conteudo.style.fontSize=`${--fonte}pt`;
    if(conteudo.scrollHeight>conteudo.clientHeight+1||conteudo.scrollWidth>conteudo.clientWidth+1)
      throw new Error(t("O conteúdo não cabe. Aumente as dimensões da etiqueta."));
  }
}
export function Etiquetas({loteId,onClose}:{loteId:number;onClose:()=>void}) {
  const {data:lote,error}=useFetch<LoteDTO>(`/api/lotes/${loteId}`);
  const [medidas,setMedidas]=useState<MedidasEtiqueta>({largura:60,altura:40,margem:2,quantidade:1});
  const [erro,setErro]=useState("");const iframe=useRef<HTMLIFrameElement>(null);
  useEffect(()=>{if(!lote||!iframe.current?.contentDocument)return;try{montarEtiquetas(iframe.current.contentDocument,lote,medidas);ajustarEtiquetas(iframe.current.contentDocument);setErro("");}catch(e){setErro(e instanceof Error?e.message:"");}},[lote,medidas]);
  const imprimir=()=>{
    if(!lote)return;
    const janela=window.open("","_blank","width=650,height=600");
    if(!janela){setErro(t("Permita a abertura de janelas para imprimir."));return;}
    try{montarEtiquetas(janela.document,lote,medidas);janela.requestAnimationFrame(()=>{
      try{ajustarEtiquetas(janela.document);janela.focus();janela.print();}catch(e){setErro(e instanceof Error?e.message:"");janela.close();}
    });}catch(e){setErro(e instanceof Error?e.message:"");janela.close();}
  };
  return <Modal open title={traduzir("Imprimir etiqueta")} onClose={onClose} width="lg" footer={<><Button variant="ghost" onClick={onClose}>{" "}{traduzir("Fechar")}{" "}</Button><Button icon="printer" onClick={imprimir} disabled={!lote||!!erro}>{" "}{traduzir("Imprimir etiqueta")}{" "}</Button></>}>
    {(error||erro)&&<AlertBanner tone="bad">{error||erro}</AlertBanner>}
    {lote&&!lote.dataValidade&&<AlertBanner tone="warn">{" "}{traduzir("Validade não informada no registro salvo. A etiqueta indicará essa ausência.")}{" "}</AlertBanner>}
    <div className="form-grid-2">{([ ["largura","Largura (mm)"],["altura","Altura (mm)"],["margem","Margens (mm)"],["quantidade","Quantidade de etiquetas"] ] as const).map(([chave,label])=>
      <NumberField key={chave} label={label} value={medidas[chave]} min={chave==="margem"?0:chave==="quantidade"?1:chave==="largura"?30:20} max={chave==="quantidade"?500:200} onValue={v=>setMedidas(m=>({...m,[chave]:v??0}))} />)}</div>
    <p className="muted">{" "}{traduzir("Configure no driver o mesmo tamanho de papel, margens e escala de 100%. Desative cabeçalhos e rodapés do navegador. O ajuste final depende da impressora e do papel.")}{" "}</p>
    <iframe title={t("Prévia das etiquetas")} ref={iframe} style={{width:"100%",height:"280px",border:"1px solid var(--line)",background:"white"}} />
  </Modal>;
}
