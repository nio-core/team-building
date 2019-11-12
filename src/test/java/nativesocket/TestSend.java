package nativesocket;

import com.nativelibs4java.bridj.szmq.Socket;
import org.bridj.Pointer;
import org.junit.Assert;
import org.junit.Test;

import static com.nativelibs4java.bridj.szmq.SzmqLibrary.ZMQ_PUB;
import static org.bridj.Pointer.pointerToCString;

public class TestSend {

    public static final String TESTMESSAGE = "Hallo";
    static final String SERVER_BIND_ENDPOINT = "tcp://*:9000";

    @Test
    public void testSend() {
        System.out.println("Send Test");
        Socket socket = new Socket(ZMQ_PUB);

        socket.curveServer(pointerToCString(TestReceive.SERVER_PRIVATE));

        socket.bindEndpoint(pointerToCString(SERVER_BIND_ENDPOINT));

        Pointer<Byte> msg = pointerToCString(TESTMESSAGE);
        int sent = socket.sendMessage(msg);
        //System.out.println("sent length:" + sent);
        Assert.assertEquals(TESTMESSAGE.length(), sent);
    }
}
