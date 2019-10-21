package contracts;

import com.google.gson.Gson;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class Contract {

    public static final String REQUESTED_PROCESSOR_ANY = "*";

    private final String contractID;
    private final String issuer;
    private final String requestedProcessor;
    private final String operation;
    private final List<String> args;

    public Contract(@Nonnull String issuer, @Nullable String requestedProcessor, @Nonnull String operation, @Nullable List<String> args) {
        this.issuer = issuer;
        this.operation = operation;
        this.args = args;
        this.contractID = UUID.randomUUID().toString();
        this.requestedProcessor = requestedProcessor != null ? requestedProcessor : REQUESTED_PROCESSOR_ANY;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getOperation() {
        return operation;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getContractID() {
        return contractID;
    }

    public String getRequestedProcessor() {
        return requestedProcessor;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
