package com.umg.compilador.controller;

import com.umg.compilador.dto.CompileRequest;
import com.umg.compilador.dto.CompileResponse;
import com.umg.compilador.service.CompilerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compile")
public class CompilerController {

    private final CompilerService compilerService;

    public CompilerController(CompilerService compilerService) {
        this.compilerService = compilerService;
    }

    /**
     * POST /api/compile
     * Body: { sql, dialect, connectionId? }
     */
    @PostMapping
    public ResponseEntity<CompileResponse> compile(@RequestBody CompileRequest request) {
        CompileResponse response = compilerService.compile(request);
        return ResponseEntity.ok(response);
    }
}
