package com.umg.compilador.model;

import jakarta.persistence.*;
import java.util.List;

/**
 * DialectEntity — mapea la tabla `dialect`.
 * Un registro por motor de BD soportado.
 */
@Entity
@Table(name = "dialect")
public class DialectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "default_port", nullable = false)
    private int defaultPort;

    @Column(name = "brand_color", nullable = false, length = 10)
    private String brandColor;

    @Column(name = "driver_class", length = 100)
    private String driverClass;

    @Column(name = "supports_txn", nullable = false)
    private boolean supportsTxn;

    @Column(name = "jdbc_url_pattern", nullable = false, length = 200)
    private String jdbcUrlPattern;

    @Column(name = "since_version", length = 20)
    private String sinceVersion;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Relaciones (lazy para no traer todo en cada consulta)
    @OneToMany(mappedBy = "dialect", fetch = FetchType.LAZY)
    private List<KeywordEntity> keywords;

    @OneToMany(mappedBy = "dialect", fetch = FetchType.LAZY)
    private List<DataTypeMappingEntity> dataTypeMappings;

    @OneToMany(mappedBy = "dialect", fetch = FetchType.LAZY)
    private List<StatementSyntaxEntity> statementSyntaxes;

    // ── Getters ──────────────────────────────────────────────────────
    public Integer getId()            { return id; }
    public String  getName()          { return name; }
    public String  getDisplayName()   { return displayName; }
    public int     getDefaultPort()   { return defaultPort; }
    public String  getBrandColor()    { return brandColor; }
    public String  getDriverClass()   { return driverClass; }
    public boolean isSupportsTxn()    { return supportsTxn; }
    public String  getJdbcUrlPattern(){ return jdbcUrlPattern; }
    public String  getSinceVersion()  { return sinceVersion; }
    public String  getNotes()         { return notes; }
    public List<KeywordEntity>          getKeywords()          { return keywords; }
    public List<DataTypeMappingEntity>  getDataTypeMappings()  { return dataTypeMappings; }
    public List<StatementSyntaxEntity>  getStatementSyntaxes() { return statementSyntaxes; }
}
