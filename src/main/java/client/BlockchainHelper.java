package client;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.processor.Utils;
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

public class BlockchainHelper {

    private String baseRestAPIUrl;
    private Signer signer;
    private HyperZMQ hyperZMQ;
    private boolean printRESTAPIResponse = false;
    private ZContext zContext = new ZContext();
    private ZMQ.Socket submitSocket;

    public static final String KEY_EXCHANGE_RECEIPT_FAMILY = "KeyExchangeReceipt";
    public static final String KEY_EXCHANGE_RECEIPT_NAMESPACE = "ac0cab";
    public static final String CSVSTRINGS_FAMILY = "csvstrings";
    public static final String CSVSTRINGS_NAMESPACE = "2f9d35";

    BlockchainHelper(HyperZMQ hyperZMQ, Signer signer) {
        this.hyperZMQ = hyperZMQ;
        baseRestAPIUrl = ValidatorAddress.REST_URL_DEFAULT;
        this.signer = signer;

        submitSocket = zContext.createSocket(ZMQ.DEALER);
        submitSocket.connect(ValidatorAddress.VALIDATOR_URL_DEFAULT);
    }

    public void setSigner(Signer signer) {
        this.signer = signer;
    }

    public void setBaseRestAPIUrl(String baseRestAPIUrl) {
        this.baseRestAPIUrl = baseRestAPIUrl;
    }

    Transaction buildTransaction(String transactionFamily, String txFamVersion, byte[] payload, String outputAddr) {
        // Create Transaction Header
        if (signer == null) {
            throw new IllegalStateException("No signer for the transaction, returning.");
        }

        // Set in- and outputs to the transaction family namespace to limit their read and write to their own namespaces
        // TODO do a switch here do support more tx families easier
        String input = KEY_EXCHANGE_RECEIPT_NAMESPACE;
        if (transactionFamily.equals(CSVSTRINGS_FAMILY)) {
            input = CSVSTRINGS_NAMESPACE;
        }
        String outputs = outputAddr == null ? input : outputAddr;

        TransactionHeader header = TransactionHeader.newBuilder()
                .setSignerPublicKey(signer.getPublicKey().hex())
                .setFamilyName(transactionFamily)       // Has to be identical in TP
                .setFamilyVersion(txFamVersion)         // Has to be identical in TP
                .addOutputs(outputs)
                .addInputs(input)
                .setPayloadSha512(Utils.hash512(payload))
                .setBatcherPublicKey(signer.getPublicKey().hex())
                .setNonce(UUID.randomUUID().toString())
                .build();

        // Create the Transaction
        String signature = signer.sign(header.toByteArray());

        return Transaction.newBuilder()
                .setHeader(header.toByteString())
                .setPayload(ByteString.copyFrom(payload))
                .setHeaderSignature(signature)
                .build();
    }

    boolean buildAndSendBatch(List<Transaction> transactionList) {
        // Wrap the transactions in a Batch (atomic unit)
        // Create the BatchHeader
        BatchHeader batchHeader = BatchHeader.newBuilder()
                .setSignerPublicKey(signer.getPublicKey().hex())
                .addAllTransactionIds(
                        transactionList
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
                .addAllTransactions(transactionList)
                .setHeaderSignature(batchSignature)
                .build();

        // Encode Batches in BatchList
        // The validator expects a batchlist (which is not atomic)
        byte[] batchListBytes = BatchList.newBuilder()
                .addBatches(batch)
                .build()
                .toByteArray();

        return sendBatchListZMQ(batchListBytes);
    }

    String getStateZMQ(String address) {
        ClientStateGetRequest req = ClientStateGetRequest.newBuilder()
                .clearStateRoot()
                .setAddress(address)
                .build();
        //System.out.println("ClientStateGetRequest: " + req.toString());

        Message message = Message.newBuilder()
                .setMessageType(Message.MessageType.CLIENT_STATE_GET_REQUEST)
                .setContent(req.toByteString())
                .setCorrelationId(EventHandler.CORRELATION_ID)
                .build();
        //System.out.println("Request Message: " + message.toString());

        submitSocket.send(message.toByteArray());
        byte[] bResponse = submitSocket.recv();
        //System.out.println("get state response raw: " + new String(bResponse));
        Message respMessage;
        try {
            respMessage = Message.parseFrom(bResponse);
            // Extract the ClientStateGetResponse
            ClientStateGetResponse csgr = ClientStateGetResponse.parseFrom(respMessage.getContent());
            //System.out.println("csgr: " + csgr.toString());
            return csgr.getValue().toStringUtf8();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return "";
        }
    }

    private boolean sendBatchListZMQ(byte[] body) {
        try {
            ClientBatchSubmitRequest req = ClientBatchSubmitRequest.parseFrom(body);
            //System.out.println("ClientBatchSubmitRequest: " + req.toString());

            Message message = Message.newBuilder()
                    .setMessageType(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST)
                    .setContent(req.toByteString())
                    .setCorrelationId(EventHandler.CORRELATION_ID)
                    .build();

            submitSocket.send(message.toByteArray());

            byte[] bResponse = submitSocket.recv();
            Message respMessage = Message.parseFrom(bResponse);
            //System.out.println("response message: " + respMessage.toString());
            ClientBatchSubmitResponse cbsResp = ClientBatchSubmitResponse.parseFrom(respMessage.getContent());
            //System.out.println("ClientBatchSubmitResponse parsed: " + cbsResp);
            // Check submission status
            boolean success = cbsResp.getStatus() == ClientBatchSubmitResponse.Status.OK;
            System.out.println("Batch submit was " + (success ? "successful" : "not successful"));
            return success;
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean sendBatchListRESTAPI(byte[] body) throws IOException {
        URL url = new URL(baseRestAPIUrl + "/batches");
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
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (response != null && printRESTAPIResponse) {
            hyperZMQ.print(response);
        }
        return true;
    }

    private String sendToRestEndpoint(String restEndpoint, String requestMethod, byte[] payload) throws IOException {
        URL url = new URL(baseRestAPIUrl + restEndpoint);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection) con;
        http.setRequestMethod(requestMethod); // PUT is another valid option
        http.setDoOutput(true);
        http.setRequestProperty("Content-Type", "application/octet-stream");
        http.connect();

        if (payload != null) {
            try (OutputStream os = http.getOutputStream()) {
                os.write(payload);
            }
        }

        String response;

        try (InputStream is = http.getInputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            Stream<String> lines = br.lines();
            response = lines.reduce("", (accu, s) -> accu += s);
        } catch (FileNotFoundException e) {
            System.out.println("Address did not match any resource");
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if (response != null && printRESTAPIResponse) {
            hyperZMQ.print(response);
        }
        return response;
    }

    public String queryStateAddress(String addr) throws IOException {
        String resp = sendToRestEndpoint(("/state/" + addr), "GET", null);
        if (resp != null) {
            JSONObject o = new JSONObject(resp);
            try {
                return o.getString("data");
            } catch (JSONException e) {
                System.out.println("Field 'data' not found in response " + resp);
                return null;
            }
        } else {
            return null;
        }
    }

    public void setRestAPIUrl(String url) {
        baseRestAPIUrl = url;
        if (baseRestAPIUrl.endsWith("/")) {
            baseRestAPIUrl = baseRestAPIUrl.substring(0, baseRestAPIUrl.length() - 2);
        }
    }
}
