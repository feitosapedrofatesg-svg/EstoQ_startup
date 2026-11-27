import { traduzir } from "../lib/i18n";
import { useState } from "react";
import { Button, Modal, Field, Select, AlertBanner } from "./UI";
import { usePreferencias, type Tema } from "../store/preferencias";
import type { Idioma } from "../lib/i18n";
import { t } from "../lib/i18n";
export function Configuracoes() {
 const [aberto,setAberto]=useState(false);const [copiado,setCopiado]=useState(false);const [erroCopia,setErroCopia]=useState("");const [salvando,setSalvando]=useState(false);
 const {preferencias:p,salvar,erro}=usePreferencias();
 const mudar=async(valor:Partial<typeof p>)=>{setSalvando(true);try{await salvar({...p,...valor});}finally{setSalvando(false);}};
 return <><Button variant="ghost" icon="settings" aria-label={traduzir("Configurações")} title={traduzir("Configurações")} onClick={()=>setAberto(true)} />
 <Modal open={aberto} title={traduzir("Configurações")} onClose={()=>setAberto(false)} footer={<Button onClick={()=>setAberto(false)}>{" "}{traduzir("Fechar")}{" "}</Button>}>
  {erro&&<AlertBanner tone="bad">{traduzir(erro)}</AlertBanner>}
  <Field label={traduzir("Tema")} htmlFor="config-tema"><Select id="config-tema" disabled={salvando} value={p.tema} onChange={e=>void mudar({tema:e.target.value as Tema})}>
   {([ ["claro","Claro"],["escuro","Escuro"],["original","Original"],["terroso","Terroso"],["roxo","Roxo"],["verde","Verde"],["azul","Azul e turquesa"] ] as const).map(([v,n])=><option key={v} value={v}>{t(n)}</option>)}
  </Select></Field>
  <fieldset className="preferencias-fonte"><legend>{t("Tamanho da fonte")}</legend>
   <Button variant="outline" aria-label={traduzir("Diminuir fonte")} disabled={salvando||p.fonte<=90} onClick={()=>void mudar({fonte:p.fonte-5})}>{" "}{traduzir("A−")}{" "}</Button><span aria-live="polite">{p.fonte}%</span>
   <Button variant="outline" aria-label={traduzir("Aumentar fonte")} disabled={salvando||p.fonte>=125} onClick={()=>void mudar({fonte:p.fonte+5})}>{" "}{traduzir("A+")}{" "}</Button>
   <Button variant="ghost" disabled={salvando} onClick={()=>void mudar({fonte:100})}>{" "}{traduzir("Restaurar padrão")}{" "}</Button>
  </fieldset>
  <Field label={traduzir("Idioma")} htmlFor="config-idioma"><Select id="config-idioma" disabled={salvando} value={p.idioma} onChange={e=>void mudar({idioma:e.target.value as Idioma})}><option value="pt-BR">{" "}{traduzir("Português (Brasil)")}{" "}</option><option value="es">{" "}{traduzir("Español")}{" "}</option><option value="en">{" "}{traduzir("English")}{" "}</option></Select></Field>
  <p><a href="/termos" target="_blank" rel="noreferrer">{t("Termos de Uso")}</a> · <a href="/privacidade" target="_blank" rel="noreferrer">{t("Política de Privacidade")}</a></p>
  <p className="muted">{" "}{traduzir("Documentos disponíveis em português (Brasil). A consulta não solicita novo aceite.")}{" "}</p>
  <h3>{t("Suporte")}</h3><p>{" "}{traduzir("estoq.sistema@gmail.com")}{" "}</p>
  <div className="form-actions"><Button variant="outline" onClick={()=>void navigator.clipboard.writeText("estoq.sistema@gmail.com").then(()=>{setCopiado(true);setErroCopia("");}).catch(()=>setErroCopia(t("Não foi possível copiar. Selecione o endereço e copie manualmente.")))}>{copiado?t("E-mail copiado"):t("Copiar e-mail")}</Button><a className="btn btn--outline btn--raised" href="mailto:estoq.sistema@gmail.com">{t("Abrir e-mail")}</a></div>
  {erroCopia&&<p role="alert">{erroCopia}</p>}
 </Modal></>;
}
