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

class BlockchainHelper {
    private static final String DEFAULT_REST_URL = "http://192.168.178.124:8008";
    private String _baseRestAPIUrl;
    private Signer _signer;
    private HyperZMQ _hyperzmq;
    private boolean _printRESTAPIResponse = false;

    private ZContext _zctx = new ZContext();
    private ZMQ.Socket _submitSocket;

    BlockchainHelper(HyperZMQ hyperZMQ, Signer signer) {
        _hyperzmq = hyperZMQ;
        _baseRestAPIUrl = DEFAULT_REST_URL;
        _signer = signer;

        _submitSocket = _zctx.createSocket(ZMQ.DEALER);
        _submitSocket.connect(EventHandler.DEFAULT_VALIDATOR_URL);
    }

    public void set_baseRestAPIUrl(String _baseRestAPIUrl) {
        this._baseRestAPIUrl = _baseRestAPIUrl;
    }

    Transaction buildTransaction(byte[] payload) {
        return buildTransaction(payload, null);
    }

    Transaction buildTransaction(byte[] payload, String outputAddr) {
        // Create Transaction Header
        if (_signer == null) {
            throw new IllegalStateException("No signer for the transaction, returning.");
        }
        String outputs = (outputAddr == null) ? HyperZMQ.CSVSTRINGS_NAMESPACE_PREFIX : outputAddr;

        TransactionHeader header = TransactionHeader.newBuilder()
                .setSignerPublicKey(_signer.getPublicKey().hex())
                .setFamilyName("csvstrings") // Has to be identical in TP
                .setFamilyVersion("0.1")        // Has to be identical in TP
                // TODO setting in/outputs increases security as it limits the read/write of the transaction processor
                .addOutputs(outputs) // Set output as wildcard to our namespace
                .addInputs("2f9d35")
                .setPayloadSha512(Utils.hash512(payload))
                .setBatcherPublicKey(_signer.getPublicKey().hex())
                .setNonce(UUID.randomUUID().toString())
                .build();

        // Create the Transaction
        String signature = _signer.sign(header.toByteArray());

        Transaction transaction = Transaction.newBuilder()
                .setHeader(header.toByteString())
                .setPayload(ByteString.copyFrom(payload))
                .setHeaderSignature(signature)
                .build();
        return transaction;
    }

    boolean buildAndSendBatch(List<Transaction> transactionList) {
        // Wrap the transactions in a Batch (atomic unit)
        // Create the BatchHeader
        BatchHeader batchHeader = BatchHeader.newBuilder()
                .setSignerPublicKey(_signer.getPublicKey().hex())
                .addAllTransactionIds(
                        transactionList
                                .stream()
                                .map(Transaction::getHeaderSignature)
                                .collect(Collectors.toList())
                )
                .build();

        // Create the Batch
        // The signature of the batch acts as the Batch's ID
        String batchSignature = _signer.sign(batchHeader.toByteArray());
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

    private boolean sendBatchListZMQ(byte[] body) {
        try {
            ClientBatchSubmitRequest req = ClientBatchSubmitRequest.parseFrom(body);
            System.out.println("ClientBatchSubmitRequest: " + req.toString());

            Message message = Message.newBuilder()
                    .setMessageType(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST)
                    .setContent(req.toByteString())
                    .setCorrelationId(EventHandler.CORRELATION_ID)
                    .build();

            _submitSocket.send(message.toByteArray());
            // Reponse is not very interesting
            //byte[] bResponse = _submitSocket.recv();
            //ClientBatchSubmitResponse cbsResp = ClientBatchSubmitResponse.parseFrom(bResponse);
            //System.out.println("ClientBatchSubmitResponse: " + cbsResp);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean sendBatchListRESTAPI(byte[] body) throws IOException {
        URL url = new URL(_baseRestAPIUrl + "/batches");
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

        if (response != null && _printRESTAPIResponse) {
            _hyperzmq.logprint(response);
        }
        return true;
    }

    private String sendToRestEndpoint(String restEndpoint, String requestMethod, byte[] payload) throws IOException {
        URL url = new URL(_baseRestAPIUrl + restEndpoint);
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

        if (response != null && _printRESTAPIResponse) {
            _hyperzmq.logprint(response);
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
        _baseRestAPIUrl = url;
        if (_baseRestAPIUrl.endsWith("/")) {
            _baseRestAPIUrl = _baseRestAPIUrl.substring(0, _baseRestAPIUrl.length() - 2);
        }
    }
}
