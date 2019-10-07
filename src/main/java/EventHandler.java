import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.protobuf.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

class EventHandler {
    private final Crypto _crypto;
    private final HyperZMQ _hyperzmq;
    private boolean _runEventReceiver = true;
    private Logger log = Logger.getLogger(EventHandler.class.getName());
    private List<String> _subscribedGroups = new ArrayList<>();

    EventHandler(HyperZMQ callback, Crypto crypto) {
        this._hyperzmq = callback;
        this._crypto = crypto;
        subscribe();
    }

    void addGroup(String groupName) {
        _subscribedGroups.add(groupName);
    }

    void removeGroup(String groupName) {
        _subscribedGroups.remove(groupName);
    }

    /**
     * Connect to the event subsystem. If successful start a thread which receives events.
     */
    private void subscribe() {
        log.info("Subscribing...");
        EventFilter eventFilter = EventFilter.newBuilder()
                .setKey("address")
                .setMatchString("2f9d35*")
                .setFilterType(EventFilter.FilterType.REGEX_ANY)
                .build();

        EventSubscription subscription = EventSubscription.newBuilder()
                //.setEventType("sawtooth/state-delta")
                .setEventType("myEvent")  // TODO event type per group?
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

    /**
     * Receive events and call the callback for all groups subscribed
     *
     * @param socket socket on which events are received
     */
    private void receiveEvents(ZMQ.Socket socket) {
        log.info("Starting to listen to events...");
        while (_runEventReceiver) {
            byte[] recv = socket.recv();
            try {
                Message msg = Message.parseFrom(recv);
                if (msg.getMessageType() != Message.MessageType.CLIENT_EVENTS) {
                    log.info("received event message is not of type event!");
                }

                EventList list = EventList.parseFrom(msg.getContent());
                for (Event e : list.getEventsList()) {
                    String received = e.toString();
                    log.info("[Event] received deserialized: " + received);

                    String fullMessage = received.substring(received.indexOf("data"));
                    log.info("fullMessage: " + fullMessage);

                    String csvMessage = fullMessage.substring(7, fullMessage.length() - 2); //TODO
                    log.info("csvMessage: " + csvMessage);

                    String[] parts = csvMessage.split(",");
                    String group = parts[0];
                    String encMessage = parts[1];
                    log.info("Group: " + group);
                    log.info("encMessage: " + encMessage);
                    if (_subscribedGroups.contains(group)) {
                        log.info("The group is subscribed, message is passed to the callback!");
                        String cleartext = _crypto.decrypt(group, encMessage);
                        //log.info("cleartext: " + cleartext);
                        _hyperzmq.newMessage(group, cleartext);
                    }
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }
}
