package keyexchangereceipt;

import client.HyperZMQ;
import keyexchange.KeyExchangeReceipt;
import keyexchange.ReceiptType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sawtooth.sdk.signing.Secp256k1Context;
import txprocessor.KeyExReceiptTP;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KeyExReceiptTest {

    @Test
    public void testSubmit() throws InterruptedException {
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

        Thread.sleep(2000);

        // Read the members
        List<String> members = memberClient.getGroupMembers("testgroup");
        System.out.println("Members: " + members.toString());
        assertTrue(members.contains(memberClient.getSawtoothPublicKey()));
        assertTrue(members.contains(joiningClient.getSawtoothPublicKey()));

        // Read the receipt
        KeyExchangeReceipt read = memberClient.getKeyExchangeReceipt(memberClient.getSawtoothPublicKey(),
                joiningClient.getSawtoothPublicKey(),
                "testgroup");

        assertNotNull(read);
    }
}
