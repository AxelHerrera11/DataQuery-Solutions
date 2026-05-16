package com.umg.compilador.repository;

import com.umg.compilador.model.DialectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DialectRepository extends JpaRepository<DialectEntity, Integer> {

    Optional<DialectEntity> findByNameIgnoreCase(String name);

    /** Trae el dialecto con sus keywords en una sola consulta (evita N+1). */
    @Query("SELECT DISTINCT d FROM DialectEntity d LEFT JOIN FETCH d.keywords WHERE d.name = :name")
    Optional<DialectEntity> findByNameWithKeywords(@Param("name") String name);

    /** Todos los dialectos con sus keywords (para cargar el DialectRegistry al arrancar). */
    @Query("SELECT DISTINCT d FROM DialectEntity d LEFT JOIN FETCH d.keywords")
    List<DialectEntity> findAllWithKeywords();
}
