package client;

import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.Assert.*;

/**
 * -Djava.util.logging.SimpleFormatter.format="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n"
 */
public class CryptoTest {

    @Test
    public void testSaveLoadKeystore() throws GeneralSecurityException {
        Crypto c1 = new Crypto(null, "password".toCharArray(), true);
        String signerKey1 = c1.getSigner().getPublicKey().hex();
        c1.createGroup("test");
        String msg = "testmessage";
        String enc = c1.encrypt(msg, "test");
        String dec = c1.decrypt(enc, "test");

        assertEquals(msg, dec);
        c1 = null;
        // Load the keystore of c1 and do the same again
        Crypto c2 = new Crypto(null, "password".toCharArray(), false);
        String signerKey2 = c2.getSigner().getPublicKey().hex();
        assertTrue(c2.getGroupNames().contains("test"));
        assertNotNull(c2.getKeyForGroup("test"));

        enc = c2.encrypt(msg, "test");
        dec = c2.decrypt(enc, "test");
        assertEquals(msg, dec);
        // The signers public key is loaded correctly from c2 // TODO
        assertEquals(signerKey1, signerKey2);
    }

    @Test
    public void testImportExportGroup() throws GeneralSecurityException {
        Crypto c1 = new Crypto(null, "jsa".toCharArray(), true);
        Crypto c2 = new Crypto(null, "jsa".toCharArray(), true);

        c1.createGroup("test");
        c2.addGroup("test", c1.getKeyForGroup("test"));
        String msg = "testmessage";
        String enc = c1.encrypt(msg, "test");
        System.out.println("Encrypted: " + enc);
        String dec = c2.decrypt(enc, "test");

        assertEquals(msg, dec);
    }

    @Test
    public void testEncryptionDecryption() throws GeneralSecurityException, IOException {
        Crypto c1 = new Crypto(null, "jsa".toCharArray(), true);
        c1.createGroup("test");
        String msg = "testmessage";
        String enc = c1.encrypt(msg, "test");
        //System.out.println("Encrypted: " + enc);
        String dec = c1.decrypt(enc, "test");

        assertEquals(msg, dec);
    }


}
