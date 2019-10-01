import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.Secp256k1Context;
import sawtooth.sdk.signing.Signer;
import sawtooth.sdk.protobuf.Message;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static sawtooth.sdk.processor.Utils.hash512;

public class HyperZMQ {
    private Logger log = Logger.getLogger(HyperZMQ.class.getName());

    private Secp256k1Context context = new Secp256k1Context();
    private PrivateKey privateKey = context.newRandomPrivateKey();
    private Signer signer = new Signer(context, privateKey);

    private List<SubscriptionCallback> subscribers;

    private boolean runEventReceiver = true;

    public HyperZMQ() {
    }

    public void subscribe(/*SubscriptionCallback s*/) {
        //subscribers.add(s);
        log.info("Subscribing...");
        EventFilter eventFilter = EventFilter.newBuilder()
                .setKey("address")
                .setMatchString("2f9d35*")
                .setFilterType(EventFilter.FilterType.REGEX_ANY)
                .build();

        EventSubscription subscription = EventSubscription.newBuilder()
                //.setEventType("sawtooth/state-delta")
                .setEventType("myEvent")
                .addFilters(eventFilter)
                .build();

        ZContext ctx = new ZContext();
        ZMQ.Socket socket = ctx.createSocket(ZMQ.DEALER);
        socket.connect("tcp://localhost:4004");

        ClientEventsSubscribeRequest request = ClientEventsSubscribeRequest.newBuilder()
                .addSubscriptions(subscription)
                .build();

        Message message = Message.newBuilder()
                .setCorrelationId("123")
                .setMessageType(Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST)
                .setContent(request.toByteString())
                .build();

        log.info("Sending subscription request...");
        socket.send(message.toByteArray());

        byte[] responseBytes = socket.recv();
        Message respMsg = null;
        try {
            respMsg = Message.parseFrom(responseBytes);
            log.info("Response deserialized: " + respMsg.toString());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (respMsg == null || respMsg.getMessageType() != Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_RESPONSE) {
            log.info("Response was no subscription response");
            return;
        }
        ClientEventsSubscribeResponse cesr = null;
        try {
            cesr = ClientEventsSubscribeResponse.parseFrom(respMsg.getContent());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (cesr == null) {
            log.info("cesr null");
            return;
        }
        if (cesr.getStatus() != ClientEventsSubscribeResponse.Status.OK) {
            log.info("Subscribing failed: " + cesr.getResponseMessage());
            return;
        }

        // Events are sent to the same socket
        Thread t = new Thread(() -> {
            receiveEvents(socket);
        });
        t.start();

    }

    public void receiveEvents(ZMQ.Socket socket) {
        log.info("Starting to listen to events...");
        while (runEventReceiver) {
            byte[] recv = socket.recv();
            try {
                Message msg = Message.parseFrom(recv);
                if (msg.getMessageType() != Message.MessageType.CLIENT_EVENTS) {
                    log.info("received event message is not of type event!");
                }

                EventList list = EventList.parseFrom(msg.getContent());
                for (Event e : list.getEventsList()) {
                    log.info("[Event] received deserialized: " + e.toString());
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendTransaction() {
        // Create the payload in CSV format

        byte[] payloadBytes = "123121,BETTERMESSAGE".getBytes(StandardCharsets.UTF_8);

        // Create Transaction Header
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
        log.info("Sending batchlist to http://localhost:8008/batches");
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
            log.info(response);
        }
    }
}
