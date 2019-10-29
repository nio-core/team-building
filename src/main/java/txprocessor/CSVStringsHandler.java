package txprocessor;

import com.google.protobuf.ByteString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;

import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CSVStringsHandler implements TransactionHandler {
    private String namespace;

    CSVStringsHandler() {
        // Convention
        namespace = Utils.hash512(transactionFamilyName().getBytes(UTF_8)).substring(0, 6);
        print("Starting TransactionProcessor with namespace '" + namespace + "'");
    }

    @Override
    public String transactionFamilyName() {
        return "csvstrings";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public Collection<String> getNameSpaces() {
        ArrayList<String> namespaces = new ArrayList<>();
        namespaces.add(namespace);
        return namespaces;
    }

    /**
     * A TRANSACTION CAN BE SENT TO THE TRANSACTION PROCESSOR MULTIPLE TIMES.
     * IT IS IMPORTANT THAT THE APPLY METHOD IS IDEMPOTENT!!!!
     * I.E. THE ADDRESS SHOULD ONLY RELY ON DATA FROM THE PAYLOAD/HEADER.
     *
     * @param tpProcessRequest tpProcessRequest
     * @param context          context
     * @throws InvalidTransactionException InvalidTransactionException
     * @throws InternalError               InternalError
     */
    @Override
    public void apply(TpProcessRequest tpProcessRequest, Context context) throws InvalidTransactionException, InternalError {
        // Decode the payload which is a CSV String in UTF-8
        if (tpProcessRequest.getPayload().isEmpty()) {
            throw new InvalidTransactionException("Payload is empty!");
        }

        String payloadStr = tpProcessRequest.getPayload().toString(UTF_8);
        //print("Got payload: " + payloadStr);

        TransactionHeader header = tpProcessRequest.getHeader();
        // TODO do something with in/outputs?
        //log.info("Inputs: " + header.getInputsList().stream().reduce("", (a, c) -> a += c));
        //log.info("Outputs: " + header.getOutputsList().stream().reduce("", (a, c) -> a += c));

        // Check payload integrity
        String receivedHash = Utils.hash512(tpProcessRequest.getPayload().toByteArray());
        if (!header.getPayloadSha512().equals(receivedHash)) {
            throw new InvalidTransactionException("Payload or Header is corrupted!");
        }

        String[] strings = payloadStr.split(",");

        if (strings.length < 2) {
            throw new InvalidTransactionException("Not enough values!");
        }

        // The order in the CSV String is <group>,<encrypted message>
        String group = strings[0];
        String message = strings[1];
        // An address is a hex-encoded 70 character string representing 35 bytes
        // The address format contains a 3 byte (6 hex character) namespace prefix
        // The rest of the address format is up to the implementation

        // for now just use the message as identifier for the remaining bytes
        String hashedMsg = Utils.hash512(message.getBytes(UTF_8));
        String address = namespace + hashedMsg.substring(hashedMsg.length() - 64);
        //log.info("Address calculated as: " + address + "  (size=" + address.length() + ")");

        // Fire event with the message //////////////////////////////////////
        //print("firing event..."); // TODO
        Map.Entry<String, String> e = new AbstractMap.SimpleEntry<>("address", address);
        Collection<Map.Entry<String, String>> collection = Arrays.asList(e);
        //  addEvent(String identifier, collection<attributes>, data bytestring)
        //context.addEvent("myEvent", collection, tpProcessRequest.getPayload());
        context.addEvent(group, collection, tpProcessRequest.getPayload());
        /////////////////////////////////////////////////////////////////////
        /*
        // Check the state at that address
        Collection<String> checkAddr = new ArrayList<>();
        checkAddr.add(address);
        //log.info("Checking state at address " + checkAddr.toString());
        Map<String, ByteString> alreadySetValues = context.getState(checkAddr);
        if (alreadySetValues != null) {
            alreadySetValues.forEach((key, value) ->
                    log.info("Values at address before: "
                    + key + "=" + value.toString(UTF_8)));
            log.info("WILL BE OVERWRITTEN");
        } else {
            log.info("address is empty.");
        }
        */
        // Prepare the message to be set
        Map.Entry<String, ByteString> entry = new AbstractMap.SimpleEntry<>(address,
                ByteString.copyFrom(message.getBytes(UTF_8)));
        //log.info("preparing entry to set: " + entry.toString());
        Collection<Map.Entry<String, ByteString>> addressValues = Arrays.asList(entry);
        //log.info("collection to set: " + addressValues.toString());
        Collection<String> returnedAddresses = context.setState(addressValues);

        // Check if successful
        if (returnedAddresses.isEmpty()) {
            throw new InvalidTransactionException("Set state error");
        }
      /*  log.info("Returned Addresses from setting new value:");
        returnedAddresses.forEach(log::info);*/
    }

    void print(String message) {
        System.out.println("[TP]  " + message);
    }
}
