/* Copyright 2016,  2017 Intel Corporation
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
------------------------------------------------------------------------------*/

package sawtooth.sdk.messaging;

import com.google.protobuf.ByteString;
import sawtooth.sdk.protobuf.Message;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A ZMQ implementation of client networking class.
 */
public class ZmqStream implements Stream {
  /**
   * Futures that are waiting to be resolved.
   */
  private ConcurrentHashMap<String, Future> futureHashMap;
  /**
   * Threadsafe queue to interact with the background thread.
   */
  private LinkedBlockingQueue<SendReceiveThread.MessageWrapper> receiveQueue;
  /**
   * The background thread.
   */
  private SendReceiveThread sendReceiveThread;
  /**
   * The Thread that drives the background sendReceiveThread.
   */
  private Thread thread;

  /**
   * The constructor.
   * @param address the zmq address.
   */
  public ZmqStream(final String address) {
    this.futureHashMap = new ConcurrentHashMap<String, Future>();
    this.receiveQueue = new LinkedBlockingQueue<SendReceiveThread.MessageWrapper>();
    this.sendReceiveThread = new SendReceiveThread(address, futureHashMap, this.receiveQueue);
    this.thread = new Thread(sendReceiveThread);
    this.thread.start();
  }

  /**
   * Send a message and return a Future that will later have the Bytestring.
   * @param destination one of the Message.MessageType enum values defined in
   *                    validator.proto
   * @param contents    the ByteString that has been serialized from a Protobuf
   *                    class
   * @return future a future that will have ByteString that can be deserialized
   *         into a, for example, GetResponse
   */
  @Override
  public final Future send(final Message.MessageType destination, final ByteString contents) {

    Message message = Message.newBuilder().setCorrelationId(this.generateId()).setMessageType(destination)
        .setContent(contents).build();

    FutureByteString future = new FutureByteString(message.getCorrelationId());
    this.futureHashMap.put(message.getCorrelationId(), future);
    this.sendReceiveThread.sendMessage(message);
    return future;
  }

  /**
   * Send a message without getting a future back. Useful for sending a response
   * message to, for example, a transaction
   * @param destination   Message.MessageType defined in validator.proto
   * @param correlationId a random string generated on the server for the client
   *                      to send back
   * @param contents      ByteString serialized contents that the server is
   *                      expecting
   */
  @Override
  public final void sendBack(final Message.MessageType destination, final String correlationId,
      final ByteString contents) {
    Message message = Message.newBuilder().setCorrelationId(correlationId).setMessageType(destination)
        .setContent(contents).build();

    this.sendReceiveThread.sendMessage(message);
  }

  /**
   * close the Stream.
   */
  @Override
  public final void close() {
    try {
      this.sendReceiveThread.stop();
      this.thread.join();
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }
  }

  /**
   * Get a message that has been received.
   * @return result, a protobuf Message
   */
  @Override
  public final Message receive() {
    SendReceiveThread.MessageWrapper result = null;
    try {
      result = this.receiveQueue.take();
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }
    return result.getMessage();
  }

  /**
   * Get a message that has been received. If the timeout is expired, throws
   * TimeoutException.
   * @param timeout time to wait for a message.
   * @return result, a protobuf Message
   * @throws TimeoutException The Message is not received before timeout.
   */
  @Override
  public final Message receive(final long timeout) throws TimeoutException {
    SendReceiveThread.MessageWrapper result = null;
    try {
      result = this.receiveQueue.poll(timeout, TimeUnit.SECONDS);
      if (result == null) {
        throw new TimeoutException("The recieve queue timed out.");
      }
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }
    return result.getMessage();
  }

  /**
   * generate a random String, to correlate sent messages. with futures
   * @return a random String
   */
  private String generateId() {
    return UUID.randomUUID().toString();
  }

}
