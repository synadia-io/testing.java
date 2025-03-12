# testing.java
Java Client Testing Programs

### ConsumerInfoSim


### Machine 1.
A 5 cluster Failground with mayhem random-hard-kill running with an interval of 30-120 seconds.
More than a real system, but a lot of random outages.

### Machine 2.
A "Produce" process, which does the following. Each round counts as 1 in the log.
[produceWorker](src/main/java/io/synadia/workloads/ConsumerInfoSim.java#L218)
1. Generates a unique consumer name and subject on the `data` stream in the form `data.<consumer_name>`
2. Creates the consumer.
3. Publishes from 10-100 messages on a unique subject \[segment\].
4. Publishes a message to the `queue` stream containing the consumer name, subject and number of messages published.

### Machine 3.
An "Info" process, which does the following.
1. Generates a unique consumer name and subject on the `data` stream in the form `data.<consumer_name>`
2. Creates the consumer.
3. Publishes from 10-100 messages on a unique subject \[segment\].
4. Publishes a message to the `queue` stream containing the consumer name, subject and number of messages published.


### Instance Details
| Type       | Instance  | OS                  | JDK                      | vCPU* Mem (GiB) | Network Performance (Gbps)*** |
|------------|-----------|---------------------|--------------------------|-----------------|-------------------------------|
| Client     | t3.large  | Amazon Linux 2023   | openjdk version "21.0.5" | 8               | Up to 5                       |
| Failground | t3.xlarge | Ubuntu 24.04.2 LTS  | N/A                      | 16              | Up to 5                       |
  


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