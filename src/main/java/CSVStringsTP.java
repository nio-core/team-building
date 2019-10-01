import sawtooth.sdk.processor.TransactionProcessor;

public class CSVStringsTP {

    public static void main(String[] args) {
        // Connect the transaction processor to the validator
        TransactionProcessor tp = new TransactionProcessor("tcp://localhost:4004");
        // The handler implements the actual chaincode
        tp.addHandler(new CSVStringsHandler());
        Thread t = new Thread(tp);
        t.start();
    }
}
