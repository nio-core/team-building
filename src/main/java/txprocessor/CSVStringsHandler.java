package txprocessor;

import client.SawtoothUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.ByteString;
import joingroup.JoinGroupRequest;
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
    private final String namespace = "2f9d35";

    CSVStringsHandler() {
        // Convention
        //namespace = SawtoothUtils.hash(transactionFamilyName()).substring(0, 6);
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
        // Decode the payload which is a CSV String or JoinGroupRequest in UTF-8
        if (tpProcessRequest.getPayload().isEmpty()) {
            throw new InvalidTransactionException("Payload is empty!");
        }

        String payloadStr = tpProcessRequest.getPayload().toString(UTF_8);
        print("Got payload: " + payloadStr);

        TransactionHeader header = tpProcessRequest.getHeader();

        // Check payload integrity
        //String receivedHash = Utils.hash512(tpProcessRequest.getPayload().toByteArray());
        String receivedHash = SawtoothUtils.hash(tpProcessRequest.getPayload().toString(UTF_8));
        if (!header.getPayloadSha512().equals(receivedHash)) {
            throw new InvalidTransactionException("Payload or Header is corrupted!");
        }

        // Check if the payload is a JoinRequest
        try {
            JoinGroupRequest request = new Gson().fromJson(payloadStr, JoinGroupRequest.class);
            //handleJoinRequest(tpProcessRequest, context, request);
            print("handling JoinRequest...");

            // First check if the applicant submitted the request
            //TODO
            if (request.getApplicantPublicKey().equals(tpProcessRequest.getHeader().getSignerPublicKey())) {
                print("JoinRequest: public keys match");
            }

            print("broadcasting JoinRequest");
            // Broadcast the the request via event subsystem
            Map.Entry<String, String> e = new AbstractMap.SimpleEntry<>("address", namespace);
            Collection<Map.Entry<String, String>> collection = Arrays.asList(e);
            try {
                context.addEvent(request.getGroupName(), collection, tpProcessRequest.getPayload());
            } catch (InternalError internalError) {
                internalError.printStackTrace();
            }

            // Nothing else to do?
            return;
        } catch (JsonSyntaxException ignored) {
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
            address = SawtoothUtils.namespaceHashAddress(namespace, message);
            //print("Address calculated as: " + address + "  (size=" + address.length() + ")");
        } else {
            // Concrete output, use that
            //String allOutputs = header.getOutputsList().stream().reduce("", (a, c) -> a += c + ", ");
            //print("All Outputs: " + allOutputs);
            address = output;
        }
        //print("Using address: " + address);

        // Prepare the message to be set
        // Has the same format has the input: <group>,<encrypted message> -> forward the payload
        // The data is given as ByteString and stores in Base64 (Sawtooth specification)
        ByteString writeToState = tpProcessRequest.getPayload();
        if (!TPUtils.writeToAddress(writeToState.toStringUtf8(), address, context)) {
            throw new InvalidTransactionException("Set state error");
        }

        //String signerPub = header.getSignerPublicKey();
        //print("signer Public key: " + signerPub);

        // Fire event with the message
        print("firing event...");
        Map.Entry<String, String> e = new AbstractMap.SimpleEntry<>("address", address);
        Collection<Map.Entry<String, String>> collection = Arrays.asList(e);
        try {
            context.addEvent(group, collection, tpProcessRequest.getPayload());
            //print("Event triggered");
        } catch (InternalError internalError) {
            internalError.printStackTrace();
        }
    }

    private void handleJoinRequest(TpProcessRequest tpProcessRequest, Context context, JoinGroupRequest request) {

    }

    void print(String message) {
        System.out.println("[TP]  " + message);
    }
}
