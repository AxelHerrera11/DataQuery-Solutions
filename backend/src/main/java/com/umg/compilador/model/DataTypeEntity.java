package com.umg.compilador.model;

import jakarta.persistence.*;

/**
 * DataTypeEntity — mapea la tabla `data_type`.
 * Tipos abstractos del compilador: INT, FLOAT, VARCHAR...
 */
@Entity
@Table(name = "data_type")
public class DataTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Integer getId()          { return id; }
    public String  getName()        { return name; }
    public String  getDescription() { return description; }
}
