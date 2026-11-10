package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * JVM tests for provider API key crypto (DeepSeek item 14, batch A). The Keystore
 * itself is Android-only; here a synthetic AES key replaces it so the same
 * encrypt/decrypt code path gets regulated. Plaintext is only ever in memory.
 */
public class ApiKeyStoreTest {

    private SecretKey syntheticKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    @Test
    public void encryptDecrypt_roundTripsTheKeyText() throws Exception {
        SecretKey key = syntheticKey();
        String stored = ApiKeyStore.INSTANCE.encryptValue(key, "sk-provider-test-1234");
        assertEquals("sk-provider-test-1234", ApiKeyStore.INSTANCE.decryptValue(key, stored));
    }

    @Test
    public void decrypt_rejectsTamperedAndTruncatedPayloads() throws Exception {
        SecretKey key = syntheticKey();
        String stored = ApiKeyStore.INSTANCE.encryptValue(key, "sk-provider-test-1234");
        char[] characters = stored.toCharArray();
        characters[characters.length / 2] ^= 1;   // flip a bit mid-payload
        assertNull(ApiKeyStore.INSTANCE.decryptValue(key, new String(characters)));
        assertNull(ApiKeyStore.INSTANCE.decryptValue(key, stored.substring(0, 4)));
    }

    @Test
    public void decrypt_neverThrowsOnGarbageOrWrongKey() throws Exception {
        SecretKey key = syntheticKey();
        assertNull(ApiKeyStore.INSTANCE.decryptValue(key, null));
        assertNull(ApiKeyStore.INSTANCE.decryptValue(key, ""));
        assertNull(ApiKeyStore.INSTANCE.decryptValue(key, "not base64 at all"));
        SecretKey otherKey = syntheticKey();
        String stored = ApiKeyStore.INSTANCE.encryptValue(otherKey, "secret");
        assertNull(ApiKeyStore.INSTANCE.decryptValue(key, stored));
    }
}
