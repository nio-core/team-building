package client;

import org.junit.Test;
import txprocessor.CSVStringsTP;

import static org.junit.Assert.assertEquals;

/**
 * -Djava.util.logging.SimpleFormatter.format="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n"
 */
public class TextMessagesTest {

    private final static String TESTGROUP = "testGroup";

    @Test
    public void testReadWriteToChain() {
        CSVStringsTP.main(null);
        sleep(1000);

        HyperZMQ client1 = new HyperZMQ("testID1", "password", true);
        HyperZMQ client2 = new HyperZMQ("testID2", "drowssap", true);
        HyperZMQ failingClient = new HyperZMQ("testID2", "testtset", true);

        client1.createGroup(TESTGROUP, (group, message, sender) -> {
            System.out.println("[Client 1] received: " + group + " " + message + " by " + sender);

            assertEquals("testGroup", group);
            assertEquals("testMessage", message);
            assertEquals("testID1", sender);
        });

        String key = client1.getKeyForGroup(TESTGROUP);
        client2.addGroup(TESTGROUP, key);
        // The client receives its own messages because it subscribed to the group
        client1.addCallbackToGroup(TESTGROUP, ((group, message, sender) -> {
            System.out.println("[Client 1] received: " + group + " " + message + " by " + sender);

            assertEquals("testGroup", group);
            assertEquals("testMessage", message);
            assertEquals("testID1", sender);
        }));
        // The other client receives the (encrypted) messages too because it has the key for the group
        // and subscribed to the group
        client2.addCallbackToGroup(TESTGROUP, (group, message, sender) -> {
            System.out.println("[Client 2] received: " + group + " " + message + " by " + sender);

            assertEquals("testMessage", message);
            assertEquals("testGroup", group);
            assertEquals("testID1", sender);
        });

        failingClient.addCallbackToGroup(TESTGROUP, ((group, message, sender) -> {
            System.out.println("[Client 3 (BAD)] received: " + group + " " + message + " by " + sender);
        }));

        client1.sendTextToChain(TESTGROUP, "testMessage");

        sleep(3000);
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}