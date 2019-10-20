package client;

import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.protobuf.*;

import java.util.logging.Logger;


class EventHandler {
    private final HyperZMQ _hyperzmq;
    private boolean _runEventReceiver = true;
    private Logger _log = Logger.getLogger(EventHandler.class.getName());

    EventHandler(HyperZMQ callback) {
        this._hyperzmq = callback;
        subscribe();
    }

    /**
     * Connect to the event subsystem. If successful, start a thread which receives events.
     */
    private void subscribe() {
        _log.info("Subscribing...");
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

        sawtooth.sdk.protobuf.Message message = sawtooth.sdk.protobuf.Message.newBuilder()
                .setCorrelationId("123")
                .setMessageType(sawtooth.sdk.protobuf.Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST)
                .setContent(request.toByteString())
                .build();

        //log.info("Sending subscription request...");
        socket.send(message.toByteArray());

        byte[] responseBytes = socket.recv();
        sawtooth.sdk.protobuf.Message respMsg = null;
        try {
            respMsg = sawtooth.sdk.protobuf.Message.parseFrom(responseBytes);
            //log.info("Response deserialized: " + respMsg.toString());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (respMsg == null || respMsg.getMessageType() != sawtooth.sdk.protobuf.Message.MessageType.CLIENT_EVENTS_SUBSCRIBE_RESPONSE) {
            _log.info("Response was no subscription response");
            return;
        }
        ClientEventsSubscribeResponse cesr = null;
        try {
            cesr = ClientEventsSubscribeResponse.parseFrom(respMsg.getContent());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (cesr == null) {
            _log.info("cesr null");
            return;
        }
        if (cesr.getStatus() != ClientEventsSubscribeResponse.Status.OK) {
            _log.info("Subscribing failed: " + cesr.getResponseMessage());
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
        _log.info("Starting to listen to events...");
        while (_runEventReceiver) {
            byte[] recv = socket.recv();
            try {
                Message msg = Message.parseFrom(recv);
                if (msg.getMessageType() != Message.MessageType.CLIENT_EVENTS) {
                    _log.info("received event message is not of type event!");
                }

                EventList list = EventList.parseFrom(msg.getContent());
                for (Event e : list.getEventsList()) {
                    String received = e.toString();
                    _log.info("[Event] received deserialized: " + received);

                    String fullMessage = received.substring(received.indexOf("data"));
                    _log.info("fullMessage: " + fullMessage);

                    String csvMessage = fullMessage.substring(7, fullMessage.length() - 2); //TODO
                    _log.info("csvMessage: " + csvMessage);

                    String[] parts = csvMessage.split(",");
                    String group = parts[0];
                    String encMessage = parts[1];
                    String senderID = parts[2];
                    _log.info("Group: " + group);
                    _log.info("Encrypted Sender: " + senderID);
                    _log.info("Encrypted Message: " + encMessage);

                    _hyperzmq.newMessage(group, encMessage);
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }
}
