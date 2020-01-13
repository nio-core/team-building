package performance;

import client.HyperZMQ;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class Receive {
    private static final String GROUP_KEY = "vMz4A0sTpRY7D7Sxe/v41LgKuCh4PKpTKHovo1oly9s=";
    public long _receiveCount = 0, _startTime = 0, _endTime, _elapsedTime;
    public AtomicBoolean _run = new AtomicBoolean(true);
    //------------------------------------------------------------------------------------------------------------------

    @Test
    public void receive() {
        System.out.println("Starting receiver in thread " + Thread.currentThread().getId());

        HyperZMQ recvClient = new HyperZMQ("recvClient", "keystore2.jks", "password", null, true);
        recvClient.addGroup("testgroup", GROUP_KEY);
        recvClient.addCallbackToGroup("testgroup", (group, message, senderID) -> {
            if (_startTime == 0) {
                _startTime = System.currentTimeMillis();
            }

            _receiveCount++;
            if ((_receiveCount % 100) == 0) {
                System.out.println(_receiveCount + " [" + Thread.currentThread().getId() + "]");
            }

            if (_receiveCount == PerfTest.MAX_RECEIVE_COUNT) {
                _endTime = System.currentTimeMillis();
                _elapsedTime = _endTime - _startTime;
                System.out.println();
                System.out.println("Needed " + _elapsedTime + "ms to receive " + PerfTest.MAX_RECEIVE_COUNT + " messages.");
                _run.set(false);
            }
        });

        while (_run.get()) {
        }

        recvClient.close();
        System.out.println("----- Receiver exiting -----");
    }

/*
    @Test
    public void receive() {
        _run.set(true);
        Thread t = new Thread(() -> {
            while (_run.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Exiting keep-alive thread " + Thread.currentThread().getId());
        });
        t.start();
        System.out.println("Starting receiver in thread " + Thread.currentThread().getId());

        HyperZMQ recvClient = new HyperZMQ("recvClient", "keystore2.jks", "password", true);
        recvClient.addGroup("testgroup", GROUP_KEY);
        recvClient.addCallbackToGroup("testgroup", (group, message, senderID) -> {
            if (_startTime == 0) {
                _startTime = System.currentTimeMillis();
            }

            _receiveCount++;
            if ((_receiveCount % 100) == 0) {
                System.out.println(_receiveCount + " [" + Thread.currentThread().getId() + "]");
            }

            if (_receiveCount == MAX_RECEIVE_COUNT) {
                _endTime = System.currentTimeMillis();
                _elapsedTime = _endTime - _startTime;
                System.out.println();
                System.out.println("Needed " + _elapsedTime + "ms to receive " + MAX_RECEIVE_COUNT + " messages.");
                _run.set(false);
            }
        });

        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("----- Receiver exiting -----");
    }*/
}
