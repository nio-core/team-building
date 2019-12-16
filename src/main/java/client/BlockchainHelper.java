package client;

import com.google.protobuf.ByteString;
import org.json.JSONException;
import org.json.JSONObject;
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
    private static final String DEFAULT_REST_URL = "http://localhost:8008";
    private String _baseRestAPIUrl;
    private Signer _signer;
    private HyperZMQ _hyperzmq;
    private boolean _printRESTAPIResponse = false;

    BlockchainHelper(HyperZMQ hyperZMQ, Signer signer, String restAPIUrl) {
        _hyperzmq = hyperZMQ;
        _signer = signer;
        _baseRestAPIUrl = restAPIUrl;
    }

    BlockchainHelper(HyperZMQ hyperZMQ, Signer signer) {
        _hyperzmq = hyperZMQ;
        _baseRestAPIUrl = DEFAULT_REST_URL;
        _signer = signer;
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

        try {
            return sendBatchList(batchListBytes);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean sendBatchList(byte[] body) throws IOException {
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
        }
        catch (IOException e) {
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
        if (resp != null){
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
