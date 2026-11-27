package com.estoq.business.usuarios;
import jakarta.validation.constraints.*;
public record PreferenciasDTO(
    @NotNull @Pattern(regexp="claro|escuro|original|terroso|roxo|verde|azul") String tema,
    @Min(90) @Max(125) int fonte,
    @NotNull @Pattern(regexp="pt-BR|es|en") String idioma) {}
