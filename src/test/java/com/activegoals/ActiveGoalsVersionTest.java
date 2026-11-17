package com.activegoals;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import net.runelite.client.config.ConfigSection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveGoalsVersionTest
{
    @Test
    void pluginMetadataAndFirstSettingsSectionUseTheSameReleaseVersion() throws Exception
    {
        Properties hubMetadata = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("runelite-plugin.properties")))
        {
            hubMetadata.load(input);
        }
        Properties packagedMetadata = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/runelite-plugin.properties"))
        {
            assertNotNull(input, "Plugin metadata must be packaged as a resource");
            packagedMetadata.load(input);
        }

        String version = hubMetadata.getProperty("version");
        assertNotNull(version, "Plugin Hub metadata must declare a version");
        assertTrue(version.matches("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"),
            "Release version must use MAJOR.MINOR.PATCH");
        assertEquals(version, packagedMetadata.getProperty("version"));

        ConfigSection versionSection = ActiveGoalsConfig.class.getField("VERSION_SECTION")
            .getAnnotation(ConfigSection.class);
        assertNotNull(versionSection);
        assertEquals("Version " + version, versionSection.name());
        for (Field field : ActiveGoalsConfig.class.getFields())
        {
            ConfigSection other = field.getAnnotation(ConfigSection.class);
            if (other != null && !field.getName().equals("VERSION_SECTION"))
                assertTrue(versionSection.position() < other.position(),
                    "Version section must appear before " + field.getName());
        }
    }
}
