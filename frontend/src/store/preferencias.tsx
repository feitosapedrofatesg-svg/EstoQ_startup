import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { useAuth } from "./auth";
import { api } from "../lib/api";
import { definirIdioma, type Idioma } from "../lib/i18n";

export type Tema="claro"|"escuro"|"original"|"terroso"|"roxo"|"verde"|"azul";
export interface Preferencias {tema:Tema;fonte:number;idioma:Idioma}
export const PADRAO:Preferencias={tema:"claro",fonte:100,idioma:"pt-BR"};
function aplicar(p:Preferencias){document.documentElement.dataset.tema=p.tema;document.documentElement.style.fontSize=`${p.fonte}%`;definirIdioma(p.idioma);}
const Ctx=createContext<{preferencias:Preferencias;salvar:(p:Preferencias)=>Promise<void>;erro:string}>({preferencias:PADRAO,salvar:async()=>{},erro:""});
export function PreferenciasProvider({children}:{children:ReactNode}) {
 const {user}=useAuth();const [preferencias,setPreferencias]=useState(PADRAO);const [erro,setErro]=useState("");const revisao=useRef(0);
 const chave=user?`estoq-preferencias:${user.restauranteId??0}:${user.id}`:null;
 useEffect(()=>{
  let ativo=true;const geracao=++revisao.current;let inicial=PADRAO;
  try{if(chave){const salvo=JSON.parse(localStorage.getItem(chave)??"null");if(salvo&&["claro","escuro","original","terroso","roxo","verde","azul"].includes(salvo.tema)&&["pt-BR","es","en"].includes(salvo.idioma)&&salvo.fonte>=90&&salvo.fonte<=125)inicial=salvo;}}catch{/* Usa o padrão apenas desta conta. */}
  aplicar(inicial);setPreferencias(inicial);
  if(chave)void api.get<Preferencias>("/api/auth/preferencias").then(p=>{if(ativo&&revisao.current===geracao&&p.tema){aplicar(p);setPreferencias(p);try{localStorage.setItem(chave,JSON.stringify(p));}catch{/* Preferência segue salva no servidor. */}}}).catch(()=>{});
  return()=>{ativo=false;};
 },[chave]);
 const salvar=async(p:Preferencias)=>{
  const geracao=++revisao.current;setErro("");
  try{await api.put("/api/auth/preferencias",p);if(geracao!==revisao.current)return;setPreferencias(p);aplicar(p);if(chave)try{localStorage.setItem(chave,JSON.stringify(p));}catch{/* O servidor já preservou a preferência. */}}
  catch(e){if(geracao!==revisao.current)return;setErro(e instanceof Error?e.message:"Não foi possível salvar as preferências.");}
 };
 return <Ctx.Provider value={{preferencias,salvar,erro}}>{children}</Ctx.Provider>;
}
export const usePreferencias=()=>useContext(Ctx);
