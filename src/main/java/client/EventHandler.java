package client;

import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sawtooth.sdk.protobuf.*;

import java.util.ArrayList;
import java.util.List;

class EventHandler {
    private final HyperZMQ _hyperzmq;
    private boolean _runEventReceiver = true;
    private static final String CORRELATION_ID = "123";
    private List<Thread> _subscriptionListeners = new ArrayList<>();
    private static final String DEFAULT_VALIDATOR_URL = "tcp://localhost:4004";

    EventHandler(HyperZMQ callback) {
        this._hyperzmq = callback;
       /* ZMQ.Socket groupSocket = subscribe(EventFilter.FilterType.REGEX_ANY, "myEvent", "tcp://localhost:4004");
        if (groupSocket != null) {
            receiveGroupEvents(groupSocket);
        } */

        ZMQ.Socket blockIDSocket = subscribe(EventFilter.FilterType.REGEX_ANY, "sawtooth/block-commit", "tcp://localhost:4004");
        if (blockIDSocket != null) {
            receiveCommitEvents(blockIDSocket);
        } else {
            _hyperzmq.logprint("Could not create socket to listen to 'block-commit'");
        }
    }

    void subscribeToGroup(String groupName) {
        ZMQ.Socket socket = subscribe(EventFilter.FilterType.REGEX_ANY, groupName, DEFAULT_VALIDATOR_URL);
        if (socket != null) {
            _hyperzmq.logprint("Subscribing to group: " + groupName);
            receiveGroupEvents(socket);
        } else {
            _hyperzmq.logprint("Could not open subscription socket for group: " + groupName);
        }
    }

    private void receiveCommitEvents(ZMQ.Socket socket) {
        Thread t = new Thread(() -> {
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
        });
        _subscriptionListeners.add(t);
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
                //.addLastKnownBlockIds("76d20bc1c9c8be78ebdd82d4a658b96a7a059a65d8a3e3ef069b6b3f30957780494378ac26b5dd8d9a6c9da976818e5f1008c029ec75e2d52dcee8f618ab6d79")
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
     *
     * @param socket socket on which events are received
     */
    private void receiveGroupEvents(ZMQ.Socket socket) {
        // Events are sent to the same socket
        Thread t = new Thread(() -> {
            while (_runEventReceiver) {
                //_hyperzmq.logprint("Starting to listen to events...");
                byte[] recv = socket.recv();
                try {
                    Message msg = Message.parseFrom(recv);
                    if (msg.getMessageType() != Message.MessageType.CLIENT_EVENTS) {
                        _hyperzmq.logprint("received event message is not of type event!");
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
                }
            }
        });
        _subscriptionListeners.add(t);
        t.start();
    }
}
