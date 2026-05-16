package com.umg.compilador.repository;

import com.umg.compilador.model.DataTypeMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataTypeMappingRepository extends JpaRepository<DataTypeMappingEntity, Integer> {

    /** Todos los mappings de un dialecto. */
    @Query("SELECT m FROM DataTypeMappingEntity m WHERE m.dialect.name = :dialectName")
    List<DataTypeMappingEntity> findByDialectName(@Param("dialectName") String dialectName);

    /** Tipo abstracto dado el tipo nativo y el dialecto. */
    @Query("""
        SELECT m FROM DataTypeMappingEntity m
        WHERE m.dialect.name = :dialectName
          AND UPPER(m.nativeType) = UPPER(:nativeType)
        """)
    Optional<DataTypeMappingEntity> findAbstractType(
        @Param("dialectName") String dialectName,
        @Param("nativeType")  String nativeType
    );
}
