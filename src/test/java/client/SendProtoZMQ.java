package client;

import org.junit.Test;

public class SendProtoZMQ {

    @Test
    public void test() {
        String message = "test string";
        String group = "testgroup";
        HyperZMQ client = new HyperZMQ("test", "password", true);
        client.createGroup(group);
        client.sendTextToChain(group, message);
    }
}
