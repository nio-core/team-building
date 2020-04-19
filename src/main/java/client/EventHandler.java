package client;

import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.protobuf.*;
import sawtooth.sdk.protobuf.Message.MessageType;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class EventHandler implements AutoCloseable {
    private final HyperZMQ hyperzmq;
    static final String CORRELATION_ID = "123";

    static final String DEFAULT_VALIDATOR_URL = "tcp://127.0.0.1:4004";
    private String validatorURL = "";
    private final ZMQ.Socket socket;
    private final AtomicBoolean runListenerLoop = new AtomicBoolean(true);
    private final List<Message> subscriptionQueue = new ArrayList<>(); // Used by two threads
    private int receiveTimeoutMS = 300;
    private final ZContext context;

    EventHandler(HyperZMQ callback) {
        this.hyperzmq = callback;
        context = new ZContext();
        this.socket = context.createSocket(ZMQ.DEALER);
        //this.sendSocket = context.createSocket(ZMQ.DEALER);
        this.socket.setReceiveTimeOut(receiveTimeoutMS);
        startListenerLoop();
    }

    private void startListenerLoop() {
        socket.connect(getValidatorURL());
        // The loop consists of the socket receiving with a timeout.
        // After each receive, the queue is checked whether there are messages to send
        Thread t = new Thread(() -> {
            while (runListenerLoop.get()) {
                // If something is in the queue, send that message
                Message messageToSent = null;
                synchronized (subscriptionQueue) {
                    if (!subscriptionQueue.isEmpty()) {
                        messageToSent = subscriptionQueue.remove(0);
                    }
                }
                if (messageToSent != null) {
                    // The message is already protobuf, ready to be sent
                    socket.send(messageToSent.toByteArray());
                    //print("Sent message:" + messageToSent.toString());
                }

                // Try to receive a message
                byte[] recv = socket.recv();
                if (recv != null) {
                    try {
                        Message messageReceived = Message.parseFrom(recv);
                        if (messageReceived != null) {
                            switch (messageReceived.getMessageType()) {
                                case CLIENT_EVENTS: {
                                    EventList list = EventList.parseFrom(messageReceived.getContent());
                                    for (Event e : list.getEventsList()) {
                                        String received = e.toString();
                                        //print("Received Event: " + received);

                                        String fullMessage = received.substring(received.indexOf("data"));
                                        //print("fullMessage: " + fullMessage);

                                        String csvMessage = fullMessage.substring(7, fullMessage.length() - 2); //TODO
                                        //print("csvMessage: " + csvMessage);

                                        String[] parts = csvMessage.split(",");
                                        if (parts.length < 2) {
                                            print("Malformed event payload: " + csvMessage);
                                            return;
                                        }
                                        String group = parts[0];
                                        String encMessage = parts[1];
                                        //print("Group: " + group);
                                        //print("Encrypted Message: " + encMessage);

                                        hyperzmq.newEventReceived(group, encMessage);
                                    }
                                    break;
                                }
                                case CLIENT_EVENTS_SUBSCRIBE_RESPONSE: {
                                    // Check for subscription success
                                    try {
                                        ClientEventsSubscribeResponse cesr = ClientEventsSubscribeResponse.parseFrom(messageReceived.getContent());
                                        print("Subscription was " + (cesr.getStatus() == ClientEventsSubscribeResponse.Status.OK ?
                                                "successful" : "unsuccessful"));
                                    } catch (InvalidProtocolBufferException e) {
                                        e.printStackTrace();
                                    }
                                    break;
                                }
                                default: {
                                    print("Received message has unknown type: " + messageReceived.toString());
                                    break;
                                }
                            }
                        }
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                }
            }
            // End while
            socket.close();
        });
        t.start();
    }

    public void subscribeToGroup(String groupName) {
        EventFilter eventFilter = EventFilter.newBuilder()
                .setFilterType(EventFilter.FilterType.REGEX_ANY)
                .setKey("address")
                .setMatchString(BlockchainHelper.CSVSTRINGS_NAMESPACE + "*")
                .build();
        queueNewSubscription(groupName, eventFilter);
    }

    private void queueNewSubscription(String eventName, EventFilter eventFilter) {
        // Build a subscription message ready to be sent which will be queued
        EventSubscription eventSubscription = EventSubscription.newBuilder()
                .addFilters(eventFilter)
                .setEventType(eventName)
                .build();

        ClientEventsSubscribeRequest request = ClientEventsSubscribeRequest.newBuilder()
                .addSubscriptions(eventSubscription)
                .build();

        Message message = Message.newBuilder()
                .setCorrelationId(CORRELATION_ID)
                .setMessageType(MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST)
                .setContent(request.toByteString())
                .build();

        synchronized (subscriptionQueue) {
            subscriptionQueue.add(message);
        }
    }

    private String getValidatorURL() {
        return validatorURL.isEmpty() ? DEFAULT_VALIDATOR_URL : validatorURL;
    }

    private void print(String msg) {
        System.out.println("[EventHandler " + hyperzmq.getClientID() + "] " + msg);
    }

    public void setValidatorURL(String validatorURL) {
        this.validatorURL = validatorURL;
    }

    @Override
    public void close() throws Exception {
        runListenerLoop.set(false);
        socket.close();
        context.close();
    }
}

