package contracts;

import client.HyperZMQ;
import com.google.gson.Gson;
import sawtooth.sdk.processor.Utils;

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
    private final String outputAddr; // Address this contract is written to
    private final String resultOutputAddr; // Address the result of this contract should be written to

    public Contract(@Nonnull String issuer, @Nullable String requestedProcessor, @Nonnull String operation, @Nullable List<String> args) {
        this.issuer = issuer;
        this.operation = operation;
        this.args = args;
        this.contractID = UUID.randomUUID().toString();
        this.requestedProcessor = requestedProcessor != null ? requestedProcessor : REQUESTED_PROCESSOR_ANY;

        // Generate output addresses to clients can track this contract
        String idHash = Utils.hash512(contractID.getBytes());
        this.outputAddr = HyperZMQ.CSVSTRINGS_NAMESPACE_PREFIX + idHash.substring(idHash.length() - 64);

        String otherHash = Utils.hash512(UUID.randomUUID().toString().getBytes());
        this.resultOutputAddr = HyperZMQ.CSVSTRINGS_NAMESPACE_PREFIX + otherHash.substring(otherHash.length() - 64);
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

    public String getOutputAddr() {
        return outputAddr;
    }

    public String getResultOutputAddr() {
        return resultOutputAddr;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
