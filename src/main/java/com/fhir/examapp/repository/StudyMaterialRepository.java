package com.fhir.examapp.repository;

import com.fhir.examapp.domain.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {

    @Query("SELECT m FROM StudyMaterial m WHERE (m.deleted IS NULL OR m.deleted = false)")
    List<StudyMaterial> findAllActive();

    @Query("SELECT m FROM StudyMaterial m WHERE m.certification.id = :certId AND (m.deleted IS NULL OR m.deleted = false)")
    List<StudyMaterial> findAllActiveByCertificationId(@Param("certId") Long certId);

    @Query("SELECT m FROM StudyMaterial m WHERE m.topic.id = :topicId AND (m.deleted IS NULL OR m.deleted = false)")
    List<StudyMaterial> findByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT m FROM StudyMaterial m WHERE m.certification.id = :certId AND (m.area.id = :areaId OR m.topic.id = :areaId) AND (m.deleted IS NULL OR m.deleted = false)")
    List<StudyMaterial> findByCertificationIdAndAreaId(@Param("certId") Long certId, @Param("areaId") Long areaId);

    @Query("SELECT m FROM StudyMaterial m WHERE (m.deleted IS NULL OR m.deleted = false) AND (LOWER(m.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(m.contentText) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<StudyMaterial> searchMaterials(@Param("query") String query);

    @Query("SELECT m FROM StudyMaterial m WHERE m.certification.id = :certId AND (m.deleted IS NULL OR m.deleted = false) AND (LOWER(m.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(m.contentText) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<StudyMaterial> searchMaterialsByCertificationId(@Param("query") String query, @Param("certId") Long certId);
}
