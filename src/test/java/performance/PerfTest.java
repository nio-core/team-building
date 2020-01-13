package performance;

import org.junit.Before;
import org.junit.Test;
import txprocessor.CSVStringsTP;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class PerfTest {
    // ----------------------- CONFIGURATION -----------------------
    private static final int TEST_REPITIONS = 10;
    private static final int TRANSACTION_PROCESSOR_COUNT = 1;
    static final int MAX_RECEIVE_COUNT = 1000;
    static final int MESSAGE_SIZE_IN_BYTE = 30000;
    static final int MESSAGE_LIST_SIZE = 100;
    static final int SEND_MESSAGE_COUNT = 1000;
    static final boolean USE_BATCH_MESSAGE = true;

    static final int BACKPRESSURE_TIMEOUT_MS = 10;
    static final int BACKPRESSURE_TIMEOUT_NS = 0;
    // -------------------------------------------------------------

    private Send sendTask;
    private Receive receiveTask;

    @Before
    public void setup() {
        for (int i = 0; i < TRANSACTION_PROCESSOR_COUNT; i++) {
            Thread t = new Thread(() -> CSVStringsTP.main(null));
            t.start();
        }
    }

    @Test
    public void test() {
        List<Long> receiveTimes = new ArrayList<>();
        List<Long> sendTimes = new ArrayList<>();

        for (int i = 0; i < TEST_REPITIONS; i++) {
            Thread receiveThread = new Thread(() -> {
                receiveTask = new Receive();
                //receiveTask.setup();
                receiveTask.receive();
            });

            Thread sendThread = new Thread(() -> {
                sendTask = new Send();
                sendTask.setup();
                sendTask.singleComputerTest();
            });

            receiveThread.start();
            sleep(100);
            sendThread.start();
            try {
                receiveThread.join();
                sendThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            receiveTimes.add(receiveTask._elapsedTime);
            sendTimes.add(sendTask.elapsedTime);
            sleep(5000);
        }
        DecimalFormat df = new DecimalFormat("####.###");
        System.out.println("\nReceive List [ms]: " + receiveTimes);
        Double recvAvg = receiveTimes.stream().mapToLong(Long::longValue).average().getAsDouble();
        Double recvMsgSec = SEND_MESSAGE_COUNT / (recvAvg / 1000);
        Double recvMbSec = ((recvMsgSec * MESSAGE_SIZE_IN_BYTE) / 1024) / 1024;
        System.out.println("Min=" + receiveTimes.stream().min(Long::compare).get()
                + "ms Max=" + receiveTimes.stream().max(Long::compare).get()
                + "ms Avg=" + recvAvg
                + "ms\n==> " + df.format(recvMsgSec) + " msg/sec received"
                + "==> " + df.format(recvMbSec) + "mb/s");
        //System.out.println("--------------------------------------------------------");
        System.out.println();
        System.out.println("Send List: " + sendTimes);
        Double sendAvg = sendTimes.stream().mapToLong(Long::longValue).average().getAsDouble();
        Double sendMsgSec = SEND_MESSAGE_COUNT / (sendAvg / 1000);
        Double sendMbSec = ((sendMsgSec * MESSAGE_SIZE_IN_BYTE) / 1024) / 1024;
        System.out.println("Min=" + sendTimes.stream().min(Long::compare).get()
                + "ms Max=" + sendTimes.stream().max(Long::compare).get()
                + "ms Avg=" + sendAvg +
                "ms\n==> " + df.format(sendMsgSec) + " msg/sec send"
                + "==> " + df.format(sendMbSec) + "mb/s");
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
