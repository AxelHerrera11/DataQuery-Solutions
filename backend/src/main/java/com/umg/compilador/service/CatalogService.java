package com.umg.compilador.service;

import com.umg.compilador.dto.catalog.*;
import com.umg.compilador.model.*;
import com.umg.compilador.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CatalogService — expone el catálogo de keywords, tipos y sintaxis.
 * Usado por el frontend para mostrar autocompletado, ayuda contextual
 * y el explorador de sintaxis por dialecto.
 */
@Service
public class CatalogService {

    private final KeywordRepository         keywordRepo;
    private final DataTypeMappingRepository typeMappingRepo;
    private final StatementSyntaxRepository syntaxRepo;
    private final StatementKeywordRepository stmtKeywordRepo;
    private final DialectRepository         dialectRepo;

    public CatalogService(KeywordRepository keywordRepo,
                          DataTypeMappingRepository typeMappingRepo,
                          StatementSyntaxRepository syntaxRepo,
                          StatementKeywordRepository stmtKeywordRepo,
                          DialectRepository dialectRepo) {
        this.keywordRepo      = keywordRepo;
        this.typeMappingRepo  = typeMappingRepo;
        this.syntaxRepo       = syntaxRepo;
        this.stmtKeywordRepo  = stmtKeywordRepo;
        this.dialectRepo      = dialectRepo;
    }

    // ── Keywords ──────────────────────────────────────────────────────

    /** Todas las keywords de un dialecto (para autocompletado Monaco). */
    public List<KeywordDTO> getKeywords(String dialectName) {
        return keywordRepo.findByDialectName(dialectName.toUpperCase())
            .stream().map(this::toKeywordDTO).toList();
    }

    /** Keywords de un dialecto filtradas por tokenType. */
    public List<KeywordDTO> getKeywordsByType(String dialectName, String tokenType) {
        return keywordRepo.findByDialectNameAndTokenType(dialectName.toUpperCase(), tokenType)
            .stream().map(this::toKeywordDTO).toList();
    }

    /** Keywords que aplican a una sentencia específica. */
    public List<KeywordDTO> getKeywordsByStatement(String dialectName, String statementName) {
        return keywordRepo.findByDialectAndStatement(
                dialectName.toUpperCase(), statementName.toUpperCase())
            .stream().map(this::toKeywordDTO).toList();
    }

    // ── Tipos de dato ─────────────────────────────────────────────────

    /** Mappings de tipos nativos del dialecto a tipos abstractos. */
    public List<DataTypeMappingDTO> getDataTypes(String dialectName) {
        return typeMappingRepo.findByDialectName(dialectName.toUpperCase())
            .stream().map(this::toTypeMappingDTO).toList();
    }

    // ── Sintaxis ──────────────────────────────────────────────────────

    /** Template de sintaxis de una sentencia para un dialecto. */
    public SyntaxDTO getSyntax(String dialectName, String statementName) {
        return syntaxRepo.findByDialectAndStatement(
                dialectName.toUpperCase(), statementName.toUpperCase())
            .map(this::toSyntaxDTO)
            .orElse(new SyntaxDTO(statementName, false, null,
                    "Sentencia no registrada para " + dialectName));
    }

    /** Todas las sentencias soportadas por un dialecto. */
    public List<SyntaxDTO> getSupportedStatements(String dialectName) {
        return syntaxRepo.findSupportedByDialect(dialectName.toUpperCase())
            .stream().map(this::toSyntaxDTO).toList();
    }

    /** Keywords de una sentencia con su rol y posición. */
    public List<StatementKeywordDTO> getStatementKeywords(String dialectName, String statementName) {
        return stmtKeywordRepo.findByStatementAndDialect(
                statementName.toUpperCase(), dialectName.toUpperCase())
            .stream().map(this::toStmtKeywordDTO).toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────

    private KeywordDTO toKeywordDTO(KeywordEntity k) {
        return new KeywordDTO(
            k.getWord(), k.getTokenType(), k.getCategory(),
            k.isReserved(), k.getSinceVersion(), k.isDeprecated(), k.getNotes()
        );
    }

    private DataTypeMappingDTO toTypeMappingDTO(DataTypeMappingEntity m) {
        return new DataTypeMappingDTO(
            m.getNativeType(), m.getAbstractType().getName(),
            m.getMaxLength(), m.isRequiresLength(), m.getNotes()
        );
    }

    private SyntaxDTO toSyntaxDTO(StatementSyntaxEntity s) {
        return new SyntaxDTO(
            s.getStatement().getName(), s.isSupported(),
            s.getSyntaxTemplate(), s.getNotes()
        );
    }

    private StatementKeywordDTO toStmtKeywordDTO(StatementKeywordEntity sk) {
        return new StatementKeywordDTO(
            sk.getKeyword().getWord(), sk.getRole(),
            sk.getPositionHint(), sk.getNotes()
        );
    }
}
