import type { Perfil } from "./types";

// Rótulos da interface; os identificadores enviados à API permanecem inalterados.
export const PERFIL_LABEL: Record<Perfil, string> = {
  ADMIN: "Administrador",
  COZINHA: "Operacional",
  NUTRICIONISTA: "Nutricionista",
  PLATAFORMA: "Plataforma",
};
