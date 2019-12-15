package txprocessor;


import sawtooth.sdk.processor.TransactionProcessor;

public class CSVStringsTP {

    public static void main(String[] args) {
        String url = "tcp://localhost:4004";
        if (args != null && args[0] != null) {
            url = args[0];
        }
        // Connect the transaction processor to the validator
        TransactionProcessor tp = new TransactionProcessor(url);
        // The handler implements the actual chaincode
        tp.addHandler(new CSVStringsHandler());
        Thread t = new Thread(tp);
        t.start();
    }
}
