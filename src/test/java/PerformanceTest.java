import client.HyperZMQ;
import org.junit.Test;
import txprocessor.CSVStringsTP;

public class PerformanceTest {

    boolean _runReceiver = false, _runSender = false;
    long _receiveCount, _sendCount;
    String _message;
    final static int MESSAGE_SIZE_IN_BYTE = 1000;

    @Test
    public void singleComputerTest() {
        CSVStringsTP.main(null);
        sleep(100);
        HyperZMQ sendClient = new HyperZMQ("sendClient", "password", true);
        HyperZMQ recvClient = new HyperZMQ("recvClient", "keystore2.jks", "password", true);
        sendClient.createGroup("testgroup");
        recvClient.addGroup("testgroup", sendClient.getKeyForGroup("testgroup"));
        // Build 1kb text
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MESSAGE_SIZE_IN_BYTE; i++) {
            sb.append("A");
        }
        _message = sb.toString();
        _runSender = true;

        Thread receiveThread = new Thread(() -> {
            receiveForXSeconds(recvClient);

        });
        Thread sendThread = new Thread(() -> {
            sendForXSeconds(sendClient);
        });
        System.out.println("Starting threads...");
        receiveThread.start();
        sendThread.start();
        sleep(60000);
        _runSender = false;
        recvClient.removeGroup("testgroup");
        System.out.println("Shutting down...");
        System.out.println("Sent messages: " + _sendCount);
        System.out.println("Received cout:" + _receiveCount);
    }

    private void sendForXSeconds(HyperZMQ client) {
        System.out.println("Starting sender in thread " + Thread.currentThread().getId());
        //long sendCount = 0;
        while (_runSender) {
            client.sendTextToChain("testgroup", _message);
            _sendCount++;
        }
        //System.out.println("Send count: " + sendCount);
    }

    private void receiveForXSeconds(HyperZMQ client) {
        System.out.println("Starting receiver in thread " + Thread.currentThread().getId());
        client.addCallbackToGroup("testgroup", (group, message, senderID) -> {
            _receiveCount++;
        });
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
