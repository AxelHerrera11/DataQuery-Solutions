package com.umg.compilador.repository;

import com.umg.compilador.model.KeywordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface KeywordRepository extends JpaRepository<KeywordEntity, Integer> {

    /** Todas las keywords de un dialecto (por nombre del dialecto). */
    @Query("SELECT k FROM KeywordEntity k WHERE k.dialect.name = :dialectName")
    List<KeywordEntity> findByDialectName(@Param("dialectName") String dialectName);

    /** Solo las palabras reservadas de un dialecto. */
    @Query("SELECT k FROM KeywordEntity k WHERE k.dialect.name = :dialectName AND k.isReserved = true")
    List<KeywordEntity> findReservedByDialectName(@Param("dialectName") String dialectName);

    /** Keywords de un dialecto filtradas por token_type. */
    @Query("SELECT k FROM KeywordEntity k WHERE k.dialect.name = :dialectName AND k.tokenType = :tokenType")
    List<KeywordEntity> findByDialectNameAndTokenType(
        @Param("dialectName") String dialectName,
        @Param("tokenType")   String tokenType
    );

    /** Solo los words como Set<String> — útil para inyectar al Lexer. */
    @Query("SELECT k.word FROM KeywordEntity k WHERE k.dialect.name = :dialectName")
    Set<String> findWordsByDialectName(@Param("dialectName") String dialectName);

    /** Keywords que aplican a una sentencia específica en un dialecto. */
    @Query("""
        SELECT k FROM KeywordEntity k
        JOIN StatementKeywordEntity sk ON sk.keyword = k
        WHERE k.dialect.name      = :dialectName
          AND sk.statement.name   = :statementName
          AND sk.role            != 'FORBIDDEN'
        ORDER BY sk.positionHint
        """)
    List<KeywordEntity> findByDialectAndStatement(
        @Param("dialectName")   String dialectName,
        @Param("statementName") String statementName
    );
}
