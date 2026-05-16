package com.umg.compilador.repository;

import com.umg.compilador.model.StatementSyntaxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatementSyntaxRepository extends JpaRepository<StatementSyntaxEntity, Integer> {

    /** Sintaxis de un dialecto para una sentencia específica. */
    @Query("""
        SELECT s FROM StatementSyntaxEntity s
        WHERE s.dialect.name    = :dialectName
          AND s.statement.name  = :statementName
        """)
    Optional<StatementSyntaxEntity> findByDialectAndStatement(
        @Param("dialectName")   String dialectName,
        @Param("statementName") String statementName
    );

    /** Todas las sentencias soportadas por un dialecto. */
    @Query("""
        SELECT s FROM StatementSyntaxEntity s
        WHERE s.dialect.name = :dialectName
          AND s.supported    = true
        """)
    List<StatementSyntaxEntity> findSupportedByDialect(@Param("dialectName") String dialectName);

    /** Todas las sentencias con su template para un dialecto. */
    @Query("SELECT s FROM StatementSyntaxEntity s WHERE s.dialect.name = :dialectName")
    List<StatementSyntaxEntity> findAllByDialectName(@Param("dialectName") String dialectName);
}
