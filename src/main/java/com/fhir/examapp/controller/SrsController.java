package com.fhir.examapp.controller;

import com.fhir.examapp.domain.QuestionSrsState;
import com.fhir.examapp.dto.FlashcardDTO;
import com.fhir.examapp.dto.SrsReviewRequest;
import com.fhir.examapp.service.SpacedRepetitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srs")
@CrossOrigin(origins = "*")
public class SrsController {

    private final SpacedRepetitionService srsService;

    public SrsController(SpacedRepetitionService srsService) {
        this.srsService = srsService;
    }

    @GetMapping("/due")
    public ResponseEntity<List<FlashcardDTO>> getDueFlashcards() {
        return ResponseEntity.ok(srsService.getDueFlashcards());
    }

    @PostMapping("/review/{questionId}")
    public ResponseEntity<QuestionSrsState> reviewFlashcard(
            @PathVariable Long questionId,
            @RequestBody SrsReviewRequest request) {
        int rating = request.getRating() != null ? request.getRating() : 3;
        return ResponseEntity.ok(srsService.recordManualReview(questionId, rating));
    }
}
