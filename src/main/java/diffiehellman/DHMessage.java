package diffiehellman;

import com.google.gson.Gson;
import txprocessor.ISignablePayload;

public class DHMessage implements ISignablePayload {

    private String publicKey; // DH PublicKey in Base64
    private String senderID;
    private String signature;

    public DHMessage(String publicKey, String senderID) {
        this.publicKey = publicKey;
        this.senderID = senderID;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getSenderID() {
        return senderID;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public String getSignablePayload() {
        return senderID + "|" + publicKey;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
