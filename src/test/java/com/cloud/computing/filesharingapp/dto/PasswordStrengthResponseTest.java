package com.cloud.computing.filesharingapp.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PasswordStrengthResponse DTO
 */
@DisplayName("PasswordStrengthResponse DTO Tests")
class PasswordStrengthResponseTest {

    private PasswordStrengthResponse response;
    private List<String> sampleRequirements;
    private List<String> sampleSuggestions;

    @BeforeEach
    void setUp() {
        sampleRequirements = Arrays.asList(
            "At least 8 characters",
            "Contains uppercase letter",
            "Contains lowercase letter",
            "Contains number"
        );
        sampleSuggestions = Arrays.asList(
            "Add special characters",
            "Increase length to 12+ characters"
        );
        response = new PasswordStrengthResponse();
    }

    @Test
    @DisplayName("Should create instance with default constructor")
    void shouldCreateInstanceWithDefaultConstructor() {
        PasswordStrengthResponse response = new PasswordStrengthResponse();
        
        assertNotNull(response);
        assertNull(response.getLevel());
        assertEquals(0, response.getScore());
        assertNull(response.getRequirements());
        assertNull(response.getSuggestions());
    }

    @Test
    @DisplayName("Should create instance with parameterized constructor")
    void shouldCreateInstanceWithParameterizedConstructor() {
        PasswordStrengthResponse response = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 
            75, 
            sampleRequirements, 
            sampleSuggestions
        );
        
        assertNotNull(response);
        assertEquals(PasswordStrength.MEDIUM, response.getLevel());
        assertEquals(75, response.getScore());
        assertEquals(sampleRequirements, response.getRequirements());
        assertEquals(sampleSuggestions, response.getSuggestions());
    }

    @Test
    @DisplayName("Should set and get level correctly")
    void shouldSetAndGetLevelCorrectly() {
        response.setLevel(PasswordStrength.STRONG);
        assertEquals(PasswordStrength.STRONG, response.getLevel());
        
        response.setLevel(PasswordStrength.WEAK);
        assertEquals(PasswordStrength.WEAK, response.getLevel());
        
        response.setLevel(null);
        assertNull(response.getLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 25, 50, 75, 100, -1, 101})
    @DisplayName("Should set and get score correctly for various values")
    void shouldSetAndGetScoreCorrectly(int score) {
        response.setScore(score);
        assertEquals(score, response.getScore());
    }

    @Test
    @DisplayName("Should set and get requirements correctly")
    void shouldSetAndGetRequirementsCorrectly() {
        response.setRequirements(sampleRequirements);
        assertEquals(sampleRequirements, response.getRequirements());
        
        List<String> emptyList = Collections.emptyList();
        response.setRequirements(emptyList);
        assertEquals(emptyList, response.getRequirements());
        
        response.setRequirements(null);
        assertNull(response.getRequirements());
    }

    @Test
    @DisplayName("Should set and get suggestions correctly")
    void shouldSetAndGetSuggestionsCorrectly() {
        response.setSuggestions(sampleSuggestions);
        assertEquals(sampleSuggestions, response.getSuggestions());
        
        List<String> emptyList = Collections.emptyList();
        response.setSuggestions(emptyList);
        assertEquals(emptyList, response.getSuggestions());
        
        response.setSuggestions(null);
        assertNull(response.getSuggestions());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        PasswordStrengthResponse response1 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        PasswordStrengthResponse response2 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        PasswordStrengthResponse response3 = new PasswordStrengthResponse(
            PasswordStrength.STRONG, 90, sampleRequirements, sampleSuggestions
        );
        
        // Reflexive
        assertEquals(response1, response1);
        
        // Symmetric
        assertEquals(response1, response2);
        assertEquals(response2, response1);
        
        // Different objects should not be equal
        assertNotEquals(response1, response3);
        assertNotEquals(response1, null);
        assertNotEquals(response1, "not a PasswordStrengthResponse");
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        PasswordStrengthResponse response1 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        PasswordStrengthResponse response2 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        
        // Equal objects should have equal hash codes
        assertEquals(response1.hashCode(), response2.hashCode());
        
        // Hash code should be consistent
        int hashCode1 = response1.hashCode();
        int hashCode2 = response1.hashCode();
        assertEquals(hashCode1, hashCode2);
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        PasswordStrengthResponse response = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        
        String toString = response.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("PasswordStrengthResponse"));
        assertTrue(toString.contains("MEDIUM"));
        assertTrue(toString.contains("75"));
        assertTrue(toString.contains("requirements"));
        assertTrue(toString.contains("suggestions"));
    }

    @Test
    @DisplayName("Should handle null values in equals")
    void shouldHandleNullValuesInEquals() {
        PasswordStrengthResponse response1 = new PasswordStrengthResponse(null, 0, null, null);
        PasswordStrengthResponse response2 = new PasswordStrengthResponse(null, 0, null, null);
        PasswordStrengthResponse response3 = new PasswordStrengthResponse(PasswordStrength.WEAK, 0, null, null);
        
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
    }

    @Test
    @DisplayName("Should handle different score values in equals")
    void shouldHandleDifferentScoreValuesInEquals() {
        PasswordStrengthResponse response1 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        PasswordStrengthResponse response2 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 80, sampleRequirements, sampleSuggestions
        );
        
        assertNotEquals(response1, response2);
    }

    @Test
    @DisplayName("Should handle different requirements in equals")
    void shouldHandleDifferentRequirementsInEquals() {
        List<String> differentRequirements = Arrays.asList("Different requirement");
        
        PasswordStrengthResponse response1 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        PasswordStrengthResponse response2 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, differentRequirements, sampleSuggestions
        );
        
        assertNotEquals(response1, response2);
    }

    @Test
    @DisplayName("Should handle different suggestions in equals")
    void shouldHandleDifferentSuggestionsInEquals() {
        List<String> differentSuggestions = Arrays.asList("Different suggestion");
        
        PasswordStrengthResponse response1 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, sampleSuggestions
        );
        PasswordStrengthResponse response2 = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, sampleRequirements, differentSuggestions
        );
        
        assertNotEquals(response1, response2);
    }

    @Test
    @DisplayName("Should support builder-like pattern")
    void shouldSupportBuilderLikePattern() {
        PasswordStrengthResponse response = new PasswordStrengthResponse();
        response.setLevel(PasswordStrength.STRONG);
        response.setScore(95);
        response.setRequirements(sampleRequirements);
        response.setSuggestions(Collections.emptyList());
        
        assertEquals(PasswordStrength.STRONG, response.getLevel());
        assertEquals(95, response.getScore());
        assertEquals(sampleRequirements, response.getRequirements());
        assertTrue(response.getSuggestions().isEmpty());
    }

    @Test
    @DisplayName("Should handle empty collections")
    void shouldHandleEmptyCollections() {
        List<String> emptyRequirements = Collections.emptyList();
        List<String> emptySuggestions = Collections.emptyList();
        
        PasswordStrengthResponse response = new PasswordStrengthResponse(
            PasswordStrength.WEAK, 25, emptyRequirements, emptySuggestions
        );
        
        assertEquals(PasswordStrength.WEAK, response.getLevel());
        assertEquals(25, response.getScore());
        assertTrue(response.getRequirements().isEmpty());
        assertTrue(response.getSuggestions().isEmpty());
    }

    @Test
    @DisplayName("Should maintain immutability of collections when properly used")
    void shouldMaintainImmutabilityOfCollectionsWhenProperlyUsed() {
        List<String> originalRequirements = Arrays.asList("Requirement 1", "Requirement 2");
        PasswordStrengthResponse response = new PasswordStrengthResponse(
            PasswordStrength.MEDIUM, 75, originalRequirements, sampleSuggestions
        );
        
        // Getting the list should return the same reference (this is expected behavior)
        List<String> retrievedRequirements = response.getRequirements();
        assertEquals(originalRequirements, retrievedRequirements);
        
        // The DTO itself doesn't enforce immutability, but this test documents the behavior
        assertSame(originalRequirements, retrievedRequirements);
    }
}