package performance;

import client.HyperZMQ;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static performance.PerfTest.*;

public class Send {
    private long _sendCount;
    private String _message;
    private List<String> _messageList = new ArrayList<>();
    private static final String GROUP_KEY = "vMz4A0sTpRY7D7Sxe/v41LgKuCh4PKpTKHovo1oly9s=";

    public long elapsedTime;

    @Before
    public void setup() {
        _sendCount = 0;
        // Build 1kb text, single message and list
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PerfTest.MESSAGE_SIZE_IN_BYTE; i++) {
            sb.append("A");
        }
        _message = sb.toString();
        for (int i = 0; i < PerfTest.MESSAGE_LIST_SIZE; i++) {
            _messageList.add(_message);
        }
    }

    @Test
    public void singleComputerTest() {
        System.out.println("Starting sender in thread " + Thread.currentThread().getId());

        HyperZMQ sendClient = new HyperZMQ("sendClient", "password", true);
        sendClient.addGroup("testgroup", GROUP_KEY);

        long startTime = System.currentTimeMillis();

        while (_sendCount < SEND_MESSAGE_COUNT) {
            if (!USE_BATCH_MESSAGE) {
                if (sendClient.sendTextToChain("testgroup", _message)) {
                    _sendCount++;
                } else {
                    sleep(BACKPRESSURE_TIMEOUT_MS, BACKPRESSURE_TIMEOUT_NS);
                }
            } else {
                if (sendClient.sendTextsToChain("testgroup", _messageList)) {
                    _sendCount += MESSAGE_LIST_SIZE;
                } else {
                    sleep(BACKPRESSURE_TIMEOUT_MS, BACKPRESSURE_TIMEOUT_NS);
                }
            }
        }
        long endTime = System.currentTimeMillis();
        elapsedTime = endTime - startTime;
        sendClient.close();
        System.out.println("----- end sending -----");
        System.out.println("Needed " + elapsedTime + "ms to send " + SEND_MESSAGE_COUNT + " messages");
    }

    private void sleep(int ms) {
        sleep(ms, 0);
    }

    private void sleep(int ms, int ns) {
        try {
            Thread.sleep(ms, ns);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
