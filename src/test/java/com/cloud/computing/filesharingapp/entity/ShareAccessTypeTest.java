package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ShareAccessType enum.
 * 
 * <p>Tests verify that the enum values are correctly defined and accessible,
 * and that the enum behaves as expected for tracking file access types.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class ShareAccessTypeTest {

    @Test
    void testEnumValues() {
        ShareAccessType[] accessTypes = ShareAccessType.values();
        
        assertEquals(2, accessTypes.length, "ShareAccessType should have exactly 2 values");
        assertEquals(ShareAccessType.VIEW, accessTypes[0], "First enum value should be VIEW");
        assertEquals(ShareAccessType.DOWNLOAD, accessTypes[1], "Second enum value should be DOWNLOAD");
    }

    @Test
    void testViewAccessType() {
        ShareAccessType accessType = ShareAccessType.VIEW;
        
        assertNotNull(accessType, "VIEW access type should not be null");
        assertEquals("VIEW", accessType.name(), "VIEW name should match");
        assertEquals(0, accessType.ordinal(), "VIEW should have ordinal 0");
    }

    @Test
    void testDownloadAccessType() {
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;
        
        assertNotNull(accessType, "DOWNLOAD access type should not be null");
        assertEquals("DOWNLOAD", accessType.name(), "DOWNLOAD name should match");
        assertEquals(1, accessType.ordinal(), "DOWNLOAD should have ordinal 1");
    }

    @Test
    void testValueOf() {
        assertEquals(ShareAccessType.VIEW, ShareAccessType.valueOf("VIEW"), 
                "valueOf should return VIEW for 'VIEW' string");
        assertEquals(ShareAccessType.DOWNLOAD, ShareAccessType.valueOf("DOWNLOAD"), 
                "valueOf should return DOWNLOAD for 'DOWNLOAD' string");
    }

    @Test
    void testValueOfInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            ShareAccessType.valueOf("INVALID_ACCESS_TYPE");
        }, "valueOf should throw IllegalArgumentException for invalid access type name");
    }

    @Test
    void testValueOfNullValue() {
        assertThrows(NullPointerException.class, () -> {
            ShareAccessType.valueOf(null);
        }, "valueOf should throw NullPointerException for null input");
    }

    @Test
    void testEnumEquality() {
        ShareAccessType view1 = ShareAccessType.VIEW;
        ShareAccessType view2 = ShareAccessType.valueOf("VIEW");
        ShareAccessType download1 = ShareAccessType.DOWNLOAD;
        ShareAccessType download2 = ShareAccessType.valueOf("DOWNLOAD");
        
        assertEquals(view1, view2, "VIEW instances should be equal");
        assertEquals(download1, download2, "DOWNLOAD instances should be equal");
        assertNotEquals(view1, download1, "VIEW and DOWNLOAD should not be equal");
    }

    @Test
    void testEnumInSwitchStatement() {
        // Test that enum can be used in switch statements
        for (ShareAccessType accessType : ShareAccessType.values()) {
            String result = switch (accessType) {
                case VIEW -> "viewed";
                case DOWNLOAD -> "downloaded";
            };
            
            assertNotNull(result, "Switch statement should handle all enum values");
            assertTrue(result.equals("viewed") || result.equals("downloaded"), 
                    "Switch result should be valid");
        }
    }

    @Test
    void testEnumComparison() {
        ShareAccessType view = ShareAccessType.VIEW;
        ShareAccessType download = ShareAccessType.DOWNLOAD;
        
        assertTrue(view.compareTo(download) < 0, 
                "VIEW should come before DOWNLOAD in enum order");
        assertTrue(download.compareTo(view) > 0, 
                "DOWNLOAD should come after VIEW in enum order");
        assertEquals(0, view.compareTo(ShareAccessType.VIEW), 
                "Same enum values should have compareTo result of 0");
    }

    @Test
    void testEnumSerialization() {
        // Test that enum values maintain their identity
        ShareAccessType original = ShareAccessType.DOWNLOAD;
        ShareAccessType fromValueOf = ShareAccessType.valueOf(original.name());
        
        assertSame(original, fromValueOf, 
                "Enum instances should be the same object (singleton pattern)");
    }
}