package client;

import org.junit.Test;

public class SendProtoZMQ {

    @Test
    public void test() {
        HyperZMQ client = new HyperZMQ("test", "password", true);
        client.createGroup("testgroup");
        client.sendTextToChain("testgroup", "test string");
    }
}
