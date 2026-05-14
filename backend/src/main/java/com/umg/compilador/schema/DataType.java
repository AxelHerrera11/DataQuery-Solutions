package com.umg.compilador.schema;

public enum DataType {
    INT, FLOAT, VARCHAR, BOOLEAN, DATETIME, TEXT, BLOB, JSON, UNKNOWN;
    @Override public String toString() { return name(); }
}
