package com.umg.compilador.model;

import jakarta.persistence.*;

/**
 * StatementSyntaxEntity — mapea la tabla `statement_syntax`.
 * Qué sentencias soporta cada dialecto y su template de sintaxis.
 */
@Entity
@Table(name = "statement_syntax",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dialect_id", "statement_id"}))
public class StatementSyntaxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dialect_id", nullable = false)
    private DialectEntity dialect;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "statement_id", nullable = false)
    private SqlStatementEntity statement;

    @Column(nullable = false)
    private boolean supported;

    @Column(name = "syntax_template", columnDefinition = "TEXT")
    private String syntaxTemplate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Integer              getId()             { return id; }
    public DialectEntity        getDialect()        { return dialect; }
    public SqlStatementEntity   getStatement()      { return statement; }
    public boolean              isSupported()       { return supported; }
    public String               getSyntaxTemplate() { return syntaxTemplate; }
    public String               getNotes()          { return notes; }
}
