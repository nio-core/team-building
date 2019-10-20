package client;

import org.junit.Test;
import txprocessor.CSVStringsTP;

import static org.junit.Assert.assertEquals;

/**
 * -Djava.util.logging.SimpleFormatter.format="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n"
 */
public class HyperZMQTest {

    final static String TESTGROUP = "testGroup";

    @Test
    public void testReadWriteToChain() {
        CSVStringsTP.main(null);
        sleep(1000);

        HyperZMQ hzmq1 = new HyperZMQ("testID1", "password", true);
        HyperZMQ hzmq2 = new HyperZMQ("testID2", "drowssap", true);
        HyperZMQ hzmqFailure = new HyperZMQ("testID2", "testtset", true);

        /*hzmq1.createGroup(TESTGROUP, (group, message) -> {
            System.out.println("[Client 1] received: " + group + " " + message);

            assertEquals("testGroup", group);
            assertEquals("testMessage", message);
        });

        String key = hzmq1.getKeyForGroup(TESTGROUP);
        hzmq2.addGroup(TESTGROUP, key);
        // The client receives its own messages because it subscribed to the group
        *//* hzmq1.subscribe(TESTGROUP, ((group, message) -> {
            System.out.println("[Client 1] received: " + group + " " + message);

            assertEquals("testGroup", group);
            assertEquals("testMessage", message);
        })); *//*
        // The other client receives the (encrypted) messages too because it has the key for the group
        // and subscribed to the group
        hzmq2.addCallbackToGroup(TESTGROUP, (group, message) -> {
            System.out.println("[Client 2] received: " + group + " " + message);

            assertEquals("testMessage", message);
            assertEquals("testGroup", group);
        });

        hzmqFailure.addCallbackToGroup(TESTGROUP, ((group, message) -> {
            System.out.println("[Client 3 (BAD)] received: " + group + " " + message);
        }));

        hzmq1.sendMessageToChain(TESTGROUP, "testMessage");
*/
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