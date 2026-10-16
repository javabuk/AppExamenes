package com.fhir.examapp.dto;

public class SrsReviewRequest {
    private Integer rating; // 0 (Blackout), 1 (Incorrecto), 2 (Difícil), 3 (Bueno), 4 (Fácil), 5 (Perfecto)

    public SrsReviewRequest() {}

    public SrsReviewRequest(Integer rating) {
        this.rating = rating;
    }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
}
