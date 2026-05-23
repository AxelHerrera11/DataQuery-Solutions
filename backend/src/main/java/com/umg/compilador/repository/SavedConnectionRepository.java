package com.umg.compilador.repository;

import com.umg.compilador.model.SavedConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedConnectionRepository extends JpaRepository<SavedConnectionEntity, String> {
    List<SavedConnectionEntity> findAllByOrderByUpdatedAtDesc();
}
