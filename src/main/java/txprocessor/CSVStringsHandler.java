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

        //print("Inputs: " + header.getInputsList().stream().reduce("", (a, c) -> a += c + ", "));

        // An address is a hex-encoded 70 character string representing 35 bytes
        // The address format contains a 3 byte (6 hex character) namespace prefix
        // The rest of the address format is up to the implementation

        String address;

        String output = header.getOutputs(0);
        if (namespace.equals(output)) {
            // Wildcard output, calculate an address in the namespace
            // Use the message bytes as identifier for the remaining bytes
            String hashedMsg = Utils.hash512(message.getBytes(UTF_8));
            address = namespace + hashedMsg.substring(hashedMsg.length() - 64);
            //print("Address calculated as: " + address + "  (size=" + address.length() + ")");
        } else {
            // Concrete output, use that
            //String allOutputs = header.getOutputsList().stream().reduce("", (a, c) -> a += c + ", ");
            //print("All Outputs: " + allOutputs);
            address = output;
        }
        //print("Using address: " + address);

        //checkStateAtAddress(address, context); // optional

        // Prepare the message to be set
        // Has the same format has the input: <group>,<encrypted message> -> forward the payload
        // The data is given as ByteString and stores in Base64 (Sawtooth specification)
        ByteString writeToState = tpProcessRequest.getPayload();
        Map.Entry<String, ByteString> entry = new AbstractMap.SimpleEntry<>(address, writeToState);
        Collection<Map.Entry<String, ByteString>> addressValues = Arrays.asList(entry);
        Collection<String> returnedAddresses = context.setState(addressValues);

        // Check if successful
        if (returnedAddresses.isEmpty()) {
            throw new InvalidTransactionException("Set state error");
        }
        //print("Returned Addresses from setting new value:");
        //returnedAddresses.forEach(this::print);


        // Fire event with the message //////////////////////////////////////
        // TODO which things to add to the event
        //Map.Entry<String, String> e = new AbstractMap.SimpleEntry<>("signerPublicKey", header.getSignerPublicKey());
        //Collection<Map.Entry<String, String>> collection = Arrays.asList(e);
        //String signerPub = header.getSignerPublicKey();
        //print("signer Public key: " + signerPub);

        Map.Entry<String, String> e = new AbstractMap.SimpleEntry<>("address", address);
        Collection<Map.Entry<String, String>> collection = Arrays.asList(e);
        try {
            context.addEvent(group, collection, tpProcessRequest.getPayload());
        } catch (InternalError internalError) {
            internalError.printStackTrace();
        }
        /////////////////////////////////////////////////////////////////////
    }

    private void checkStateAtAddress(String address, Context context) {
        Collection<String> checkAddr = new ArrayList<>();
        checkAddr.add(address);
        Map<String, ByteString> alreadySetValues = null;
        try {
            alreadySetValues = context.getState(checkAddr);
        } catch (InternalError internalError) {
            internalError.printStackTrace();
        } catch (InvalidTransactionException e) {
            e.printStackTrace();
        }
        if (alreadySetValues != null) {
            alreadySetValues.forEach((key, value) ->
                    print("Values at address before: "
                            + key + "=" + value.toString(UTF_8)));
            print("... will be overwritten.");
        } else {
            print("address is empty.");
        }
    }

    void print(String message) {
        System.out.println("[TP]  " + message);
    }
}
