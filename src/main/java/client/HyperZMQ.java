package client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.ByteString;
import contracts.Contract;
import contracts.ContractProcessor;
import contracts.ContractReceipt;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.signing.Signer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static client.Envelope.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static sawtooth.sdk.processor.Utils.hash512;

public class HyperZMQ {
    private Logger _log = Logger.getLogger(HyperZMQ.class.getName());

    private EventHandler _eventHandler;
    private Crypto _crypto;
    private String _id;
    private List<ContractProcessor> _contractProcessors = new ArrayList<>();
    private Map<String, List<SubscriptionCallback>> _textmessageCallbacks = new HashMap<>();
    private Map<String, ContractProcessingCallback> _contractCallbacks = new HashMap<>(); // key is the contractID

    public HyperZMQ(String id, String pathToKeyStore, String keystorePassword, boolean createNewStore) {
        this._id = id;
        _crypto = new Crypto(this, pathToKeyStore, keystorePassword.toCharArray(), createNewStore);
        _eventHandler = new EventHandler(this);
    }

    public HyperZMQ(String id, String keystorePassword, boolean createNewStore) {
        this._id = id;
        _crypto = new Crypto(this, keystorePassword.toCharArray(), createNewStore);
        _eventHandler = new EventHandler(this);
    }

    private void sendToChain(String group, Envelope envelope) {
        // Create the payload in CSV format
        // The group stays in clearText so clients attempting to decrypt can know if they can without trial and error
        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append(group).append(",");
        // Encrypt the whole message
        try {
            msgBuilder.append(_crypto.encrypt(envelope.toString(), group));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            _log.info("Message will not be send.");
            return;
        } catch (IllegalStateException e) {
            _log.info("Trying to encrypt for group for which the key is not present (" + group + "). Message will not be send.");
            return;
        }
        byte[] payloadBytes = msgBuilder.toString().getBytes(UTF_8);

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

    public void sendTextToChain(String group, String message) {
        if (group == null || message == null || group.isEmpty() || message.isEmpty()) {
            _log.warning("Empty group and/or message!");
            return;
        }
        // Wrap the message - the complete envelope will be encrypted
        Envelope envelope = new Envelope(_id, MESSAGETYPE_TEXT, message);
        sendToChain(group, envelope);
    }

    public void sendContractToChain(String groupName, Contract contract, ContractProcessingCallback callback) {
        if (callback != null) {
            _contractCallbacks.put(contract.getContractID(), callback);
        }
        sendContractToChain(groupName, contract);
    }

    public void sendContractToChain(String groupName, Contract contract) {
        if (groupName == null || contract == null) {
            _log.warning("Empty group and/or contract!");
            return;
        }
        // Wrap the contract - the complete envelope will be encrypted
        Envelope envelope = new Envelope(_id, MESSAGETYPE_CONTRACT, contract.toString());
        sendToChain(groupName, envelope);
    }

    private void sendReceiptToChain(String groupName, ContractReceipt receipt) {
        if (groupName == null || receipt == null) {
            _log.warning("Empty group and/or receipt!");
            return;
        }

        Envelope envelope = new Envelope(_id, MESSAGETYPE_CONTRACT_RECEIPT, receipt.toString());
        sendToChain(groupName, envelope);
    }

    public void addContractProcessor(ContractProcessor contractProcessor) {
        _contractProcessors.add(contractProcessor);
    }

    public void removeContractProcessor(ContractProcessor contractProcessor) {
        _contractProcessors.remove(contractProcessor);
    }

    public void createGroup(String groupName, SubscriptionCallback callback) {
        _crypto.createGroup(groupName);
        if (callback != null) {
            putCallback(groupName, callback);
        }
    }

    public void createGroup(String groupName) {
        createGroup(groupName, null);
    }

    public void addGroup(String groupName, String key, SubscriptionCallback callback) {
        _crypto.addGroup(groupName, key);
        if (callback != null) {
            putCallback(groupName, callback);
        }
    }

    public void addGroup(String groupName, String key) {
        addGroup(groupName, key, null);
    }

    public void addCallbackToGroup(String groupName, SubscriptionCallback callback) {
        putCallback(groupName, callback);
    }

    /**
     * ALL CALLBACKS ARE INVALIDATED WHEN THE GROUP IS REMOVED
     *
     * @param groupName name of group to remove key and callbacks for
     */
    public void removeGroup(String groupName) {
        _crypto.removeGroup(groupName);
        // TODO
        _textmessageCallbacks.remove(groupName);
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
     * @param group            group name
     * @param encryptedMessage encrypted message
     */
    void newEventReceived(String group, String encryptedMessage) {
        String plainMessage;
        try {
            plainMessage = _crypto.decrypt(encryptedMessage, group);
            //_log.info("New message in group '" + group + "': " + plainMessage);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return;
        } catch (IllegalStateException e) {
            _log.info("Received a message in a group for which a key is not present. Message: (" + group + "," + encryptedMessage + ")");
            return;
        }
        Envelope envelope = new Gson().fromJson(plainMessage, Envelope.class);
        // TODO
        switch (envelope.getType()) {
            case MESSAGETYPE_CONTRACT: {
                handleContractMessage(group, envelope);
                break;
            }
            case MESSAGETYPE_TEXT: {
                handleTextMessage(group, envelope);
                break;
            }
            case MESSAGETYPE_CONTRACT_RECEIPT: {
                handleContractReceipt(group, envelope);
                break;
            }
            default:
                _log.info("Unknown message type: " + envelope.getType());
                break;
        }
    }

    private void handleContractReceipt(String group, Envelope envelope) {
        ContractReceipt receipt;
        try {
            receipt = new Gson().fromJson(envelope.getRawMessage(), ContractReceipt.class);
        } catch (JsonSyntaxException e) {
            _log.warning("Cannot convert to ContractReceipt: " + envelope.getRawMessage());
            return;
        }
        ContractProcessingCallback cb = _contractCallbacks.get(receipt.getContract().getContractID());
        if (cb != null) {
            cb.processingFinished(receipt);
        }
    }

    private void handleContractMessage(String group, Envelope envelope) {
        // TODO ASYNC THIS?
        Contract contract;
        try {
            contract = new Gson().fromJson(envelope.getRawMessage(), Contract.class);
        } catch (JsonSyntaxException e) {
            _log.info("Could not extract contract from envelope: " + envelope.toString());
            return;
        }
        if (_id.equals(contract.getRequestedProcessor()) || Contract.REQUESTED_PROCESSOR_ANY.equals(contract.getRequestedProcessor())) {
            // Find a processor to handle the message
            Object result = null;
            for (ContractProcessor processor : _contractProcessors) {
                if (processor.getSupportedOperations().contains(contract.getOperation())) {
                    result = processor.processContract(contract);
                    if (result != null) {
                        break;
                    }
                }
            }
            if (result == null) {
                _log.info("No processor found for contract: " + contract.toString());
                return;
            }
            //
            //_log.info("Contract processed with result: " + result);

            // Process the result: Build a receipt to send back
            ContractReceipt receipt = new ContractReceipt(_id, String.valueOf(result), contract);
            sendReceiptToChain(group, receipt);
        } else {
            //_log.info("Contract was not for this client: " + contract);
        }

    }


    private void handleTextMessage(String group, Envelope envelope) {
        // Send the message to all subscribers of that group
        List<SubscriptionCallback> list = _textmessageCallbacks.get(group);
        if (list != null) {
            //_log.info("Callback(s) found for the group...");
            list.forEach(c -> c.newMessageOnChain(group, envelope.getRawMessage(), envelope.getSender()));
        }
    }

    private void putCallback(String groupName, SubscriptionCallback callback) {
        //_log.info("New subscription for group: " + groupName);
        if (_textmessageCallbacks.containsKey(groupName)) {
            List<SubscriptionCallback> list = _textmessageCallbacks.get(groupName);
            if (list.contains(callback)) {
                //_log.info("Subscription skipped, callback already registered.");
            } else {
                list.add(callback);
                //_log.info("Subscription completed, callback registered to existing group.");
            }
        } else {
            List<SubscriptionCallback> newList = new ArrayList<>();
            newList.add(callback);
            _textmessageCallbacks.put(groupName, newList);
            //_log.info("Subscription completed, new group created.");
        }
    }

    private void sendBatchList(byte[] body) throws IOException {
        //_log.info("Sending batchlist to http://localhost:8008/batches");
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
            //_log.info(response);
        }
    }
}
