package txprocessor;

import sawtooth.sdk.processor.TransactionProcessor;

public class KeyExReceiptTP {

    public static void main(String[] args) {
        String url = "tcp://127.0.0.1:4004";
        if (args != null && args.length > 0) {
            url = args[0];
        }
        // Connect the transaction processor to the validator
        TransactionProcessor tp = new TransactionProcessor(url);
        // The handler implements the actual chaincode
        tp.addHandler(new KeyExReceiptHandler());
        Thread t = new Thread(tp);
        t.start();
    }

}
