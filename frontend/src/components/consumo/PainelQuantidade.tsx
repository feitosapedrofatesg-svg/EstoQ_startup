import { useState } from "react";
import { Button, Modal } from "../UI";

/** Teclado da própria aplicação: o campo de quantidade nunca abre o teclado do aparelho. */
export function PainelQuantidade({ produto, unidade, quantidade, limite, onConfirmar, onClose }: {
  produto: string; unidade: string; quantidade: number; limite: number;
  onConfirmar: (quantidade: number) => void; onClose: () => void;
}) {
  const [texto, setTexto] = useState(String(quantidade).replace(".", ","));
  const [erro, setErro] = useState("");
  const digitar = (tecla: string) => {
    setErro("");
    setTexto(anterior => {
      if (tecla === "apagar") return anterior.slice(0, -1);
      if (tecla === "limpar") return "";
      if (tecla === ",") return anterior.includes(",") ? anterior : (anterior || "0") + ",";
      const proximo = anterior === "0" ? tecla : anterior + tecla;
      return /^\d{0,15}(,\d{0,3})?$/.test(proximo) ? proximo : anterior;
    });
  };
  const confirmar = () => {
    const valor = Number((texto || "0").replace(",", "."));
    if (!Number.isFinite(valor) || valor > limite) return setErro("A quantidade ultrapassa o saldo disponível.");
    onConfirmar(valor); onClose();
  };
  return <Modal open title={`Quantidade de ${produto}`} width="sm" onClose={onClose}
    footer={<><Button variant="ghost" onClick={onClose}>Cancelar</Button><Button onClick={confirmar}>Aplicar quantidade</Button></>}>
    <output className="consumo-numero" aria-live="polite" aria-label="Quantidade escolhida">{texto || "0"} {unidade.toLowerCase()}</output>
    {erro && <p role="alert" className="field__error">{erro}</p>}
    <div className="consumo-teclado" onKeyDown={evento => {
      if (/^\d$/.test(evento.key) || evento.key === "," || evento.key === ".") { evento.preventDefault(); digitar(evento.key === "." ? "," : evento.key); }
      if (evento.key === "Backspace") { evento.preventDefault(); digitar("apagar"); }
    }}>
      {["1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "apagar"].map(tecla =>
        <Button key={tecla} variant="outline" aria-label={tecla === "apagar" ? "Apagar último dígito" : tecla === "," ? "Vírgula decimal" : tecla} onClick={() => digitar(tecla)}>{tecla === "apagar" ? "⌫" : tecla}</Button>)}
      <Button variant="ghost" onClick={() => digitar("limpar")}>Limpar</Button>
    </div>
  </Modal>;
}
