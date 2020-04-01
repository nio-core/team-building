package keyexchange;

import com.google.gson.Gson;
import txprocessor.ISignablePayload;

import javax.annotation.Nullable;

public class KeyExchangeReceipt implements ISignablePayload {

    private String memberPublicKey;
    private String applicantPublicKey;
    private ReceiptType receiptType;
    private String group; // is null if ReceiptType is JOIN_NETWORK
    private long timestamp;
    private String signature;

    // TODO add builder for signature
    // TODO add group members to payload to have member list can be encrypted (created by client instead of TP)

    public KeyExchangeReceipt(String memberPublicKey, String applicantPublicKey, ReceiptType receiptType, @Nullable String group, long timestamp) {
        this.memberPublicKey = memberPublicKey;
        this.applicantPublicKey = applicantPublicKey;
        this.receiptType = receiptType;
        this.group = group;
        if (receiptType == ReceiptType.JOIN_NETWORK)
            this.group = null;
        this.timestamp = timestamp;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getMemberPublicKey() {
        return memberPublicKey;
    }

    public String getApplicantPublicKey() {
        return applicantPublicKey;
    }

    public ReceiptType getReceiptType() {
        return receiptType;
    }

    public String getGroup() {
        return group;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public String getSignablePayload() {
        String ret = "" + applicantPublicKey + "|" + receiptType + "|" + timestamp;
        if (receiptType == ReceiptType.JOIN_GROUP) {
            ret += "|" + group;
        }
        return ret;
    }
}
