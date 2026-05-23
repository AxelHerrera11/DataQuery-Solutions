package com.umg.compilador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record ConnectionRequest(
    @NotBlank(message = "El nombre es obligatorio")
    String name,
    @NotBlank(message = "El dialecto es obligatorio")
    String dialect,
    @NotBlank(message = "El host es obligatorio")
    String host,
    @PositiveOrZero
    int    port,
    @NotBlank(message = "La base de datos es obligatoria")
    String database,
    @NotBlank(message = "El usuario es obligatorio")
    String username,
    String password
) {}
