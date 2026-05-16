package com.umg.compilador.model;

import jakarta.persistence.*;

/**
 * SqlStatementEntity — mapea la tabla `sql_statement`.
 * SELECT, INSERT, UPDATE, DELETE, CREATE_TABLE, etc.
 */
@Entity
@Table(name = "sql_statement")
public class SqlStatementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(nullable = false, length = 20)
    private String category; // DML | DDL | DCL | TCL

    @Column(columnDefinition = "TEXT")
    private String description;

    public Integer getId()          { return id; }
    public String  getName()        { return name; }
    public String  getCategory()    { return category; }
    public String  getDescription() { return description; }
}
