package com.umg.compilador.schema;

import java.util.Set;

public enum DataType {
    INT, FLOAT, VARCHAR, CHAR, BOOLEAN, DATETIME, DATE, TIME,
    TEXT, BLOB, JSON, UUID, ARRAY, UNKNOWN;

    private static final Set<DataType> NUMERIC_TYPES = Set.of(INT, FLOAT);
    private static final Set<DataType> STRING_TYPES = Set.of(VARCHAR, CHAR, TEXT);
    private static final Set<DataType> DATE_TYPES = Set.of(DATETIME, DATE, TIME);
    private static final Set<DataType> BINARY_TYPES = Set.of(BLOB);

    public boolean isNumeric() { return NUMERIC_TYPES.contains(this); }
    public boolean isString()  { return STRING_TYPES.contains(this); }
    public boolean isDate()    { return DATE_TYPES.contains(this); }
    public boolean isBinary()  { return BINARY_TYPES.contains(this); }

    public boolean isCompatibleWith(DataType other) {
        if (this == other || this == UNKNOWN || other == UNKNOWN) return true;

        if (this.isNumeric() && other.isNumeric()) return true;
        if (this.isString()  && other.isString())  return true;
        if (this.isDate()    && other.isDate())    return true;

        return false;
    }

    public static DataType fromString(String name) {
        if (name == null) return UNKNOWN;
        String upper = name.toUpperCase();
        for (DataType dt : values()) {
            if (dt.name().equals(upper)) return dt;
        }
        return UNKNOWN;
    }

    @Override public String toString() { return name(); }
}
