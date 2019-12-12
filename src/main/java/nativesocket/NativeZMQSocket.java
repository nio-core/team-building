package nativesocket;

import org.bridj.Pointer;

import java.szmq.SzmqLibrary;

import static org.bridj.Pointer.pointerToCString;

public class NativeZMQSocket {

    public static final String CLIENT_CONNECT_ENDPOINT = "tcp://127.0.0.1:9000";
    public static final int NO_TIMEOUT = -1;

    public static final String CLIENT_PRIVATE = "N4Wt:S%oO6E{=Xk7YiN*nM+VR*Rfn/&pUDFmA5I=";
    public static final String CLIENT_PUBLIC = "YKHb-A)4EHX/uWF/kYPgl2[#0TE5uNM:O5uZt#N%";
    public static final String SERVER_PRIVATE = "gesR9P{KF{h5#VJ-)em5/GK%lR%Hp.p$zgH)B[Q$";
    public static final String SERVER_PUBLIC = "mnoq)mdV7(-y.JkJhbXUi$(hTB))-fUJ?nKp[qDg";

    szmq.Socket subSocket, pubSocket;

    public static final String TESTMESSAGE = "Hallo";
    static final String SERVER_BIND_ENDPOINT = "tcp://*:9000";

    public NativeZMQSocket(boolean send) {
        if (send) {
            pubSocket = new szmq.Socket(SzmqLibrary.ZMQ_PUB);
            pubSocket.curveServer(pointerToCString(SERVER_PRIVATE));

            pubSocket.bindEndpoint(pointerToCString(SERVER_BIND_ENDPOINT));

            Pointer<Byte> msg = pointerToCString(TESTMESSAGE);
            int sent = pubSocket.sendMessage(msg);
            System.out.println("Sent " + sent + " bytes");
        } else {
            subSocket = new szmq.Socket(SzmqLibrary.ZMQ_SUB);
            subSocket.setSocketOption(SzmqLibrary.ZMQ_SUBSCRIBE, pointerToCString(""), 0);
            subSocket.curveClient(pointerToCString(CLIENT_PRIVATE), pointerToCString(SERVER_PUBLIC));

            subSocket.connectEndpoint(pointerToCString(CLIENT_CONNECT_ENDPOINT));
            System.out.println("Receiving...");
            String s = subSocket.receive(NO_TIMEOUT).getCString();
            System.out.println("Received: " + s);
        }
    }

    public static Keypair getZ85Keypair() {
        szmq.Socket s = new szmq.Socket(SzmqLibrary.ZMQ_PUB);
        String priv = s.getPrivatekey().getCString();
        String pub = s.getPublickey(pointerToCString(priv)).getCString();

        return new Keypair(priv, pub);
    }
}
