package com.umg.compilador.repository;

import com.umg.compilador.model.StatementKeywordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatementKeywordRepository extends JpaRepository<StatementKeywordEntity, Integer> {

    /** Keywords requeridas de una sentencia para un dialecto. */
    @Query("""
        SELECT sk FROM StatementKeywordEntity sk
        WHERE sk.statement.name   = :statementName
          AND sk.keyword.dialect.name = :dialectName
          AND sk.role = 'REQUIRED'
        """)
    List<StatementKeywordEntity> findRequiredByStatementAndDialect(
        @Param("statementName") String statementName,
        @Param("dialectName")   String dialectName
    );

    /** Todas las keywords de una sentencia para un dialecto (cualquier rol). */
    @Query("""
        SELECT sk FROM StatementKeywordEntity sk
        WHERE sk.statement.name         = :statementName
          AND sk.keyword.dialect.name   = :dialectName
        ORDER BY sk.positionHint NULLS LAST
        """)
    List<StatementKeywordEntity> findByStatementAndDialect(
        @Param("statementName") String statementName,
        @Param("dialectName")   String dialectName
    );
}
