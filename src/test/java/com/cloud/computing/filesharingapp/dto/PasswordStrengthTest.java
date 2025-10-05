package com.cloud.computing.filesharingapp.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PasswordStrength enum
 */
@DisplayName("PasswordStrength Enum Tests")
class PasswordStrengthTest {

    @Test
    @DisplayName("Should have exactly three strength levels")
    void shouldHaveThreeStrengthLevels() {
        PasswordStrength[] values = PasswordStrength.values();
        assertEquals(3, values.length, "PasswordStrength should have exactly 3 values");
    }

    @Test
    @DisplayName("Should contain all expected enum values")
    void shouldContainAllExpectedValues() {
        PasswordStrength[] values = PasswordStrength.values();
        
        assertTrue(Arrays.asList(values).contains(PasswordStrength.WEAK), "Should contain WEAK");
        assertTrue(Arrays.asList(values).contains(PasswordStrength.MEDIUM), "Should contain MEDIUM");
        assertTrue(Arrays.asList(values).contains(PasswordStrength.STRONG), "Should contain STRONG");
    }

    @Test
    @DisplayName("WEAK should have correct display name and level")
    void weakShouldHaveCorrectProperties() {
        assertEquals("Weak", PasswordStrength.WEAK.getDisplayName());
        assertEquals(1, PasswordStrength.WEAK.getLevel());
    }

    @Test
    @DisplayName("MEDIUM should have correct display name and level")
    void mediumShouldHaveCorrectProperties() {
        assertEquals("Medium", PasswordStrength.MEDIUM.getDisplayName());
        assertEquals(2, PasswordStrength.MEDIUM.getLevel());
    }

    @Test
    @DisplayName("STRONG should have correct display name and level")
    void strongShouldHaveCorrectProperties() {
        assertEquals("Strong", PasswordStrength.STRONG.getDisplayName());
        assertEquals(3, PasswordStrength.STRONG.getLevel());
    }

    @ParameterizedTest
    @EnumSource(PasswordStrength.class)
    @DisplayName("All enum values should have non-null display names")
    void allValuesShouldHaveNonNullDisplayNames(PasswordStrength strength) {
        assertNotNull(strength.getDisplayName(), 
            "Display name should not be null for " + strength.name());
        assertFalse(strength.getDisplayName().trim().isEmpty(), 
            "Display name should not be empty for " + strength.name());
    }

    @ParameterizedTest
    @EnumSource(PasswordStrength.class)
    @DisplayName("All enum values should have positive levels")
    void allValuesShouldHavePositiveLevels(PasswordStrength strength) {
        assertTrue(strength.getLevel() > 0, 
            "Level should be positive for " + strength.name());
    }

    @Test
    @DisplayName("Levels should be in ascending order")
    void levelsShouldBeInAscendingOrder() {
        assertTrue(PasswordStrength.WEAK.getLevel() < PasswordStrength.MEDIUM.getLevel(),
            "WEAK level should be less than MEDIUM level");
        assertTrue(PasswordStrength.MEDIUM.getLevel() < PasswordStrength.STRONG.getLevel(),
            "MEDIUM level should be less than STRONG level");
    }

    @Test
    @DisplayName("Should support valueOf with correct enum names")
    void shouldSupportValueOfWithCorrectNames() {
        assertEquals(PasswordStrength.WEAK, PasswordStrength.valueOf("WEAK"));
        assertEquals(PasswordStrength.MEDIUM, PasswordStrength.valueOf("MEDIUM"));
        assertEquals(PasswordStrength.STRONG, PasswordStrength.valueOf("STRONG"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid valueOf")
    void shouldThrowExceptionForInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () -> {
            PasswordStrength.valueOf("INVALID");
        }, "Should throw IllegalArgumentException for invalid enum name");
        
        assertThrows(IllegalArgumentException.class, () -> {
            PasswordStrength.valueOf("weak");
        }, "Should throw IllegalArgumentException for lowercase enum name");
        
        assertThrows(IllegalArgumentException.class, () -> {
            PasswordStrength.valueOf("");
        }, "Should throw IllegalArgumentException for empty string");
    }

    @Test
    @DisplayName("Should throw NullPointerException for null valueOf")
    void shouldThrowNullPointerExceptionForNullValueOf() {
        assertThrows(NullPointerException.class, () -> {
            PasswordStrength.valueOf(null);
        }, "Should throw NullPointerException for null enum name");
    }

    @Test
    @DisplayName("Should maintain consistent toString behavior")
    void shouldMaintainConsistentToStringBehavior() {
        assertEquals("WEAK", PasswordStrength.WEAK.toString());
        assertEquals("MEDIUM", PasswordStrength.MEDIUM.toString());
        assertEquals("STRONG", PasswordStrength.STRONG.toString());
    }

    @Test
    @DisplayName("Should maintain consistent name behavior")
    void shouldMaintainConsistentNameBehavior() {
        assertEquals("WEAK", PasswordStrength.WEAK.name());
        assertEquals("MEDIUM", PasswordStrength.MEDIUM.name());
        assertEquals("STRONG", PasswordStrength.STRONG.name());
    }

    @Test
    @DisplayName("Should maintain consistent ordinal behavior")
    void shouldMaintainConsistentOrdinalBehavior() {
        assertEquals(0, PasswordStrength.WEAK.ordinal());
        assertEquals(1, PasswordStrength.MEDIUM.ordinal());
        assertEquals(2, PasswordStrength.STRONG.ordinal());
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        assertEquals(PasswordStrength.WEAK, PasswordStrength.WEAK);
        assertEquals(PasswordStrength.MEDIUM, PasswordStrength.MEDIUM);
        assertEquals(PasswordStrength.STRONG, PasswordStrength.STRONG);
        
        assertNotEquals(PasswordStrength.WEAK, PasswordStrength.MEDIUM);
        assertNotEquals(PasswordStrength.MEDIUM, PasswordStrength.STRONG);
        assertNotEquals(PasswordStrength.WEAK, PasswordStrength.STRONG);
    }

    @Test
    @DisplayName("Should support comparison operations")
    void shouldSupportComparisonOperations() {
        assertTrue(PasswordStrength.WEAK.compareTo(PasswordStrength.MEDIUM) < 0);
        assertTrue(PasswordStrength.MEDIUM.compareTo(PasswordStrength.STRONG) < 0);
        assertTrue(PasswordStrength.STRONG.compareTo(PasswordStrength.WEAK) > 0);
        assertEquals(0, PasswordStrength.MEDIUM.compareTo(PasswordStrength.MEDIUM));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEAK", "MEDIUM", "STRONG"})
    @DisplayName("Should handle case-sensitive enum name lookups")
    void shouldHandleCaseSensitiveEnumNameLookups(String enumName) {
        assertDoesNotThrow(() -> {
            PasswordStrength strength = PasswordStrength.valueOf(enumName);
            assertNotNull(strength);
        });
    }

    @Test
    @DisplayName("Should have immutable properties")
    void shouldHaveImmutableProperties() {
        // Test that the enum values maintain their properties consistently
        PasswordStrength weak1 = PasswordStrength.WEAK;
        PasswordStrength weak2 = PasswordStrength.valueOf("WEAK");
        
        assertEquals(weak1.getDisplayName(), weak2.getDisplayName());
        assertEquals(weak1.getLevel(), weak2.getLevel());
        assertSame(weak1, weak2); // Enum instances should be the same
    }

    @Test
    @DisplayName("Should work correctly in switch statements")
    void shouldWorkCorrectlyInSwitchStatements() {
        for (PasswordStrength strength : PasswordStrength.values()) {
            String result = switch (strength) {
                case WEAK -> "Low security";
                case MEDIUM -> "Moderate security";
                case STRONG -> "High security";
            };
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    @DisplayName("Should be serializable as enum")
    void shouldBeSerializableAsEnum() {
        // Enums are inherently serializable in Java
        assertTrue(java.io.Serializable.class.isAssignableFrom(PasswordStrength.class));
    }

}