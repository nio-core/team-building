import com.google.protobuf.ByteString;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.Secp256k1Context;
import sawtooth.sdk.signing.Signer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static sawtooth.sdk.processor.Utils.hash512;

public class HyperZMQ {
    private Logger log = Logger.getLogger(HyperZMQ.class.getName());

    private Secp256k1Context context = new Secp256k1Context();
    private PrivateKey privateKey = context.newRandomPrivateKey();
    private Signer signer = new Signer(context, privateKey);
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private EventHandler eventHandler;
    private List<SubscriptionCallback> _callbacks = new ArrayList<>();
    private Crypto crypto;
    private String id;

    public HyperZMQ(String id, String pathToKeyStore, String password, boolean createNewStore) {
        this.id = id;
        crypto = new Crypto(this, pathToKeyStore, password.toCharArray(), createNewStore);

        eventHandler = new EventHandler(this, crypto);
    }

    public HyperZMQ(String id, String password, boolean createNewStore) {
        this.id = id;
        crypto = new Crypto(this, password.toCharArray(), createNewStore);

        eventHandler = new EventHandler(this, crypto);

    }

    public void sendMessageToChain(String group, String message) {
        // Create the payload in CSV format

        // The group stays in clearText to clients attempting to decrypt can know if they have
        StringBuilder msg = new StringBuilder();
        msg.append(group).append(",");
        // Encrypt the message
        byte[] msgBytes = message.getBytes(UTF8);
        byte[] cipherText = crypto.encrypt(group, msgBytes).getBytes(UTF8);
        // Encode it in Base64 and add it to the payload
        byte[] encMessageBytes = Base64.getEncoder().encode(cipherText);
        msg.append(new String(encMessageBytes, UTF8));
        byte[] payloadBytes = msg.toString().getBytes(UTF8);

        // Create Transaction Header
        TransactionHeader header = TransactionHeader.newBuilder()
                .setSignerPublicKey(signer.getPublicKey().hex())
                .setFamilyName("csvstrings") // Has to be identical in TP
                .setFamilyVersion("0.1")        // Has to be identical in TP
                // TODO setting in/outputs increases security as it limits the read/write of the transaction processor
                .addOutputs("2f9d35") // Set output as wildcard to our namespace
                .addInputs("2f9d35")
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
        List<Transaction> transactions = new ArrayList<>();
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

    private void sendBatchList(byte[] body) throws IOException {
        log.info("Sending batchlist to http://localhost:8008/batches");
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
            log.info(response);
        }
    }

    public void createGroup(String groupName, boolean subscribe) {
        try {
            crypto.createGroup(groupName);
            if (subscribe) {
                eventHandler.addGroup(groupName);
            }
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addGroup(String groupName, String key) {
        crypto.addGroup(groupName, key);
    }

    public void removeGroup(String groupName) {
        crypto.removeGroup(groupName);
        eventHandler.removeGroup(groupName);
    }

    public List<String> getGroupNames() {
        return crypto.getGroupNames();
    }

    public String getKeyForGroup(String groupName) {
        return crypto.getKeyForGroup(groupName);
    }


    void newMessage(String group, String message) {
        log.info("New Message in Group '" + group + "': " + message);
        for (SubscriptionCallback c : _callbacks) {
            c.stateChange(group, message);
        }

    }
}
