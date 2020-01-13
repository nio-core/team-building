package contracts;

import java.util.List;

public interface ContractProcessor {
    Object processContract(Contract contract);

    List<String> getSupportedOperations();
}
