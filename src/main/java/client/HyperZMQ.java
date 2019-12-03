package client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import contracts.Contract;
import contracts.ContractProcessor;
import contracts.ContractReceipt;
import sawtooth.sdk.protobuf.Transaction;

import java.security.GeneralSecurityException;
import java.util.*;

import static client.Envelope.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HyperZMQ {

    private EventHandler _eventHandler;
    private Crypto _crypto;
    private String _clientID;
    private List<ContractProcessor> _contractProcessors = new ArrayList<>();
    private Map<String, List<SubscriptionCallback>> _textmessageCallbacks = new HashMap<>();
    private Map<String, ContractProcessingCallback> _contractCallbacks = new HashMap<>(); // key is the contractID
    private BlockchainHelper _blockchainHelper;

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
        return sendSingleEnvelope(groupName, envelope);
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

    private boolean sendSingleEnvelope(String group, Envelope envelope) {
        byte[] payloadBytes = encryptEnvelope(group, envelope);
        List<Transaction> transactionList = Collections.singletonList(_blockchainHelper.buildTransaction(payloadBytes));
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

    public boolean sendContractToChain(String groupName, Contract contract) {
        if (groupName == null || contract == null) {
            logprint("Empty group and/or contract!");
            return false;
        }
        // Wrap the contract - the complete envelope will be encrypted
        Envelope envelope = new Envelope(_clientID, MESSAGETYPE_CONTRACT, contract.toString());
        return sendSingleEnvelope(groupName, envelope);
    }

    private boolean sendReceiptToChain(String groupName, ContractReceipt receipt) {
        if (groupName == null || receipt == null) {
            logprint("Empty group and/or receipt!");
            return false;
        }

        Envelope envelope = new Envelope(_clientID, MESSAGETYPE_CONTRACT_RECEIPT, receipt.toString());
        return sendSingleEnvelope(groupName, envelope);
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
    public void createGroup(String groupName, SubscriptionCallback callback) {
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
    public void addGroup(String groupName, String key, SubscriptionCallback callback) {
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
    public boolean addCallbackToGroup(String groupName, SubscriptionCallback callback) {
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
    void newEventReceived(String group, String encryptedMessage) {
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
                logprint("Unknown message type: " + envelope.getType());
                break;
        }
    }

    /**
     * IT'S IMPORTANT TO CALL THIS METHOD WHEN FINISHED WITH THIS INSTANCE AND YOU STILL WANT TO CONTINUE.
     * STOPS ALL THREADS RELATED TO THIS INSTANCE
     */
    public void close() {
        _eventHandler.stopAllThreads();
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
            sendReceiptToChain(group, receipt);
        } else {
            //logprint("Contract was not for this client: " + contract);
        }

    }

    private void handleTextMessage(String group, Envelope envelope) {
        // Send the message to all subscribers of that group
        List<SubscriptionCallback> list = _textmessageCallbacks.get(group);
        if (list != null) {
            //logprint("Callback(s) found for the group...");
            list.forEach(c -> c.newMessageOnChain(group, envelope.getRawMessage(), envelope.getSender()));
        }
    }

    private boolean putCallback(String groupName, SubscriptionCallback callback) {
        //logprint("New subscription for group: " + groupName);
        if (_textmessageCallbacks.containsKey(groupName)) {
            List<SubscriptionCallback> list = _textmessageCallbacks.get(groupName);
            if (list.contains(callback)) {
                //logprint("Subscription skipped, callback already registered.");
                return false;
            } else {
                return list.add(callback);
                //logprint("Subscription completed, callback registered to existing group.");
            }
        } else {
            List<SubscriptionCallback> newList = new ArrayList<>();
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
