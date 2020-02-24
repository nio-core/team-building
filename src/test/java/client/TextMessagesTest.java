package client;

import org.junit.Test;
import txprocessor.CSVStringsTP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * -Djava.util.logging.SimpleFormatter.format="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n"
 * <p>
 * <p>
 * THESE TESTS ASSUME THAT THE SAWTOOTH NETWORK IS RUNNING (CONSENSUS ENGINE + VALIDATOR)
 */
public class TextMessagesTest {

    private final static String TESTGROUP = "testGroup";

    /*@Test
    public void testDelayedGroupJoin() {
        CSVStringsTP.main(null);
        sleep(1000);

        HyperZMQ client1 = new HyperZMQ("testID1", "password", true);
        HyperZMQ client2 = new HyperZMQ("testID2", "drowssap", true);

        client1.createGroup(TESTGROUP);

        client2.addGroup(TESTGROUP, client1.getKeyForGroup(TESTGROUP), ((group, message, senderID) -> {
            System.out.println("[Client 2] received: " + group + ", " + message + ", by " + senderID);
        }));
        client1.sendTextToChain(TESTGROUP, "testmessage");
        sleep(5000);

        sleep(3000);
    } */

    @Test
    public void testMultiGroup() {
        CSVStringsTP.main(null);
        sleep(1000);
        HyperZMQ client1 = new HyperZMQ("Client1", "password", true);
        HyperZMQ client2 = new HyperZMQ("Client2", "client2.jks", "drowssap", null, true);
        HyperZMQ client3 = new HyperZMQ("Client3", "client3.jks", "drowssap", null, true);
        AtomicBoolean send12 = new AtomicBoolean(false);
        AtomicBoolean send13 = new AtomicBoolean(false);
        client1.createGroup("group12");
        client1.createGroup("group13");
        client2.addGroup("group12", client1.getKeyForGroup("group12"), (group, message, senderID) -> {
            System.out.println("[Client2] received: group=" + group + ", message=" + message + ", sender=" + senderID);

            assertEquals("group12", group);
            assertEquals("test from 1 to 2", message);
            assertEquals("Client1", senderID);
            send12.set(true);
        });

        client3.addGroup("group13", client1.getKeyForGroup("group13"), (group, message, senderID) -> {
            System.out.println("[Client3] received: group=" + group + ", message=" + message + ", sender=" + senderID);

            assertEquals("group13", group);
            assertEquals("test from 1 to 3", message);
            assertEquals("Client1", senderID);
            send13.set(true);
        });

        client1.sendTextToChain("group12", "test from 1 to 2");
        client1.sendTextToChain("group13", "test from 1 to 3");
        sleep(3000);
        assertTrue(send12.get());
        assertTrue(send13.get());
    }

    @Test
    public void testReadWriteToChain() {
        CSVStringsTP.main(null);
        sleep(1000);

        HyperZMQ client1 = new HyperZMQ("Client1", "password", true);
        HyperZMQ client2 = new HyperZMQ("Client2", "drowssap", true);
        HyperZMQ failingClient = new HyperZMQ("ClientFail", "testtset", true);

        client1.createGroup(TESTGROUP);
        AtomicBoolean c1received = new AtomicBoolean(false);
        AtomicBoolean c2received = new AtomicBoolean(false);
        String key = client1.getKeyForGroup(TESTGROUP);
        client2.addGroup(TESTGROUP, key);

        // The client receives its own messages because it subscribed to the group
        client1.addCallbackToGroup(TESTGROUP, ((group, message, sender) -> {
            System.out.println("[Client1] received: group=" + group + ", message=" + message + ", sender=" + sender);

            assertEquals("testGroup", group);
            assertEquals("testMessage", message);
            assertEquals("Client1", sender);
            c1received.set(true);
        }));

        // The other client receives the (encrypted) messages too, because it has the key for the group
        // and subscribed to the group
        client2.addCallbackToGroup(TESTGROUP, (group, message, sender) -> {
            System.out.println("[Client2] received: group=" + group + ", message=" + message + ", sender=" + sender);

            assertEquals("testMessage", message);
            assertEquals("testGroup", group);
            assertEquals("Client1", sender);
            c2received.set(true);
        });

        failingClient.addCallbackToGroup(TESTGROUP, ((group, message, sender) -> {
            fail("Should not receive anything!");
        }));

        client1.sendTextToChain(TESTGROUP, "testMessage");

        sleep(3000);

        assertTrue(c1received.get());
        assertTrue(c2received.get());
    }

    private int messageCount;

    @Test
    public void testMultiMessage() {
        CSVStringsTP.main(null);
        sleep(1000);
        HyperZMQ client1 = new HyperZMQ("Client1", "password", true);
        HyperZMQ client2 = new HyperZMQ("Client2", "client2.jks", "drowssap", null, true);
        client1.createGroup(TESTGROUP);
        client2.addGroup(TESTGROUP, client1.getKeyForGroup(TESTGROUP));

        messageCount = 0;
        client2.addCallbackToGroup(TESTGROUP, (group, message, senderID) -> {
            messageCount++;
        });

        List<String> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            list.add("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        }
        client1.sendTextsToChain(TESTGROUP, list);
        sleep(4000);
        assertEquals(50, messageCount);
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}