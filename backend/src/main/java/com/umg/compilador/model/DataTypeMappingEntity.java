package com.umg.compilador.model;

import jakarta.persistence.*;

/**
 * DataTypeMappingEntity — mapea la tabla `data_type_mapping`.
 * Equivalencia: tipo nativo del motor → tipo abstracto del compilador.
 * Ej: MySQL 'TINYINT' → INT | PostgreSQL 'JSONB' → JSON
 */
@Entity
@Table(name = "data_type_mapping",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dialect_id", "native_type"}))
public class DataTypeMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dialect_id", nullable = false)
    private DialectEntity dialect;

    @Column(name = "native_type", nullable = false, length = 60)
    private String nativeType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "abstract_type_id", nullable = false)
    private DataTypeEntity abstractType;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "requires_length", nullable = false)
    private boolean requiresLength;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Integer        getId()             { return id; }
    public DialectEntity  getDialect()        { return dialect; }
    public String         getNativeType()     { return nativeType; }
    public DataTypeEntity getAbstractType()   { return abstractType; }
    public Integer        getMaxLength()      { return maxLength; }
    public boolean        isRequiresLength()  { return requiresLength; }
    public String         getNotes()          { return notes; }
}
