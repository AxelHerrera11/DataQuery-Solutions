package com.umg.compilador.controller;

import com.umg.compilador.dialect.DialectRegistry;
import com.umg.compilador.dto.DialectDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dialects")
public class DialectController {

    private final DialectRegistry dialectRegistry;

    public DialectController(DialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    /** GET /api/dialects — lista todos los motores disponibles */
    @GetMapping
    public List<DialectDTO> getAll() {
        return dialectRegistry.getAllDialects().stream().map(d ->
            new DialectDTO(
                d.getName(),
                d.getDisplayName(),
                d.getDefaultPort(),
                d.getBrandColor(),
                d.supportsTransactions()
            )
        ).toList();
    }
}
