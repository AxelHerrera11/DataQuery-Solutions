package com.umg.compilador.model;

import jakarta.persistence.*;

/**
 * KeywordEntity — mapea la tabla `keyword`.
 * Una keyword por dialecto, con tipo y categoría.
 */
@Entity
@Table(name = "keyword",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dialect_id", "word"}))
public class KeywordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dialect_id", nullable = false)
    private DialectEntity dialect;

    @Column(nullable = false, length = 60)
    private String word;

    @Column(name = "token_type", nullable = false, length = 30)
    private String tokenType;
    // RESERVED | FUNCTION | DATA_TYPE | CLAUSE | OPERATOR | MODIFIER | PRAGMA

    @Column(length = 30)
    private String category;
    // DML | DDL | DCL | TCL | AGGREGATE | WINDOW | JSON | STRING | DATETIME...

    @Column(name = "is_reserved", nullable = false)
    private boolean isReserved;

    @Column(name = "since_version", length = 20)
    private String sinceVersion;

    @Column(nullable = false)
    private boolean deprecated;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Getters ──────────────────────────────────────────────────────
    public Integer       getId()           { return id; }
    public DialectEntity getDialect()      { return dialect; }
    public String        getWord()         { return word; }
    public String        getTokenType()    { return tokenType; }
    public String        getCategory()     { return category; }
    public boolean       isReserved()      { return isReserved; }
    public String        getSinceVersion() { return sinceVersion; }
    public boolean       isDeprecated()    { return deprecated; }
    public String        getNotes()        { return notes; }
}
