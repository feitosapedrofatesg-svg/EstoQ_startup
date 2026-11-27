import { traduzir } from "../lib/i18n";
import { useEffect } from "react";
import { Checkbox, AlertBanner } from "./UI";
import { useFetch } from "../lib/hooks";
export interface Confirmacoes {aceitouTermos:boolean;cientePrivacidade:boolean;versaoTermos:string;versaoPrivacidade:string}
export const SEM_CONFIRMACAO:Confirmacoes={aceitouTermos:false,cientePrivacidade:false,versaoTermos:"",versaoPrivacidade:""};
export function ConfirmacoesCadastro({valor,onChange}:{valor:Confirmacoes;onChange:(c:Confirmacoes)=>void}) {
 const {data,error}=useFetch<{termos:string;privacidade:string;termosEmRevisao:boolean}>("/api/documentos");
 useEffect(()=>{if(data?.termos){const mudou=!!valor.versaoTermos&&(valor.versaoTermos!==data.termos||valor.versaoPrivacidade!==data.privacidade);onChange({...valor,...(mudou?{aceitouTermos:false,cientePrivacidade:false}:{}),versaoTermos:data.termos,versaoPrivacidade:data.privacidade});}},[data]);
 return <div className="confirmacoes-cadastro">
  <p><a href="/termos" target="_blank" rel="noreferrer">{" "}{traduzir("Termos de Uso completos")}{" "}</a> · <a href="/privacidade" target="_blank" rel="noreferrer">{" "}{traduzir("Política de Privacidade completa")}{" "}</a></p>
  {data?.termosEmRevisao&&<AlertBanner tone="warn">{" "}{traduzir("Termos de Uso em revisão. Esta minuta destina-se à validação em homologação.")}{" "}</AlertBanner>}
  {error&&<AlertBanner tone="bad">{" "}{traduzir("Não foi possível consultar as versões dos documentos. Recarregue antes de cadastrar.")}{" "}</AlertBanner>}
  <Checkbox checked={valor.aceitouTermos} onChange={v=>onChange({...valor,aceitouTermos:v})} label={traduzir("Li e aceito os Termos de Uso.")} />
  <Checkbox checked={valor.cientePrivacidade} onChange={v=>onChange({...valor,cientePrivacidade:v})} label={traduzir("Li e estou ciente da Política de Privacidade.")} />
 </div>;
}
