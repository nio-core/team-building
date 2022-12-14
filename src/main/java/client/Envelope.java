package client;

import com.google.gson.Gson;

import javax.annotation.Nonnull;
import java.util.UUID;

public class Envelope {
    private final String sender;
    private final String type;
    private final String rawMessage;
    private final String uuid;

    public static final String MESSAGETYPE_CONTRACT = "contract";
    public static final String MESSAGETYPE_TEXT = "text";
    public static final String MESSAGETYPE_CONTRACT_RECEIPT = "contract_receipt";

    public Envelope(@Nonnull String sender, @Nonnull String type, @Nonnull String rawMessage) {
        this.sender = sender;
        this.rawMessage = rawMessage;
        this.type = type;
        this.uuid = UUID.randomUUID().toString();
    }

    public String getUUID() {
        return uuid;
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
