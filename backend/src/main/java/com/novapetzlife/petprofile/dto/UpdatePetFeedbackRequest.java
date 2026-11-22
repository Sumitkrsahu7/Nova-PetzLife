package com.novapetzlife.petprofile.dto;

import jakarta.validation.constraints.Size;

public class UpdatePetFeedbackRequest {

    @Size(max = 2000, message = "Feedback cannot exceed 2000 characters")
    private String feedback;

    public UpdatePetFeedbackRequest() {}

    public UpdatePetFeedbackRequest(String feedback) {
        this.feedback = feedback;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
