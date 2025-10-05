package com.cloud.computing.filesharingapp.dto;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for password strength evaluation
 */
public class PasswordStrengthResponse {
    private PasswordStrength level;
    private int score; // 0-100
    private List<String> requirements;
    private List<String> suggestions;

    public PasswordStrengthResponse() {
    }

    public PasswordStrengthResponse(PasswordStrength level, int score, List<String> requirements, List<String> suggestions) {
        this.level = level;
        this.score = score;
        this.requirements = requirements;
        this.suggestions = suggestions;
    }

    public PasswordStrength getLevel() {
        return level;
    }

    public void setLevel(PasswordStrength level) {
        this.level = level;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordStrengthResponse that = (PasswordStrengthResponse) o;
        return score == that.score &&
                level == that.level &&
                Objects.equals(requirements, that.requirements) &&
                Objects.equals(suggestions, that.suggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, score, requirements, suggestions);
    }

    @Override
    public String toString() {
        return "PasswordStrengthResponse{" +
                "level=" + level +
                ", score=" + score +
                ", requirements=" + requirements +
                ", suggestions=" + suggestions +
                '}';
    }
}