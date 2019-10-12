import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class CryptoTest {

    @Test
    public void completeTest() throws GeneralSecurityException, IOException {
        Crypto c1 = new Crypto(null, "jsa".toCharArray(), true);
        Crypto c2 = new Crypto(null, "jsa".toCharArray(), true);

        c1.createGroup("test");
        c2.addGroup("test", c1.getKeyForGroup("test"));
        String msg = "testmessage";
        String enc = c1.encrypt(msg, "test");
        System.out.println("Encrypted: " + enc);
        String dec = c2.decrypt(enc, "test");

        Assert.assertEquals(msg, dec);
    }

    @Test
    public void testEncryptionDecryption() throws GeneralSecurityException, IOException {
        Crypto c1 = new Crypto(null, "jsa".toCharArray(), true);
        c1.createGroup("test");
        String msg = "testmessage";
        String enc = c1.encrypt(msg, "test");
        //System.out.println("Encrypted: " + enc);
        String dec = c1.decrypt(enc, "test");

        Assert.assertEquals(msg, dec);
    }


}
