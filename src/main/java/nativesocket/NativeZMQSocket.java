package nativesocket;

import com.nativelibs4java.bridj.szmq.Socket;

public class NativeZMQSocket {

    private Socket _socket;

    protected NativeZMQSocket(int type) {
        _socket = new Socket(type);
    }


}
