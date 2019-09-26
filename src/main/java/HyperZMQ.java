import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import com.google.protobuf.ByteString;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.Secp256k1Context;
import sawtooth.sdk.signing.Signer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static sawtooth.sdk.processor.Utils.hash512;

public class HyperZMQ {
    private Secp256k1Context context = new Secp256k1Context();
    private PrivateKey privateKey = context.newRandomPrivateKey();
    private Signer signer = new Signer(context, privateKey);


    public HyperZMQ() {
        // Create the payload in CBOR format
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try {
            new CborEncoder(payload).encode(new CborBuilder()
                    .addMap()
                    .put("Verb", "set")
                    .put("Name", "foo")
                    .put("Value", 42)
                    .end()
                    .build());
        } catch (CborException e) {
            e.printStackTrace();
        }
        byte[] payloadBytes = payload.toByteArray();

        // Create Transaction Header
        TransactionHeader header = TransactionHeader.newBuilder()
                .setSignerPublicKey(signer.getPublicKey().hex())
                .setFamilyName("xo")
                .setFamilyVersion("1.0")
                .addInputs("1cf1266e282c41be5e4254d8820772c5518a2c5a8c0c7f7eda19594a7eb539453e1ed7")
                .addOutputs("1cf1266e282c41be5e4254d8820772c5518a2c5a8c0c7f7eda19594a7eb539453e1ed7")
                .setPayloadSha512(hash512(payloadBytes))
                .setBatcherPublicKey(signer.getPublicKey().hex())
                .setNonce(UUID.randomUUID().toString())
                .build();

        // Create the Transaction
        String signature = signer.sign(header.toByteArray());

        Transaction transaction = Transaction.newBuilder()
                .setHeader(header.toByteString())
                .setPayload(ByteString.copyFrom(payloadBytes))
                .setHeaderSignature(signature)
                .build();


        // Wrap the transaction in a Batch (atomic unit)

        // Create the BatchHeader
        // Transaction IDs have to be in the same order they are in the batch
        List<Transaction> transactions = new ArrayList();
        transactions.add(transaction);

        BatchHeader batchHeader = BatchHeader.newBuilder()
                .setSignerPublicKey(signer.getPublicKey().hex())
                .addAllTransactionIds(
                        transactions
                                .stream()
                                .map(Transaction::getHeaderSignature)
                                .collect(Collectors.toList())
                )
                .build();

        // Create the Batch
        // The signature of the batch acts as the Batch's ID
        String batchSignature = signer.sign(batchHeader.toByteArray());

        Batch batch = Batch.newBuilder()
                .setHeader(batchHeader.toByteString())
                .addAllTransactions(transactions)
                .setHeaderSignature(batchSignature)
                .build();

        // Encode Batches in BatchList
        // The validator expects a batchlist (which is not atomic)
        byte[] batchListBytes = BatchList.newBuilder()
                .addBatches(batch)
                .build()
                .toByteArray();

        // Send the BatchList as body via POST to the rest api /batches endpoint
        try {
            sendBatchList(batchListBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    void sendBatchList(byte[] body) throws IOException {
        URL url = new URL("http://localhost:8008/batches");
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
            System.out.println(response);
        }
    }
}
