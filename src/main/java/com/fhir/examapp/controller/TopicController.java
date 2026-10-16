package com.fhir.examapp.controller;

import com.fhir.examapp.domain.SyllabusTopic;
import com.fhir.examapp.repository.SyllabusTopicRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@CrossOrigin(origins = "*")
public class TopicController {

    private final SyllabusTopicRepository topicRepository;

    public TopicController(SyllabusTopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @GetMapping
    public ResponseEntity<List<SyllabusTopic>> getAllTopics() {
        return ResponseEntity.ok(topicRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<SyllabusTopic> createTopic(@RequestBody SyllabusTopic topic) {
        if (topic.getStudyStatus() == null || topic.getStudyStatus().isBlank()) {
            topic.setStudyStatus("PENDING");
        }
        if (topic.getExamWeightPercent() == null) {
            topic.setExamWeightPercent(10.0);
        }
        topic.setCode(topic.getCode().trim().toUpperCase());
        return ResponseEntity.ok(topicRepository.save(topic));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SyllabusTopic> updateTopic(@PathVariable Long id, @RequestBody SyllabusTopic updated) {
        return topicRepository.findById(id)
                .map(existing -> {
                    if (updated.getCode() != null) existing.setCode(updated.getCode().trim().toUpperCase());
                    if (updated.getName() != null) existing.setName(updated.getName());
                    if (updated.getExamWeightPercent() != null) existing.setExamWeightPercent(updated.getExamWeightPercent());
                    if (updated.getStudyStatus() != null) existing.setStudyStatus(updated.getStudyStatus());
                    return ResponseEntity.ok(topicRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
