package com.umg.compilador.controller;

import com.umg.compilador.connection.ConnectionResult;
import com.umg.compilador.dto.*;
import com.umg.compilador.service.ConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    /** GET /api/connections — lista todas las conexiones guardadas */
    @GetMapping
    public List<ConnectionDTO> getAll() {
        return connectionService.getAll();
    }

    /** POST /api/connections — guarda una nueva conexión */
    @PostMapping
    public ResponseEntity<ConnectionDTO> save(@RequestBody ConnectionRequest request) {
        return ResponseEntity.ok(connectionService.save(request));
    }

    /** DELETE /api/connections/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/connections/test — prueba sin guardar */
    @PostMapping("/test")
    public ResponseEntity<ConnectionResult> test(@RequestBody ConnectionRequest request) {
        return ResponseEntity.ok(connectionService.test(request));
    }

    /** GET /api/connections/{id}/schema — schema real de la BD */
    @GetMapping("/{id}/schema")
    public ResponseEntity<SchemaDTO> getSchema(@PathVariable String id) {
        return ResponseEntity.ok(connectionService.getSchema(id));
    }
}
