package keyexchangereceipt;

import client.HyperZMQ;
import keyexchange.KeyExchangeReceipt;
import keyexchange.ReceiptType;
import org.junit.Test;
import sawtooth.sdk.signing.Secp256k1Context;
import txprocessor.KeyExReceiptTP;

import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KeyExReceiptTest {

    @Test
    public void testSubmit() throws InterruptedException {
        // KeyExReceiptTP.main(null);
        //Thread.sleep(1000);

        HyperZMQ memberClient = new HyperZMQ("testclient", "password", true);
        memberClient.createGroup("testgroup");

        HyperZMQ joiningClient = new HyperZMQ("joinClient", "drowssap", true);

        KeyExchangeReceipt receipt = new KeyExchangeReceipt(
                memberClient.getSawtoothPublicKey(),
                joiningClient.getSawtoothPublicKey(),
                ReceiptType.JOIN_GROUP,
                "testgroup",
                System.currentTimeMillis());

        receipt.setSignature(memberClient.sign(receipt.getSignablePayload()));

        memberClient.sendKeyExchangeReceipt(receipt);
        memberClient.getGroupMembers("testgroup");

        Thread.sleep(5000);
    }
}
