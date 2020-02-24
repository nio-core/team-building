package txprocessor;


import sawtooth.sdk.processor.TransactionProcessor;

public class CSVStringsTP {

    public static void main(String[] args) {
        String url = "tcp://192.168.178.124:4004";
        if (args != null && args.length > 0) {
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
