package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the SharePermission enum.
 * 
 * <p>Tests verify that the enum values are correctly defined and accessible,
 * and that the enum behaves as expected for file sharing permission levels.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class SharePermissionTest {

    @Test
    void testEnumValues() {
        SharePermission[] permissions = SharePermission.values();
        
        assertEquals(2, permissions.length, "SharePermission should have exactly 2 values");
        assertEquals(SharePermission.VIEW_ONLY, permissions[0], "First enum value should be VIEW_ONLY");
        assertEquals(SharePermission.DOWNLOAD, permissions[1], "Second enum value should be DOWNLOAD");
    }

    @Test
    void testViewOnlyPermission() {
        SharePermission permission = SharePermission.VIEW_ONLY;
        
        assertNotNull(permission, "VIEW_ONLY permission should not be null");
        assertEquals("VIEW_ONLY", permission.name(), "VIEW_ONLY name should match");
        assertEquals(0, permission.ordinal(), "VIEW_ONLY should have ordinal 0");
    }

    @Test
    void testDownloadPermission() {
        SharePermission permission = SharePermission.DOWNLOAD;
        
        assertNotNull(permission, "DOWNLOAD permission should not be null");
        assertEquals("DOWNLOAD", permission.name(), "DOWNLOAD name should match");
        assertEquals(1, permission.ordinal(), "DOWNLOAD should have ordinal 1");
    }

    @Test
    void testValueOf() {
        assertEquals(SharePermission.VIEW_ONLY, SharePermission.valueOf("VIEW_ONLY"), 
                "valueOf should return VIEW_ONLY for 'VIEW_ONLY' string");
        assertEquals(SharePermission.DOWNLOAD, SharePermission.valueOf("DOWNLOAD"), 
                "valueOf should return DOWNLOAD for 'DOWNLOAD' string");
    }

    @Test
    void testValueOfInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            SharePermission.valueOf("INVALID_PERMISSION");
        }, "valueOf should throw IllegalArgumentException for invalid permission name");
    }

    @Test
    void testValueOfNullValue() {
        assertThrows(NullPointerException.class, () -> {
            SharePermission.valueOf(null);
        }, "valueOf should throw NullPointerException for null input");
    }

    @Test
    void testEnumEquality() {
        SharePermission viewOnly1 = SharePermission.VIEW_ONLY;
        SharePermission viewOnly2 = SharePermission.valueOf("VIEW_ONLY");
        SharePermission download1 = SharePermission.DOWNLOAD;
        SharePermission download2 = SharePermission.valueOf("DOWNLOAD");
        
        assertEquals(viewOnly1, viewOnly2, "VIEW_ONLY instances should be equal");
        assertEquals(download1, download2, "DOWNLOAD instances should be equal");
        assertNotEquals(viewOnly1, download1, "VIEW_ONLY and DOWNLOAD should not be equal");
    }

    @Test
    void testEnumHashCode() {
        SharePermission viewOnly1 = SharePermission.VIEW_ONLY;
        SharePermission viewOnly2 = SharePermission.valueOf("VIEW_ONLY");
        SharePermission download = SharePermission.DOWNLOAD;
        
        assertEquals(viewOnly1.hashCode(), viewOnly2.hashCode(), 
                "Equal VIEW_ONLY instances should have same hash code");
        assertNotEquals(viewOnly1.hashCode(), download.hashCode(), 
                "Different enum values should have different hash codes");
    }

    @Test
    void testEnumToString() {
        assertEquals("VIEW_ONLY", SharePermission.VIEW_ONLY.toString(), 
                "VIEW_ONLY toString should return 'VIEW_ONLY'");
        assertEquals("DOWNLOAD", SharePermission.DOWNLOAD.toString(), 
                "DOWNLOAD toString should return 'DOWNLOAD'");
    }

    @Test
    void testEnumInSwitchStatement() {
        // Test that enum can be used in switch statements
        for (SharePermission permission : SharePermission.values()) {
            String result = switch (permission) {
                case VIEW_ONLY -> "view";
                case DOWNLOAD -> "download";
            };
            
            assertNotNull(result, "Switch statement should handle all enum values");
            assertTrue(result.equals("view") || result.equals("download"), 
                    "Switch result should be valid");
        }
    }

    @Test
    void testEnumComparison() {
        SharePermission viewOnly = SharePermission.VIEW_ONLY;
        SharePermission download = SharePermission.DOWNLOAD;
        
        assertTrue(viewOnly.compareTo(download) < 0, 
                "VIEW_ONLY should come before DOWNLOAD in enum order");
        assertTrue(download.compareTo(viewOnly) > 0, 
                "DOWNLOAD should come after VIEW_ONLY in enum order");
        assertEquals(0, viewOnly.compareTo(SharePermission.VIEW_ONLY), 
                "Same enum values should have compareTo result of 0");
    }

    @Test
    void testEnumSerialization() {
        // Test that enum values maintain their identity across serialization scenarios
        SharePermission original = SharePermission.DOWNLOAD;
        SharePermission fromValueOf = SharePermission.valueOf(original.name());
        
        assertSame(original, fromValueOf, 
                "Enum instances should be the same object (singleton pattern)");
    }
}