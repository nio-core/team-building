package client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import contracts.Contract;
import contracts.ContractProcessor;
import contracts.ContractReceipt;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.protobuf.Transaction;
import zmq.io.mechanism.curve.Curve;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

import static client.Envelope.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HyperZMQ {

    public static final String CSVSTRINGS_NAMESPACE_PREFIX = "2f9d35";

    private EventHandler _eventHandler;
    private Crypto _crypto;
    private String _clientID;
    private List<ContractProcessor> _contractProcessors = new ArrayList<>();
    private Map<String, List<GroupCallback>> _textmessageCallbacks = new HashMap<>();
    private Map<String, ContractProcessingCallback> _contractCallbacks = new HashMap<>(); // key is the contractID
    private BlockchainHelper _blockchainHelper;
    private ZContext _zContext = new ZContext();
    private boolean _passthroughAll = false;

    /**
     * @param id               id
     * @param pathToKeyStore   path to a keystore file including .jks
     * @param keystorePassword password for the keystore
     * @param createNewStore   whether a new keystore should be created, if true a new signer (=blockchain identity)
     *                         and encryption key will be created
     */
    public HyperZMQ(String id, String pathToKeyStore, String keystorePassword, boolean createNewStore) {
        _clientID = id;
        _crypto = new Crypto(this, pathToKeyStore, keystorePassword.toCharArray(), createNewStore);
        _eventHandler = new EventHandler(this);
        _blockchainHelper = new BlockchainHelper(this, _crypto.getSigner());
    }

    /**
     * Using the default keystore file path
     */
    public HyperZMQ(String id, String keystorePassword, boolean createNewStore) {
        _clientID = id;
        _crypto = new Crypto(this, keystorePassword.toCharArray(), createNewStore);
        _eventHandler = new EventHandler(this);
        _blockchainHelper = new BlockchainHelper(this, _crypto.getSigner());
    }

    /**
     * The the URL of the RestAPI to something else than localhost
     *
     * @param url base URL of the rest api
     */
    public void setRestAPIUrl(String url) {
        _blockchainHelper.setRestAPIUrl(url);
    }

    /**
     * Create a socket with encryption set up as a server which binds.
     * (Other clients need this clients public key to create a socket which can
     * receive from the one created)
     *
     * @param type        type of socket
     * @param myKeysAlias alias for this clients key
     * @param addr        address to call bind for
     * @return socket or null if error
     */
    public ZMQ.Socket makeServerSocket(int type, String myKeysAlias, String addr) {
        Keypair kp = _crypto.getKeypair(myKeysAlias);
        if (kp == null) {
            System.out.println("No keys for alias " + myKeysAlias + "found!");
            return null;
        }

        ZMQ.Socket s = _zContext.createSocket(type);
        s.setAsServerCurve(true);
        s.setCurvePublicKey(kp.publicKey.getBytes());
        s.setCurveSecretKey(kp.privateKey.getBytes());
        s.bind(addr);
        return s;
    }

    /**
     * Create a socket with encryption set up as client which connects
     *
     * @param type          type of socket
     * @param myKeysAlias   alias for this clients key
     * @param theirKeyAlias alias for the key of entity we want to receive from
     * @param addr          address to call connect for
     * @return socket or null if error
     */
    public ZMQ.Socket makeClientSocket(int type, String myKeysAlias, String theirKeyAlias, String addr) {
        Keypair server = _crypto.getKeypair(theirKeyAlias);
        if (server == null) {
            System.out.println("No keys for alias " + theirKeyAlias + "found!");
            return null;
        }
        Keypair client = _crypto.getKeypair(myKeysAlias);
        if (client == null) {
            System.out.println("No keys for alias " + myKeysAlias + "found!");
            return null;
        }

        ZMQ.Socket s = _zContext.createSocket(type);
        s.setCurvePublicKey(client.publicKey.getBytes());
        s.setCurveSecretKey(client.privateKey.getBytes());
        s.setCurveServerKey(server.publicKey.getBytes());
        s.connect(addr);
        return s;
    }

    /**
     * Generate a Keypair of 256bit key for the Curve25519 elliptic curve.
     * The keys are encoded in Z85 (a ascii85 variant).
     * The keypair is also added to the store.
     *
     * @return keypair
     */
    public Keypair generateZ85Keypair(String alias) {
        Curve curve = new Curve();
        String[] keys = curve.keypairZ85();
        Keypair kp = new Keypair(alias, keys[1], keys[0]);
        _crypto.addKeypair(kp);
        return kp;
    }

    public void addForeignKeypair(String alias, String publicKey) {
        _crypto.addKeypair(new Keypair(alias, null, publicKey));
    }

    /**
     * @param alias alias
     * @return keypair or null if not found
     */
    public Keypair getKeypair(String alias) {
        return _crypto.getKeypair(alias);
    }

    public boolean removeKeypair(String alias) {
        return _crypto.removeKeypair(alias);
    }

    public boolean groupIsAvailable(String groupName) {
        return _crypto.hasKeyForGroup(groupName);
    }

    /**
     * Query the given address of the global state.
     *
     * @param addr address to query (70 hex chars)
     * @return response or null if error
     */
    public Envelope queryStateAddress(String addr) {
        try {
            // The data is stored in Base64, decode it first
            String raw = _blockchainHelper.queryStateAddress(addr);
            if (raw == null) {
                return null;
            }
            byte[] bytes = Base64.getDecoder().decode(raw);
            String data = new String(bytes, UTF_8);

            // Now we have <group>,<decrypted msg>
            String[] strings = data.split(",");
            if (strings.length < 2) {
                System.out.println("Queried data format is incorrect: " + data);
                return null;
            }
            if (!_crypto.hasKeyForGroup(strings[0])) {
                System.out.println("Can't decrypt queried data, because key is missing for group: " + strings[0]);
                return null;
            }

            try {
                String clearText = _crypto.decrypt(strings[1], strings[0]);
                return new Gson().fromJson(clearText, Envelope.class);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                return null;
            } catch (JsonSyntaxException e) {
                System.out.println("Queried data could not be deserialized to Envelope");
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return null;
    }

    /**
     * Set to true to get a callback for ALL messages in groups (incl contracts and receipts), not just text.
     * If true, the callback's message can contain Contract and ContractReceipt objects in serialized form (JSON).
     *
     * @param value value to set
     */
    void setPassthroughAllMessages(boolean value) {
        _passthroughAll = value;
    }

    /**
     * Send a single message to a group
     * Builds batch list with a single batch with a single transaction in it.
     *
     * @param groupName group
     * @param message   message
     */
    public boolean sendTextToChain(String groupName, String message) {
        if (groupName == null || message == null || groupName.isEmpty() || message.isEmpty()) {
            logprint("Empty group and/or message!");
            return false;
        }
        // Wrap the message - the complete envelope will be encrypted
        Envelope envelope = new Envelope(_clientID, MESSAGETYPE_TEXT, message);
        return sendSingleEnvelope(groupName, envelope, null);
    }

    /**
     * Send multiple messages in a group
     * Builds a batch list with a single batch with multiple transactions in it
     *
     * @param groupName groups name
     * @param messages  messages
     */
    public boolean sendTextsToChain(String groupName, List<String> messages) {
        if (groupName == null || messages == null || groupName.isEmpty() || messages.isEmpty()) {
            logprint("Empty group and/or message!");
            return false;
        }
        return sendTextsToChain(Collections.singletonMap(groupName, messages));
    }

    /**
     * Send multiple messages in multiple groups
     * Builds a batch list with a single batch with multiple transactions in it
     *
     * @param map map of group with their corresponding messages
     */
    public boolean sendTextsToChain(Map<String, List<String>> map) {
        if (map == null || map.isEmpty()) {
            logprint("Empty map!");
            return false;
        }
        Map<String, List<Envelope>> list = new HashMap<>();

        map.forEach((group, messages) -> {
            List<Envelope> envelopeList = new ArrayList<>();
            for (String message : messages) {
                envelopeList.add(new Envelope(_clientID, MESSAGETYPE_TEXT, message));
            }
            list.put(group, envelopeList);
        });
        return sendEnvelopeList(list);
    }

    private boolean sendSingleEnvelope(String group, Envelope envelope, String outputAddr) {
        byte[] payloadBytes = encryptEnvelope(group, envelope);
        List<Transaction> transactionList = Collections.singletonList(_blockchainHelper.buildTransaction(payloadBytes, outputAddr));
        return _blockchainHelper.buildAndSendBatch(transactionList);
    }

    private boolean sendEnvelopeList(Map<String, List<Envelope>> list) {
        List<Transaction> transactionList = new ArrayList<>();
        list.forEach((groupName, envelopeList) -> {
            envelopeList.forEach((envelope -> {
                transactionList.add(_blockchainHelper.buildTransaction(encryptEnvelope(groupName, envelope)));
            }));
        });
        return _blockchainHelper.buildAndSendBatch(transactionList);
    }

    public boolean sendContractToChain(String groupName, Contract contract, ContractProcessingCallback callback) {
        if (callback != null) {
            _contractCallbacks.put(contract.getContractID(), callback);
        }
        return sendContractToChain(groupName, contract);
    }

    /**
     * Send a list of contracts and callbacks (can be null) in a group
     * @param groupName group name
     * @param map  map of contracts and callback
     * @return success
     */
    public boolean sendContractsToChain(String groupName, Map<Contract, ContractProcessingCallback> map) {
        if (groupName == null || map.isEmpty()) {
            logprint("Empty group and/or contracts");
            return false;
        }
        List<Envelope> envelopes = new ArrayList<>();
        map.forEach((contract, callback) -> {
            Envelope e = new Envelope(_clientID, MESSAGETYPE_CONTRACT, contract.toString());
            envelopes.add(e);
            if (callback != null) {
                _contractCallbacks.put(contract.getContractID(), callback);
            }
        });
        return sendEnvelopeList(Collections.singletonMap(groupName, envelopes));
    }

    public boolean sendContractToChain(String groupName, Contract contract) {
        if (groupName == null || contract == null) {
            logprint("Empty group and/or contract!");
            return false;
        }
        // Wrap the contract - the complete envelope will be encrypted
        Envelope envelope = new Envelope(_clientID, MESSAGETYPE_CONTRACT, contract.toString());
        return sendSingleEnvelope(groupName, envelope, contract.getOutputAddr());
    }

    private boolean sendReceiptToChain(String groupName, ContractReceipt receipt, String resultOutputAddr) {
        if (groupName == null || receipt == null) {
            logprint("Empty group and/or receipt!");
            return false;
        }

        Envelope envelope = new Envelope(_clientID, MESSAGETYPE_CONTRACT_RECEIPT, receipt.toString());
        //System.out.println("Sending receipt to addr: " + resultOutputAddr);
        return sendSingleEnvelope(groupName, envelope, resultOutputAddr);
    }

    public void addContractProcessor(ContractProcessor contractProcessor) {
        _contractProcessors.add(contractProcessor);
    }

    public void removeContractProcessor(ContractProcessor contractProcessor) {
        _contractProcessors.remove(contractProcessor);
    }

    /**
     * Create a new group. Generates a new secret key which can be accessed by getKeyForGroup afterwards.
     *
     * @param groupName group name
     * @param callback  callback that is called when a new message arrives
     */
    public void createGroup(String groupName, GroupCallback callback) {
        _crypto.createGroup(groupName);
        if (callback != null) {
            putCallback(groupName, callback);
        }
        _eventHandler.subscribeToGroup(groupName);
    }

    /**
     * Create a new group. Generates a new secret key which can be accessed by getKeyForGroup afterwards.
     *
     * @param groupName group name
     */
    public void createGroup(String groupName) {
        createGroup(groupName, null);
    }

    /**
     * Add a group with an external key.
     * The group name has to be identical with the one that messages are sent to.
     *
     * @param groupName name of the group
     * @param key       the key in Base64
     * @param callback  callback to be called when a new messages arrives
     */
    public void addGroup(String groupName, String key, GroupCallback callback) {
        _crypto.addGroup(groupName, key);
        if (callback != null) {
            putCallback(groupName, callback);
        }
        _eventHandler.subscribeToGroup(groupName);
    }

    /**
     * Add a group with an external key.
     * The group name has to be identical with the one that messages are sent to.
     *
     * @param groupName name of the group
     * @param key       the key in Base64
     */
    public void addGroup(String groupName, String key) {
        addGroup(groupName, key, null);
    }

    /**
     * Add a new callback to a group
     *
     * @param groupName group to add the callback to
     * @param callback  callback to add
     * @return true if successful, false if error or already existent
     */
    public boolean addCallbackToGroup(String groupName, GroupCallback callback) {
        return putCallback(groupName, callback);
    }

    /**
     * ALL CALLBACKS ARE INVALIDATED WHEN THE GROUP IS REMOVED
     *
     * @param groupName name of group to remove key and callbacks for
     */
    public void removeGroup(String groupName) {
        _crypto.removeGroup(groupName);
        _textmessageCallbacks.remove(groupName);
    }

    /**
     * List of known group names for which the secret key is present
     *
     * @return list of names
     */
    public List<String> getGroupNames() {
        return _crypto.getGroupNames();
    }

    /**
     * Returns the secret key for the specified group
     *
     * @param groupName group name to return key for
     * @return key in Base64 format or null if not found
     */
    public String getKeyForGroup(String groupName) {
        return _crypto.getKeyForGroup(groupName);
    }

    /**
     * Receives the message from the client.EventHandler. The message is not decrypted yet.
     *
     * @param group            group name
     * @param encryptedMessage encrypted message
     */
    synchronized void newEventReceived(String group, String encryptedMessage) {
        String plainMessage;
        try {
            plainMessage = _crypto.decrypt(encryptedMessage, group);
            //logprint("New message in group '" + group + "': " + plainMessage);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return;
        } catch (IllegalStateException e) {
            logprint("Received a message in a group for which a key is not present. Message: (" + group + "," + encryptedMessage + ")");
            return;
        }
        Envelope envelope = new Gson().fromJson(plainMessage, Envelope.class);

        // TODO PROCESS NEW MESSAGE TYPES HERE
        switch (envelope.getType()) {
            case MESSAGETYPE_CONTRACT: {
                handleContractMessage(group, envelope);
                if (_passthroughAll) {
                    handleTextMessage(group, envelope);
                }
                break;
            }
            case MESSAGETYPE_TEXT: {
                handleTextMessage(group, envelope);
                break;
            }
            case MESSAGETYPE_CONTRACT_RECEIPT: {
                handleContractReceipt(group, envelope);
                if (_passthroughAll) {
                    handleTextMessage(group, envelope);
                }
                break;
            }
            default:
                logprint("Unknown message type: " + envelope.getType());
                break;
        }
    }

    /**
     * CALL THIS METHOD WHEN FINISHED WITH THIS INSTANCE AND YOU STILL WANT TO CONTINUE.
     * STOPS ALL THREADS RELATED TO THIS INSTANCE
     */
    public void close() {
        _eventHandler.stopAllThreads();
    }

    /**
     * Return the public key of the current Sawtooth entity.
     * Can be used to regulate permissions on the validator.
     * (Could be denied or allowed)
     *
     * @return public key of the current entity in hex encoding
     */
    public String getSawtoothPublicKey() {
        return _crypto.getSawtoothPublicKey();
    }

    private void handleContractReceipt(String group, Envelope envelope) {
        ContractReceipt receipt;
        try {
            receipt = new Gson().fromJson(envelope.getRawMessage(), ContractReceipt.class);
        } catch (JsonSyntaxException e) {
            logprint("Cannot convert to ContractReceipt: " + envelope.getRawMessage());
            return;
        }
        ContractProcessingCallback cb = _contractCallbacks.get(receipt.getContract().getContractID());
        if (cb != null) {
            cb.processingFinished(receipt);
        }
    }

    private void handleContractMessage(String group, Envelope envelope) {
        Contract contract;
        try {
            contract = new Gson().fromJson(envelope.getRawMessage(), Contract.class);
        } catch (JsonSyntaxException e) {
            logprint("Could not extract contract from envelope: " + envelope.toString());
            return;
        }
        if (_clientID.equals(contract.getRequestedProcessor()) || Contract.REQUESTED_PROCESSOR_ANY.equals(contract.getRequestedProcessor())) {
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
                logprint("No processor found for contract: " + contract.toString());
                return;
            }
            //
            //logprint("Contract processed with result: " + result);

            // Process the result: Build a receipt to send back
            ContractReceipt receipt = new ContractReceipt(_clientID, String.valueOf(result), contract);
            sendReceiptToChain(group, receipt, contract.getResultOutputAddr());
        } else {
            //logprint("Contract was not for this client: " + contract);
        }

    }

    private void handleTextMessage(String group, Envelope envelope) {
        // Send the message to all subscribers of that group
        List<GroupCallback> list = _textmessageCallbacks.get(group);
        if (list != null) {
            //logprint("Callback(s) found for the group...");
            list.forEach(c -> c.newMessageOnChain(group, envelope.getRawMessage(), envelope.getSender()));
        }
    }

    private boolean putCallback(String groupName, GroupCallback callback) {
        //logprint("New subscription for group: " + groupName);
        if (_textmessageCallbacks.containsKey(groupName)) {
            List<GroupCallback> list = _textmessageCallbacks.get(groupName);
            if (list.contains(callback)) {
                //logprint("Subscription skipped, callback already registered.");
                return false;
            } else {
                return list.add(callback);
                //logprint("Subscription completed, callback registered to existing group.");
            }
        } else {
            List<GroupCallback> newList = new ArrayList<>();
            newList.add(callback);
            _textmessageCallbacks.put(groupName, newList);
            return true;
            //logprint("Subscription completed, new group created.");
        }
    }

    private byte[] encryptEnvelope(String group, Envelope envelope) {
        // Create the payload in CSV format
        // The group stays in clearText so clients attempting to decrypt can know if they can without trial and error
        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append(group).append(",");
        // Encrypt the whole message
        try {
            msgBuilder.append(_crypto.encrypt(envelope.toString(), group));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            logprint("Message will not be send.");
            return null;
        } catch (IllegalStateException e) {
            logprint("Trying to encrypt for group for which the key is not present (" + group + "). Message will not be send.");
            return null;
        }
        return msgBuilder.toString().getBytes(UTF_8);
    }

    protected void logprint(String message) {
        System.out.println("[" + _clientID + "]  " + message);
    }

    public void setValidatorURL(String url) {
        _eventHandler.setValidatorURL(url);
    }
}
