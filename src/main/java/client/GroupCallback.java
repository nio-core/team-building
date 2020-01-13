package client;

public interface GroupCallback {
    void newMessageOnChain(String group, String message, String senderID);
}
