package contracts;
import com.google.gson.Gson;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ContractReceipt {

    private String processor;
    private String receiptID;
    private String result;
    private Contract contract;

    public ContractReceipt(@Nonnull String processor, @Nonnull String result, @Nonnull Contract contract) {
        this.processor = processor;
        this.receiptID = UUID.randomUUID().toString();
        this.result = result;
        this.contract = contract;
    }

    public String getProcessor() {
        return processor;
    }

    public String getReceiptID() {
        return receiptID;
    }

    public String getResult() {
        return result;
    }

    public Contract getContract() {
        return contract;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
