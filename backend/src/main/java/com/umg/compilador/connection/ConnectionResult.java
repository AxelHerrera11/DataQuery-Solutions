package com.umg.compilador.connection;

/**
 * ConnectionResult — resultado de un test de conexión.
 */
public record ConnectionResult(
    boolean success,
    String  message,
    String  serverVersion
) {
    public static ConnectionResult ok(String version) {
        return new ConnectionResult(true, "Conexión exitosa", version);
    }
    public static ConnectionResult fail(String reason) {
        return new ConnectionResult(false, reason, null);
    }
}
