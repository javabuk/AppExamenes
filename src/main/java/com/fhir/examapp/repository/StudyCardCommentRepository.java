package com.fhir.examapp.repository;

import com.fhir.examapp.domain.StudyCardComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyCardCommentRepository extends JpaRepository<StudyCardComment, Long> {

    List<StudyCardComment> findByMaterialIdOrderByCardIndexAscIdAsc(Long materialId);

    List<StudyCardComment> findByMaterialIdAndCardIndexOrderByIdAsc(Long materialId, Integer cardIndex);

    long countByMaterialId(Long materialId);

    void deleteByMaterialId(Long materialId);
}
