package com.umg.compilador.dto;

import java.util.List;

/**
 * CompileResponse — respuesta del POST /api/compile
 */
public record CompileResponse(
    boolean         valid,
    List<CompileError> errors,
    List<CompileError> warnings,
    String          astJson,
    PhaseResult     lexerPhase,
    PhaseResult     parserPhase,
    PhaseResult     semanticPhase
) {
    public record PhaseResult(boolean passed, String message) {}
}
