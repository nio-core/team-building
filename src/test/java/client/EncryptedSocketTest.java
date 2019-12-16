package client;

import org.junit.Test;
import org.zeromq.ZMQ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EncryptedSocketTest {

    @Test
    public void t() {
        HyperZMQ hyper = new HyperZMQ("test", "password", true);

        hyper.generateZ85Keypair("server");
        hyper.generateZ85Keypair("client");

        ZMQ.Socket server = hyper.makeServerSocket(ZMQ.PUSH, "server", "tcp://*:7210");
        assertNotNull(server);

        ZMQ.Socket client = hyper.makeClientSocket(ZMQ.PULL, "client", "server", "tcp://127.0.0.1:7210");
        assertNotNull(client);

        server.send("test message");
        String received = client.recvStr();
        System.out.println("Received: " + received);
        assertEquals("test message", received);
    }
}
