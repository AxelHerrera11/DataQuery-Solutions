package com.umg.compilador.controller;

import com.umg.compilador.dto.catalog.*;
import com.umg.compilador.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CatalogController — endpoints del catálogo de la BD del compilador.
 *
 * GET /api/catalog/{dialect}/keywords                  → todas las keywords
 * GET /api/catalog/{dialect}/keywords?type=FUNCTION    → filtradas por tipo
 * GET /api/catalog/{dialect}/keywords?statement=SELECT → por sentencia
 * GET /api/catalog/{dialect}/types                     → mappings de tipos
 * GET /api/catalog/{dialect}/syntax                    → todas las sentencias
 * GET /api/catalog/{dialect}/syntax/{statement}        → una sentencia
 * GET /api/catalog/{dialect}/syntax/{statement}/keywords → keywords de la sentencia
 */
@RestController
@RequestMapping("/api/catalog/{dialect}")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/keywords")
    public List<KeywordDTO> getKeywords(
        @PathVariable String dialect,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String statement
    ) {
        if (statement != null) return catalogService.getKeywordsByStatement(dialect, statement);
        if (type      != null) return catalogService.getKeywordsByType(dialect, type);
        return catalogService.getKeywords(dialect);
    }

    @GetMapping("/types")
    public List<DataTypeMappingDTO> getTypes(@PathVariable String dialect) {
        return catalogService.getDataTypes(dialect);
    }

    @GetMapping("/syntax")
    public List<SyntaxDTO> getAllSyntax(@PathVariable String dialect) {
        return catalogService.getSupportedStatements(dialect);
    }

    @GetMapping("/syntax/{statement}")
    public SyntaxDTO getSyntax(
        @PathVariable String dialect,
        @PathVariable String statement
    ) {
        return catalogService.getSyntax(dialect, statement);
    }

    @GetMapping("/syntax/{statement}/keywords")
    public List<StatementKeywordDTO> getStatementKeywords(
        @PathVariable String dialect,
        @PathVariable String statement
    ) {
        return catalogService.getStatementKeywords(dialect, statement);
    }
}
