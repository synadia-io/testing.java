# testing.java
Java Client Testing Programs

## Consumer Info Sim

As described to me, the problem app was doing about 10,000 consumer info calls periodically and repeatedly spread between 8 threads.
The main complaint was that timeouts stack up and eventually bring the system down. So I built a system to replicate as best I can.

* The run shown at the end ran for 46 hours.
* The servers were run on one instance with a 5 node Failground cluster (containerized servers) with mayhem random-hard-kill running with an interval of 30-120 seconds.
* The client processes were each run on their own instance
* All Java processes were built and run with Java 21 using the 2.20.5 version of JNats.

    | Type       | Instance  | OS                  | vCPU* Mem (GiB) | Network Performance (Gbps)*** |
    |------------|-----------|---------------------|-----------------|-------------------------------|
    | Failground | t3.xlarge | Ubuntu 24.04.2 LTS  | 16              | Up to 5                       |
    | Client     | t3.large  | Amazon Linux 2023   | 8               | Up to 5                       |

The instances/processes are as follows. (The clients notes have links to code.)

#### Failground Instance
The Failground setup running mayhem. Mayhem automatically kills and restarts servers periodically.

#### Client Instance 1
A "Produce" process does the following repeatedly until stopped:
1. Generates a unique consumer name and subject on the `data` stream in the form `data.<consumer_name>`
2. Creates the consumer.
3. Publishes from 10-100 messages on the subject.
4. Publishes a message to the `queue` stream containing the consumer name, subject and number of messages published.

* Each round (Steps 1-4) counts as 1 in the log.
* 6 individual full threads each ran the process. 
* Failure at any step is logged, but ignored, meaning for instance if it fails at step 3, the consumer is not removed.
* The process pausees if there are 11,000 or more consumers. It then waited until the Consume process removed consumers and would resume once there were 6,500 or fewer consumers.
* [Produce Source Code](src/main/java/io/synadia/workloads/ConsumerInfoSim.java#L218)

#### Client Instance 2
An "Info" process does the following repeatedly until stopped:
1. Read a list of consumer names from the stream. Shuffle the list.
2. For each consumer in the list...
    1. Make a consumer info call.
    2. Any success is counted. Any failure is logged.

* 8 individual full threads each ran the process.
* [Info Source Code](src/main/java/io/synadia/workloads/ConsumerInfoSim.java#L333)

#### Client Instance 3
A "Consume" process does the following repeatedly until stopped:
1. Use a shared consumer against the `queue` stream, read 1 record. This record contains a consumer name, subject and number of messages published.
2. Start a simplified consumer and fetch a fixed number of messages (not necessarily all messages for that subject).
3. Delete the consumer
4. Purge the subject

* Each round (Steps 1-4) counts as 1 in the log.
* 3 individual full threads each ran the process.
* [Consume Source Code](src/main/java/io/synadia/workloads/ConsumerInfoSim.java#L271)

### Streams

| Stream | Description                                                           |
|--------|-----------------------------------------------------------------------|
| data   | A stream used the processes to have subjects, consumers and messages. |
| queue  | A stream user for interprocess communication.                         |
| ex     | A stream to log info on exceptions.                                   |
| log    | A stream to log counts.                                               |

### Example Run Snapshot
There is another process that watches the streams and displays the state. 
Every so often it gathers info. Here is the info just before I stopped all processes. 
An asterix on a line item shows that that particular items was updated since the last gather.

1\. Basic stream information
```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│ Stream Information                                                       2025-03-04T13:06:19 │
├────────────────┬────────────┬────────────┬────────────┬────────────┬────────────┬────────────┤
│ ? Stream       │   Messages │   Subjects │  Consumers │  First Seq │   Last Seq │      Bytes │
├────────────────┼────────────┼────────────┼────────────┼────────────┼────────────┼────────────┤
│ * data         │    264,034 │      5,431 │      6,461 │  9,494,559 │ 43,302,837 │   13.58 mb │
│ * queue        │          0 │          0 │          1 │    784,676 │    784,675 │     0.00 b │
├────────────────┼────────────┼────────────┼────────────┼────────────┼────────────┼────────────┤
│   ex           │     33,552 │          8 │          0 │          1 │     33,552 │    8.02 mb │
│ * log          │      1,320 │         17 │          0 │    352,161 │    353,480 │  174.20 kb │
└────────────────┴────────────┴────────────┴────────────┴────────────┴────────────┴────────────┘
```

2\. A more specific breakdown of exceptions caught by the processes.
```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Exceptions                                                                                                                                               │
├────────────────┬───────────────────────────┬─────────────────────┬─────────┬─────────────────────────────────────────────────────────────────────────────┤
│ ? Job (Thread) │ Exception                 │ Last Occurrence     │ Count   │ Message                                                                     │
├────────────────┼───────────────────────────┼─────────────────────┼─────────┼─────────────────────────────────────────────────────────────────────────────┤
│   Consume      │ IO                        │ 2025-03-04T13:13:19 │   3,524 │ Timeout or no response waiting for NATS JetStream server                    │
│   Consume      │ JetStreamApi              │ 2025-03-04T00:37:30 │      92 │ JetStream system temporarily unavailable [10008]                            │
│   Consume      │ JetStreamStatus           │ 2025-03-04T13:00:35 │     178 │ 503 No Responders Available For Request                                     │
│   Consume      │ JetStreamStatusChecked    │ 2025-03-04T10:14:33 │      64 │ 503 No Responders Available For Request                                     │
│   Info         │ IO                        │ 2025-03-04T13:12:16 │  16,597 │ Timeout or no response waiting for NATS JetStream server                    │
│   Info         │ JetStreamApi              │ 2025-03-04T13:12:15 │     724 │ consumer is offline [10119]                                                 │
│   Produce      │ IO                        │ 2025-03-04T13:12:18 │  12,082 │ Timeout or no response waiting for NATS JetStream server                    │
│   Produce      │ JetStreamApi              │ 2025-03-04T13:12:15 │     291 │ stream is offline [10118]                                                   │
└────────────────┴───────────────────────────┴─────────────────────┴─────────┴─────────────────────────────────────────────────────────────────────────────┘
```

3\. The counts, by thread. Elapsed time was about 46 hours.
```
┌────────────────────────────────────────────────────┐
│ Log                                                │
├───────────────────┬────────────────┬───────────────┤
│ ? Job (Thread)    │ Count          │ Elapsed       │
├───────────────────┼────────────────┼───────────────┤
│   Consume (0)     │        261,124 │ 45:45:17.084  │
│   Consume (1)     │        261,283 │ 45:45:15.349  │
│   Consume (2)     │        260,595 │ 45:45:16.424  │
│   Info (0)        │     14,880,911 │ 46:00:28.813  │
│   Info (1)        │     14,872,681 │ 46:00:28.813  │
│   Info (2)        │     14,887,438 │ 46:00:28.813  │
│   Info (3)        │     15,311,403 │ 46:00:28.813  │
│   Info (4)        │     14,856,532 │ 46:00:28.818  │
│   Info (5)        │     14,843,474 │ 46:00:28.818  │
│   Info (6)        │     14,862,279 │ 46:00:28.813  │
│   Info (7)        │     14,900,289 │ 46:00:28.818  │
│   Produce (0)     │        132,434 │ 45:53:17.495  │
│   Produce (1)     │        135,446 │ 45:53:18.688  │
│   Produce (2)     │        107,122 │ 45:53:17.475  │
│   Produce (3)     │        130,931 │ 45:53:20.070  │
│   Produce (4)     │        121,031 │ 45:53:21.442  │
│   Produce (5)     │        157,041 │ 45:53:18.731  │
└───────────────────┴────────────────┴───────────────┘
```
