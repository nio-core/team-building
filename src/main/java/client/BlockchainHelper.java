package client;

import com.google.protobuf.ByteString;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.signing.Signer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static sawtooth.sdk.processor.Utils.hash512;

class BlockchainHelper {
    private static final String DEFAULT_BATCHES_ENDPOINT = "http://localhost:8008/batches";
    private String _batchesEndpoint;
    private Signer _signer;
    private HyperZMQ _hyperzmq;

    BlockchainHelper(HyperZMQ hyperZMQ, Signer signer, String batchesEndpoint) {
        _hyperzmq = hyperZMQ;
        _signer = signer;
        _batchesEndpoint = batchesEndpoint;
    }

    BlockchainHelper(HyperZMQ hyperZMQ, Signer signer) {
        _hyperzmq = hyperZMQ;
        _batchesEndpoint = DEFAULT_BATCHES_ENDPOINT;
        _signer = signer;
    }

    Transaction buildTransaction(byte[] payload) {
        // Create Transaction Header
        /*if (signer == null) {
            throw new IllegalStateException("No signer for the transaction, returning.");
        }*/
        TransactionHeader header = TransactionHeader.newBuilder()
                .setSignerPublicKey(_signer.getPublicKey().hex())
                .setFamilyName("csvstrings") // Has to be identical in TP
                .setFamilyVersion("0.1")        // Has to be identical in TP
                // TODO setting in/outputs increases security as it limits the read/write of the transaction processor
                .addOutputs("2f9d35") // Set output as wildcard to our namespace
                .addInputs("2f9d35")
                .setPayloadSha512(hash512(payload))
                .setBatcherPublicKey(_signer.getPublicKey().hex())
                .setNonce(UUID.randomUUID().toString())
                .build();

        // Create the Transaction
        String signature = _signer.sign(header.toByteArray());

        Transaction transaction = Transaction.newBuilder()
                .setHeader(header.toByteString())
                .setPayload(ByteString.copyFrom(payload))
                .setHeaderSignature(signature)
                .build();
        return transaction;
    }

    void buildAndSendBatch(List<Transaction> transactionList) {
        // Wrap the transaction in a Batch (atomic unit)
        // Create the BatchHeader
        BatchHeader batchHeader = BatchHeader.newBuilder()
                .setSignerPublicKey(_signer.getPublicKey().hex())
                .addAllTransactionIds(
                        transactionList
                                .stream()
                                .map(Transaction::getHeaderSignature)
                                .collect(Collectors.toList())
                )
                .build();

        // Create the Batch
        // The signature of the batch acts as the Batch's ID
        String batchSignature = _signer.sign(batchHeader.toByteArray());
        Batch batch = Batch.newBuilder()
                .setHeader(batchHeader.toByteString())
                .addAllTransactions(transactionList)
                .setHeaderSignature(batchSignature)
                .build();
        // Encode Batches in BatchList
        // The validator expects a batchlist (which is not atomic)
        byte[] batchListBytes = BatchList.newBuilder()
                .addBatches(batch)
                .build()
                .toByteArray();

        try {
            sendBatchList(batchListBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendBatchList(byte[] body) throws IOException {
        URL url = new URL(_batchesEndpoint);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection) con;
        http.setRequestMethod("POST"); // PUT is another valid option
        http.setDoOutput(true);
        http.setRequestProperty("Content-Type", "application/octet-stream");
        http.connect();

        try (OutputStream os = http.getOutputStream()) {
            os.write(body);
        }

        String response;

        try (InputStream is = http.getInputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            Stream<String> lines = br.lines();
            response = lines.reduce("", (accu, s) -> accu += s);
        }

        if (response != null) {
            _hyperzmq.logprint(response);
        }
    }
}
