package client;

import com.google.gson.Gson;

import javax.annotation.Nonnull;

public class Envelope {
    private String sender;
    private String type;
    private String rawMessage;

    public static final String TYPE_CONTRACT = "contract";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_CONTRACT_RECEIPT = "contract_receipt";

    public Envelope(@Nonnull String sender, @Nonnull String type, @Nonnull String rawMessage) {
        this.sender = sender;
        this.rawMessage = rawMessage;
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
