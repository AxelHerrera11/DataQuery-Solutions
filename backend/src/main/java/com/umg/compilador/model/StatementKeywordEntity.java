package com.umg.compilador.model;

import jakarta.persistence.*;

/**
 * StatementKeywordEntity — mapea la tabla `statement_keyword`.
 * Qué keywords aplican a cada sentencia, con qué rol y posición.
 */
@Entity
@Table(name = "statement_keyword",
       uniqueConstraints = @UniqueConstraint(columnNames = {"statement_id", "keyword_id"}))
public class StatementKeywordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "statement_id", nullable = false)
    private SqlStatementEntity statement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "keyword_id", nullable = false)
    private KeywordEntity keyword;

    @Column(nullable = false, length = 30)
    private String role;
    // REQUIRED | OPTIONAL | EXCLUSIVE | FORBIDDEN

    @Column(name = "position_hint", length = 30)
    private String positionHint;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Integer            getId()           { return id; }
    public SqlStatementEntity getStatement()    { return statement; }
    public KeywordEntity      getKeyword()      { return keyword; }
    public String             getRole()         { return role; }
    public String             getPositionHint() { return positionHint; }
    public String             getNotes()        { return notes; }
}
