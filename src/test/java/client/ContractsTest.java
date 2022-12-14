package client;

import com.google.gson.Gson;
import contracts.Contract;
import contracts.ContractProcessor;
import contracts.ContractReceipt;
import contracts.SumContractProcessor;
import org.junit.Test;
import txprocessor.CSVStringsTP;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ContractsTest {
    private static final String TESTGROUP = "testGroup";

    @Test
    public void testSumContractProcessor() {
        List<String> args = Arrays.asList("1", "2", "3", "aaa", "10"); // = 16, string should be skipped
        Contract contract = new Contract("testClient", Contract.REQUESTED_PROCESSOR_ANY, "sum", args);

        SumContractProcessor processor = new SumContractProcessor();

        Object result = processor.processContract(contract);
        if (result instanceof Integer) {
            assertEquals(16, (int) result);
        } else {
            fail();
        }
    }

    @Test
    public void testProcessingViaChain() {
        CSVStringsTP.main(null);
        sleep(1000);
        // Setup clients to receive messages from each other
        HyperZMQ client1 = new HyperZMQ("client1", "password", true);
        HyperZMQ client2 = new HyperZMQ("client2", "password", true);
        client1.createGroup(TESTGROUP);
        client2.addGroup(TESTGROUP, client1.getKeyForGroup(TESTGROUP));

        // Setup contract + processor
        Contract contract = new Contract("client1", "client2", "sum", Arrays.asList("1", "5"));
        ContractProcessor processor = new SumContractProcessor();
        client2.addContractProcessor(processor);

        client1.sendContractToChain(TESTGROUP, contract, contractReceipt -> {
            System.out.println("Result: " + contractReceipt.getResult());
            System.out.println("Processor: " + contractReceipt.getProcessor());
            assertEquals("1+5 should be 6", "6", contractReceipt.getResult());
            assertEquals("Processor should be client2 as specified", "client2", contractReceipt.getProcessor());
        });
        sleep(3000);

        // Now client1 processes its own contract
        client2.removeContractProcessor(processor);
        client1.addContractProcessor(processor);

        Contract contract2 = new Contract("client1", Contract.REQUESTED_PROCESSOR_ANY, "sum", Arrays.asList("6", "8", "2", "4"));
        client1.sendContractToChain(TESTGROUP, contract2, contractReceipt -> {
            System.out.println("Result: " + contractReceipt.getResult());
            System.out.println("Processor: " + contractReceipt.getProcessor());
            assertEquals("6+8+2+4 = 20", "20", contractReceipt.getResult());
            assertEquals("Processor should be client1", "client1", contractReceipt.getProcessor());
        });

        sleep(3000);
    }

    @Test
    public void testQueryRest() {
        CSVStringsTP.main(null);
        sleep(1000);
        // Setup client to receive messages from self
        HyperZMQ client1 = new HyperZMQ("client1", "password", true);
        client1.createGroup(TESTGROUP);

        // Setup contract + processor
        Contract contract = new Contract("client1", "client1", "sum", Arrays.asList("1", "5"));
        ContractProcessor processor = new SumContractProcessor();
        client1.addContractProcessor(processor);

        String outputAddr = contract.getOutputAddr();
        String resultAddr = contract.getResultOutputAddr();

        client1.sendContractToChain(TESTGROUP, contract);

        // No callback - wait some secs and check the result address to see if client1 processed it
        sleep(3000);
        //System.out.println(client1.queryStateAdress(outputAddr));
        Envelope e = client1.queryStateAddress(resultAddr);
        assertNotNull(e);
        assertEquals(Envelope.MESSAGETYPE_CONTRACT_RECEIPT, e.getType());
        assertEquals("client1", e.getSender());

        // Extract the receipt from the envelope
        ContractReceipt receipt = new Gson().fromJson(e.getRawMessage(), ContractReceipt.class);
        assertEquals("6", receipt.getResult());
        assertEquals(contract.getContractID(), receipt.getContract().getContractID());
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
