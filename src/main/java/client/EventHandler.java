package client;

import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.protobuf.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static client.HyperZMQ.CSVSTRINGS_NAMESPACE_PREFIX;

class EventHandler {
    private final HyperZMQ _hyperzmq;
    private boolean _runEventReceiver = true;
    static final String CORRELATION_ID = "123";

    //TODO: Adding a identifier(groupName) for which event the subscription is and to cancel them individually
    private Map<Thread, AtomicBoolean> _subscriptionListeners = new HashMap<>();
    static final String DEFAULT_VALIDATOR_URL = "tcp://192.168.178.124:4004";
    private String validatorURL = "";

    EventHandler(HyperZMQ callback) {
        this._hyperzmq = callback;
//        ZMQ.Socket blockIDSocket = subscribe(EventFilter.FilterType.REGEX_ANY, "sawtooth/block-commit", "tcp://localhost:4004");
//        if (blockIDSocket != null) {
//            receiveCommitEvents(blockIDSocket);
//        } else {
//            _hyperzmq.logprint("Could not create socket to listen to 'block-commit'");
//        }
        //System.out.println("EventHandler main thread id: " + Thread.currentThread().getId());
    }

    void subscribeToGroup(String groupName) {
        //ZMQ.Socket socket = subscribe(EventFilter.FilterType.REGEX_ANY, groupName, DEFAULT_VALIDATOR_URL);
        //if (socket != null) {
        _hyperzmq.logprint("Subscribing to group: " + groupName);
        receiveGroupEvents(groupName);
        //} else {
        //  _hyperzmq.logprint("Could not open subscription socket for group: " + groupName);
        //}
    }

    private void receiveCommitEvents(ZMQ.Socket socket) {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);
        Thread t = new Thread(() -> {
            while (atomicBoolean.get()) {
                //System.out.println("Starting to listen to sawtooth/block-commit...");
                byte[] recv = socket.recv();
                try {
                    Message msg = Message.parseFrom(recv);
                    if (msg.getMessageType() != Message.MessageType.CLIENT_EVENTS) {
                        _hyperzmq.logprint("received event message is not of type event!");
                    }
                    //System.out.println("Message: " + msg.toString());
                    EventList list = EventList.parseFrom(msg.getContent());
                    for (Event e : list.getEventsList()) {
                        String received = e.toString();
                        _hyperzmq.logprint("[sawtooth/block-commit] " + received);
                    }
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }
        });
        _subscriptionListeners.put(t, atomicBoolean);
        t.start();
    }

    private ZMQ.Socket subscribe(EventFilter.FilterType filterType, String eventType, String validatorURL) {
        //_hyperzmq.logprint("Subscribing...");
        EventFilter eventFilter = EventFilter.newBuilder()
                .setKey("address")
                .setMatchString("2f9d35*")
                .setFilterType(filterType)
                .build();

        EventSubscription subscription = EventSubscription.newBuilder()
                .setEventType(eventType)
                .addFilters(eventFilter)
                .build();

        ZContext ctx = new ZContext();
        ZMQ.Socket socket = ctx.createSocket(ZMQ.DEALER);
        socket.connect(validatorURL);

        ClientEventsSubscribeRequest request = ClientEventsSubscribeRequest.newBuilder()
                .addSubscriptions(subscription)  //TODO add last known block ids
                //.addLastKnownBlockIds("")
                .build();

        sawtooth.sdk.protobuf.Message message = sawtooth.sdk.protobuf.Message.newBuilder()
                .setCorrelationId(CORRELATION_ID)
                .setMessageType(sawtooth.sdk.protobuf.Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST)
                .setContent(request.toByteString())
                .build();

        //_hyperzmq.logprint("Sending subscription request...");
        socket.send(message.toByteArray());

        byte[] responseBytes = socket.recv();
        sawtooth.sdk.protobuf.Message respMsg = null;
        try {
            respMsg = sawtooth.sdk.protobuf.Message.parseFrom(responseBytes);
            //_hyperzmq.logprint("Response deserialized: " + respMsg.toString());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (respMsg == null || respMsg.getMessageType() != sawtooth.sdk.protobuf.Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_RESPONSE) {
            _hyperzmq.logprint("Response was no subscription response");
            return null;
        }
        ClientEventsSubscribeResponse cesr = null;
        try {
            cesr = ClientEventsSubscribeResponse.parseFrom(respMsg.getContent());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (cesr == null) {
            _hyperzmq.logprint("ClientEventsSubscribeResponse is null");
            return null;
        }
        if (cesr.getStatus() != ClientEventsSubscribeResponse.Status.OK) {
            _hyperzmq.logprint("Subscribing failed: " + cesr.getResponseMessage());
            return null;
        }
        return socket;
    }

    /**
     * Receive events and call the callback for all groups subscribed
     */
    private void receiveGroupEvents(String groupName) {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);
        // Events are sent to the same socket
        Thread t = new Thread(() -> {
            ZContext _ctx = new ZContext();
            ZMQ.Socket socket = _ctx.createSocket(ZMQ.DEALER);

            //_hyperzmq.logprint("Subscribing...");
            EventFilter eventFilter = EventFilter.newBuilder()
                    .setKey("address")
                    .setMatchString(CSVSTRINGS_NAMESPACE_PREFIX + "*")
                    .setFilterType(EventFilter.FilterType.REGEX_ANY)
                    .build();

            EventSubscription subscription = EventSubscription.newBuilder()
                    .setEventType(groupName)
                    .addFilters(eventFilter)
                    .build();

            socket.connect((validatorURL.isEmpty() ? DEFAULT_VALIDATOR_URL : validatorURL));

            ClientEventsSubscribeRequest request = ClientEventsSubscribeRequest.newBuilder()
                    .addSubscriptions(subscription)  //TODO add last known block ids
                    //.addLastKnownBlockIds("")
                    .build();

            sawtooth.sdk.protobuf.Message message = sawtooth.sdk.protobuf.Message.newBuilder()
                    .setCorrelationId(CORRELATION_ID)
                    .setMessageType(sawtooth.sdk.protobuf.Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST)
                    .setContent(request.toByteString())
                    .build();

            //_hyperzmq.logprint("Sending subscription request...");
            socket.send(message.toByteArray());

            byte[] responseBytes = socket.recv();
            sawtooth.sdk.protobuf.Message respMsg = null;
            try {
                respMsg = sawtooth.sdk.protobuf.Message.parseFrom(responseBytes);
                //_hyperzmq.logprint("Response deserialized: " + respMsg.toString());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            if (respMsg == null ||
                    respMsg.getMessageType() != sawtooth.sdk.protobuf.Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_RESPONSE) {
                _hyperzmq.logprint("Response was no subscription response");
                return;
            }
            ClientEventsSubscribeResponse cesr = null;
            try {
                cesr = ClientEventsSubscribeResponse.parseFrom(respMsg.getContent());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            if (cesr == null) {
                _hyperzmq.logprint("ClientEventsSubscribeResponse is null");
                return;
            }
            if (cesr.getStatus() != ClientEventsSubscribeResponse.Status.OK) {
                _hyperzmq.logprint("Subscribing failed: " + cesr.getResponseMessage());
                return;
            }

            while (atomicBoolean.get()) {
                //_hyperzmq.logprint("Starting to listen to events...");
                byte[] recv = socket.recv();
                try {
                    Message msg = Message.parseFrom(recv);
                    if (msg.getMessageType() != Message.MessageType.CLIENT_EVENTS) {
                        _hyperzmq.logprint("received event message is not of type event!");
                        _hyperzmq.logprint("message: " + msg.toString());
                    }

                    EventList list = EventList.parseFrom(msg.getContent());
                    for (Event e : list.getEventsList()) {
                        String received = e.toString();
                        //_hyperzmq.logprint("[Event] received deserialized: " + received);

                        String fullMessage = received.substring(received.indexOf("data"));
                        //_hyperzmq.logprint("fullMessage: " + fullMessage);

                        String csvMessage = fullMessage.substring(7, fullMessage.length() - 2); //TODO
                        //_hyperzmq.logprint("csvMessage: " + csvMessage);

                        String[] parts = csvMessage.split(",");
                        if (parts.length < 2) {
                            _hyperzmq.logprint("Malformed event payload: " + csvMessage);
                            return;
                        }
                        String group = parts[0];
                        String encMessage = parts[1];
                        //_hyperzmq.logprint("Group: " + group);
                        //_hyperzmq.logprint("Encrypted Message: " + encMessage);

                        _hyperzmq.newEventReceived(group, encMessage);
                    }
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                    socket.close();
                }
            }
            socket.close();
        });
        _subscriptionListeners.put(t, atomicBoolean);
        t.start();
    }

    public void setValidatorURL(String validatorURL) {
        this.validatorURL = validatorURL;
    }

    public void stopAllThreads() {
        _subscriptionListeners.forEach((t, aBool) -> aBool.set(false));
    }
}

