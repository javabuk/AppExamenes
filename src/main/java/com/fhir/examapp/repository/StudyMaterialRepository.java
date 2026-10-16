package com.fhir.examapp.repository;

import com.fhir.examapp.domain.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {
    List<StudyMaterial> findByTopicId(Long topicId);

    @Query("SELECT m FROM StudyMaterial m WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(m.contentText) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<StudyMaterial> searchMaterials(@Param("query") String query);
}
