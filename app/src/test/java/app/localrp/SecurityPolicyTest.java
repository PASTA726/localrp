package app.localrp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

/**
 * Security-policy regression guard (v0.19.19): the shipped Android policy XML files
 * must keep denying cleartext traffic and platform data extraction (cloud backup
 * and the device-transfer migration channel). If someone removes the manifest
 * wiring or weakens a rule, these tests fail before release. They read the resource
 * files directly; no Android runtime is involved.
 */
public class SecurityPolicyTest {

    private static String readPolicy(String name) {
        // Unit tests run with the module directory as the working directory; the
        // repo-root fallback keeps the test robust when run from the project root.
        File module = new File("src/main/res/xml/" + name);
        File fromRoot = new File("app/src/main/res/xml/" + name);
        File file = module.isFile() ? module : fromRoot;
        assertTrue("policy file missing: " + name, file.isFile());
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError("policy file unreadable: " + name);
        }
    }

    @Test
    public void networkPolicy_deniesCleartext() {
        String config = readPolicy("network_security_config.xml");
        assertTrue("cleartext must be denied in the base config",
                config.contains("cleartextTrafficPermitted=\"false\""));
        assertTrue("base config must cover all traffic (no domain-specific carve-outs)",
                config.contains("<base-config"));
        assertTrue("no per-domain cleartext exceptions allowed",
                !config.contains("cleartextTrafficPermitted=\"true\""));
    }

    @Test
    public void extractionPolicy_deniesCloudBackupAndDeviceTransfer() {
        String rules = readPolicy("data_extraction_rules.xml");
        assertTrue("cloud-backup section must exist", rules.contains("<cloud-backup>"));
        assertTrue("device-transfer section must exist", rules.contains("<device-transfer>"));
        // Every storage domain must be excluded from BOTH extraction surfaces, not
        // just from cloud backup — a rule relaxed in one section must fail the build.
        int cloudStart = rules.indexOf("<cloud-backup>");
        int cloudEnd = rules.indexOf("</cloud-backup>");
        int transferStart = rules.indexOf("<device-transfer>");
        int transferEnd = rules.indexOf("</device-transfer>");
        assertTrue("well-formed sections", cloudStart >= 0 && cloudEnd > cloudStart
                && transferStart >= 0 && transferEnd > transferStart);
        String cloud = rules.substring(cloudStart, cloudEnd);
        String transfer = rules.substring(transferStart, transferEnd);
        for (String domain : new String[]{"root", "file", "database", "sharedpref", "external"}) {
            String exclude = "domain=\"" + domain + "\"";
            assertTrue("cloud backup must exclude " + domain, cloud.contains(exclude));
            assertTrue("device transfer must exclude " + domain, transfer.contains(exclude));
        }
    }
}
