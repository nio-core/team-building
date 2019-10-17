package client;

import com.google.protobuf.ByteString;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.signing.Signer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static sawtooth.sdk.processor.Utils.hash512;

public class HyperZMQ {
    private Logger _log = Logger.getLogger(HyperZMQ.class.getName());

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private EventHandler _eventHandler;
    private Map<String, List<SubscriptionCallback>> _callbacks = new HashMap<>();
    private Crypto _crypto;
    private String _id;

    public HyperZMQ(String id, String pathToKeyStore, String password, boolean createNewStore) {
        this._id = id;
        _crypto = new Crypto(this, pathToKeyStore, password.toCharArray(), createNewStore);
        _eventHandler = new EventHandler(this);
    }

    public HyperZMQ(String id, String password, boolean createNewStore) {
        this._id = id;
        _crypto = new Crypto(this, password.toCharArray(), createNewStore);
        _eventHandler = new EventHandler(this);
    }

    public void sendMessageToChain(String group, String message) {
        //TODO
        if (group == null || message == null || group.isEmpty() || message.isEmpty()) {
            _log.warning("Empty group and/or message!");
            return;
        }

        // Create the payload in CSV format
        // The group stays in clearText so clients attempting to decrypt can know if they can without trial and error
        StringBuilder msg = new StringBuilder();
        msg.append(group).append(",");
        // Encrypt the message
        try {
            msg.append(_crypto.encrypt(message, group));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            _log.info("Message will not be send.");
            return;
        } catch (IllegalStateException e) {
            _log.info("Trying to encrypt for group for which the key is not present (" + group + "). Message will not be send.");
            return;
        }
        byte[] payloadBytes = msg.toString().getBytes(UTF8);

        // Create Transaction Header
        Signer signer = _crypto.getSigner();
        if (signer == null) {
            throw new IllegalStateException("No signer for the transaction, returning.");
        }
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
        _log.info("Sending batchlist to http://localhost:8008/batches");
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
            _log.info(response);
        }
    }

    public void createGroup(String groupName, SubscriptionCallback callback) {
        _crypto.createGroup(groupName);
        if (callback != null) {
            subscribe(groupName, callback);
        }
    }

    public void createGroup(String groupName) {
        createGroup(groupName, null);
    }

    public void addGroup(String groupName, String key, SubscriptionCallback callback) {
        _crypto.addGroup(groupName, key);
        if (callback != null) {
            subscribe(groupName, callback);
        }
    }

    public void addGroup(String groupName, String key) {
        addGroup(groupName, key, null);
    }

    public void addCallbackToGroup(String groupName, SubscriptionCallback callback) {
        subscribe(groupName, callback);
    }

    /**
     * ALL CALLBACKS ARE INVALIDATED WHEN THE GROUP IS REMOVED
     *
     * @param groupName name of group to remove key and callbacks for
     */
    public void removeGroup(String groupName) {
        _crypto.removeGroup(groupName);
        // TODO
        _callbacks.remove(groupName);
    }

    public List<String> getGroupNames() {
        return _crypto.getGroupNames();
    }

    public String getKeyForGroup(String groupName) {
        return _crypto.getKeyForGroup(groupName);
    }

    /**
     * Receives the message from the client.EventHandler. The message is not decrypted yet.
     *
     * @param group   group name
     * @param message encrypted message
     */
    void newMessage(String group, String message) {
        try {
            String plaintext = _crypto.decrypt(message, group);
            _log.info("New message in group '" + group + "': " + plaintext);
            // Send the message to all subscribers of that group
            List<SubscriptionCallback> list = _callbacks.get(group);
            if (list != null) {
                _log.info("Callback(s) found for the group...");
                list.forEach(c -> c.newMessageOnChain(group, plaintext));
            }
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            _log.info("Received a message in a group for which a key is not present. (" + group + "," + message + ")");
        }
    }

    private void subscribe(String groupName, SubscriptionCallback callback) {
        _log.info("New subscription for group: " + groupName);
        if (_callbacks.containsKey(groupName)) {
            List<SubscriptionCallback> list = _callbacks.get(groupName);
            if (list.contains(callback)) {
                _log.info("Subscription skipped, callback already registered.");
            } else {
                list.add(callback);
                _log.info("Subscription completed, callback registered to existing group.");
            }
        } else {
            List<SubscriptionCallback> newList = new ArrayList<>();
            newList.add(callback);
            _callbacks.put(groupName, newList);
            _log.info("Subscription completed, new group created.");
        }
    }
}