package client;

public interface SubscriptionCallback {
    void newMessageOnChain(String group, String message, String senderID);
}
