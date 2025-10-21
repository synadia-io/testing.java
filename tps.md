## TPS and Lost Messages

In an attempt to isolate where messages are being lost I've modified the code for that purpose. 

### Receiver
Connect to server 2 and set up a dispatched subscription on agreed subject. The message listener is as follows:

When a message comes in...

If it is the terminal message (0 byte payload), run is complete 
  * signal the terminate latch.
  * done processing messages. 

Else...
* Extract the message id.
* Count the message
* Is it the first message? 
  * record the message id as the last received
  * log
  * done processing the message.
  
  Else...
  * determine the expected message id from the last received + 1 
  * record the message id as the last received
  * compare the expected message id to the current message id
  * If the ids are different...
      * calculate and record the gap
      * log the current, expected and gap

The main thread of the receiver waits for the terminate latch to complete, then the process exits.

### Sender

Connect to server 1

#### Phase 1
Normal publishing until the connection is broken. A connection is considered broken if any of these are true.
(They should all be true within milliseconds.)
* Connection status is not Connection.Status.CONNECTED
* The Connection Listener indicates `disconnected` is true
* The Error Listener indicates `readClosed` is true

#### Phase 2
Everything after the connection is broken. The only behavior is to let the pending message queue empty out.

#### Error Listener
An error listener is in place to log and to track notification of the underlying socket being closed (`readClosed`).
If `readClosed` is true, the sender will leave phase 1. 

#### Connection Listener
A connection listener is in place to log and track a disconnect event (`disconnected`).
If `disconnected` is true, the sender will leave phase 1.

#### Write Listener
The write listener gets notified with every message that has been _buffered_.
Buffered means it has been removed from the pending message and its bytes have been copied to the byte array
buffer in preparation to be written to the socket. 

The message id is extracted from the header and is tracked much like the receiver looking for a gap.
(Messages without message ids, i.e. protocol messages are ignored)
If a gap occurs it's recorded for later reporting. In phase 2, the notification is ignored.

#### Stats Collector
In phase 1 ...

`incrementOutBytes(bytes)` and `registerWrite(bytes)` are tracked.
Every call to `incrementOutBytes` represent that 1 message and it's bytes have been buffered from the 
pending message queue to the byte array buffer. A call to `registerWrite(bytes)` indicates that
all the bytes currently in the byte array buffer have been used to call the socket write.

Calls to `incrementOutBytes` are tracked as "buffered" until `registerWrite(bytes)` is called, at which time
"buffered" is reset. If at time of disconnect, there are values in "buffered", it means those messages/bytes
where buffered but not written.

In phase 2 ... since we have stopped publishing and will not be disconnected, we are just tracking so we can
check the total amount of messages/bytes that we published versus the total amount buffered.

#### Publishing

While the client is connected...

* Publish messages at the TPS rate. 
  * For each publish, increment an id counter and build a header entry with its value.
* Log the number of messages published during the last "publish second" each time a new "publish second" starts
* If there is a publish failure (i.e. queue full) log the failure and decrement the id counter since the message was not published.

Once the process becomes aware of being disconnected...
* switch all listeners to phase 2
* publish the end marker message
* wait until there are no more messages in the pending queue.

### Sample Run

#### Results
```
RECEIVER
  Total Received Messages: 55345 // total messages received
  Total Receive Gap: 16          // gap encountered during outage

SENDER
Before Disconnect...
  Total Socket Written Bytes: 682,219,975    // bytes written to socket for test messages only
  Total Buffered Payload Messages: 55327     // number of test messages buffered
  Total Buffered Payload Bytes: 682,281,458  // bytes buffered from test messages, not necessarily written
  Last Message Id Buffered: 55327            // id of last message buffered before disconnect
After Disconnect...
  Total Buffered Payload Messages: 34        // number of test messages buffered
  Total Buffered Payload Bytes: 419,288      // bytes buffered from test messages, not necessarily written
Analysis ...
  Last Write Messages: 1                     // number of messages in the last socket write before disconnect
  Last Write Bytes: 12,332                   // number of bytes in the last socket write before disconnect
  Buffered Not Written Messages: 5           // messages buffered but not written to socket
  Buffered Not Written Bytes: 61,660         // bytes buffered but not written to socket
  No Writer Gaps                             // gap between message id between message being buffered. This should be none.
```

#### Log
```
[main@12:00:06.9232] Environment | JNats 2.23.1
[main@12:00:06.9487] Command Line | workload | Tps
[main@12:00:06.9487] Command Line | action | both
[main@12:00:06.9497] Command Line | paramsFile | [params\params.json, params\tps.json]
[main@12:00:06.9508] TPS | ----- Application Options -----
[main@12:00:06.9508] TPS | targetTps | 10000
[main@12:00:06.9508] TPS | subject | tps
[main@12:00:06.9508] TPS | messageIdKey | mid
[main@12:00:06.9508] TPS | payloadSize | 12288
[Thread-0@12:00:06.9642] RECEIVER | ----- Connection Options -----
[Thread-0@12:00:06.9653] RECEIVER | servers | [nats://localhost:5222, nats://localhost:4222, nats://localhost:6222]
[Thread-0@12:00:06.9664] RECEIVER | connectionTimeout | PT5S
[Thread-0@12:00:06.9664] RECEIVER | maxReconnects | -1
[Thread-0@12:00:06.9664] RECEIVER | reconnectBufferSize | 500000000
[Thread-0@12:00:06.9664] RECEIVER | bufferSize | 65536
[Thread-0@12:00:06.9664] RECEIVER | maxMessagesInOutgoingQueue | 10000
[Thread-0@12:00:06.9674] RECEIVER | receiveBufferSize | -1
[Thread-0@12:00:06.9674] RECEIVER | sendBufferSize | -1
[Thread-0@12:00:06.9674] RECEIVER | reconnectWait | PT5S
[Thread-0@12:00:06.9674] RECEIVER | pingInterval | PT5S
[Thread-0@12:00:06.9688] RECEIVER | maxPingsOut | 2
[Thread-0@12:00:06.9688] RECEIVER | socketWriteTimeout | PT0.1S
[Thread-0@12:00:06.9688] RECEIVER | socketReadTimeoutMillis | 10000
[pool-1-1@12:00:07.0551] CL-RECEIVER | [12:00:07.0530] | opened(CONNECTED) | nats://localhost:5222
[Thread-1@12:00:07.0688] SENDER | ----- Connection Options -----
[Thread-1@12:00:07.0688] SENDER | servers | [nats://localhost:4222, nats://localhost:6222, nats://localhost:5222]
[Thread-1@12:00:07.0688] SENDER | connectionTimeout | PT5S
[Thread-1@12:00:07.0700] SENDER | maxReconnects | -1
[Thread-1@12:00:07.0700] SENDER | reconnectBufferSize | 500000000
[Thread-1@12:00:07.0700] SENDER | bufferSize | 65536
[Thread-1@12:00:07.0700] SENDER | maxMessagesInOutgoingQueue | 10000
[Thread-1@12:00:07.0700] SENDER | receiveBufferSize | -1
[Thread-1@12:00:07.0710] SENDER | sendBufferSize | -1
[Thread-1@12:00:07.0710] SENDER | reconnectWait | PT5S
[Thread-1@12:00:07.0710] SENDER | pingInterval | PT5S
[Thread-1@12:00:07.0710] SENDER | maxPingsOut | 2
[Thread-1@12:00:07.0710] SENDER | socketWriteTimeout | PT0.1S
[Thread-1@12:00:07.0710] SENDER | socketReadTimeoutMillis | 10000
[pool-4-1@12:00:07.0772] CL-SENDER | [12:00:07.0760] | opened(CONNECTED) | nats://localhost:4222
[pool-3-1@12:00:07.0782] STATS | Payload Message Bytes | 12328
[pool-3-1@12:00:07.0782] WL-SENDER | buffering started
[pool-3-1@12:00:07.0782] STATS | Payload Message Bytes | 12329
[nats:3@12:00:07.0807] RECEIVER | Started Receiving...
[pool-3-1@12:00:07.0867] STATS | Payload Message Bytes | 12330
[pool-3-1@12:00:07.1778] STATS | Payload Message Bytes | 12331
[Thread-1@12:00:08.0794] SENDER | Messages Last Second: 9997
[pool-3-1@12:00:08.0804] STATS | Payload Message Bytes | 12332
[Thread-1@12:00:09.0793] SENDER | Messages Last Second: 10000
[Thread-1@12:00:10.0772] SENDER | Messages Last Second: 9987
[Thread-1@12:00:11.0778] SENDER | Messages Last Second: 9992
[Thread-1@12:00:12.0781] SENDER | Messages Last Second: 9999
[Thread-1@12:00:12.6159] SENDER | Publishing end marker message
[pool-4-1@12:00:12.6169] EL-SENDER | exceptionOccurred: | Connection(870283218) CONNECTED | java.io.IOException: Read channel closed.
[pool-4-1@12:00:12.6179] EL-SENDER | exceptionOccurred: | Connection(870283218) RECONNECTING | java.net.SocketException: An established connection was aborted by the software in your host machine
[pool-4-1@12:00:12.6179] CL-SENDER | [12:00:12.6150] | disconnected(RECONNECTING) | nats://localhost:4222
[pool-4-1@12:00:12.6216] CL-SENDER | [12:00:12.6210] | reconnected(CONNECTED) | nats://localhost:6222
[Thread-1@12:00:12.6221] SENDER | Waiting for 35 queued messages to be sent...
[pool-4-1@12:00:12.6221] CL-SENDER | [12:00:12.6220] | subscriptions re-established(CONNECTED) | nats://localhost:6222
[nats:3@12:00:12.6451] RECEIVER | ****** | Got Message Id: 55,328 but expected: 55,312 | Loss of 16
[pool-1-1@12:00:13.6588] CL-RECEIVER | [12:00:13.6580] | closed(CLOSED) | nats://localhost:5222
[pool-4-1@12:00:13.6588] CL-SENDER | [12:00:13.6580] | closed(CLOSED) | nats://localhost:6222
```