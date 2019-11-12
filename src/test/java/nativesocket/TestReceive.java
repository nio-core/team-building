package nativesocket;

import com.nativelibs4java.bridj.szmq.Socket;
import org.bridj.Pointer;
import org.junit.Assert;
import org.junit.Test;

import static com.nativelibs4java.bridj.szmq.SzmqLibrary.ZMQ_SUB;
import static com.nativelibs4java.bridj.szmq.SzmqLibrary.ZMQ_SUBSCRIBE;
import static org.bridj.Pointer.pointerToCString;

/*
    BRIDJ_DEBUG=1;BRIDJ_DIRECT=0
*/
public class TestReceive {
    public static final String CLIENT_CONNECT_ENDPOINT = "tcp://127.0.0.1:9000";
    public static final int NO_TIMEOUT = -1;

    public static final String CLIENT_PRIVATE = "N4Wt:S%oO6E{=Xk7YiN*nM+VR*Rfn/&pUDFmA5I=";
    public static final String CLIENT_PUBLIC = "YKHb-A)4EHX/uWF/kYPgl2[#0TE5uNM:O5uZt#N%";
    public static final String SERVER_PRIVATE = "gesR9P{KF{h5#VJ-)em5/GK%lR%Hp.p$zgH)B[Q$";
    public static final String SERVER_PUBLIC = "mnoq)mdV7(-y.JkJhbXUi$(hTB))-fUJ?nKp[qDg";

    @Test
    public void testRecv() {
        System.out.println("Receive Test");
        Socket socket = new Socket(ZMQ_SUB);

        socket.setSocketOption(ZMQ_SUBSCRIBE, pointerToCString(""), 0);

        socket.curveClient(pointerToCString(CLIENT_PRIVATE), pointerToCString(SERVER_PUBLIC));

        socket.connectEndpoint(Pointer.pointerToCString(CLIENT_CONNECT_ENDPOINT));

        String s = socket.receive(NO_TIMEOUT).getCString();
        System.out.println("Received: " + s);

        Assert.assertEquals(TestSend.TESTMESSAGE, s);
    }
}
