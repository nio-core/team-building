package contracts;

import com.google.gson.Gson;

import java.util.List;
import java.util.UUID;

public class Contract {
    private String issuer;
    private String operation;
    private List<String> args;
    private String contractID;

    public Contract(String issuer, String operation, List<String> args) {
        this.issuer = issuer;
        this.operation = operation;
        this.args = args;
        this.contractID = UUID.randomUUID().toString();
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

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
