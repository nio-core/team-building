package nativesocket;

import com.nativelibs4java.bridj.szmq.Socket;
import org.bridj.Pointer;
import org.junit.Assert;
import org.junit.Test;

import static com.nativelibs4java.bridj.szmq.SzmqLibrary.ZMQ_SUB;
import static com.nativelibs4java.bridj.szmq.SzmqLibrary.ZMQ_SUBSCRIBE;
import static org.bridj.Pointer.pointerToCString;

public class TestBadReceive {

    @Test
    public void testRecv() {
        System.out.println("Receive Test");
        Socket socket = new Socket(ZMQ_SUB);

        socket.setSocketOption(ZMQ_SUBSCRIBE, pointerToCString(""), 0);

        socket.curveClient(pointerToCString(TestReceive.CLIENT_PRIVATE), pointerToCString(TestReceive.CLIENT_PRIVATE));

        socket.connectEndpoint(Pointer.pointerToCString(TestReceive.CLIENT_CONNECT_ENDPOINT));

        String s = socket.receive(5000).getCString();
        System.out.println("Received: " + s);

        Assert.assertEquals("Timeout after 5000ms", s);
    }
}
