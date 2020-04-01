package client;

import org.junit.Assert;
import org.junit.Test;
import sawtooth.sdk.signing.Secp256k1Context;

public class SawtoothUtilTest {

    @Test
    public void testAllTheThings() {
        String hex = new Secp256k1Context().newRandomPrivateKey().hex();

        byte[] data = SawtoothUtils.hexDecode(hex);
        String encoded = SawtoothUtils.hexEncode(data);

        Assert.assertEquals(hex, encoded);
    }
}
