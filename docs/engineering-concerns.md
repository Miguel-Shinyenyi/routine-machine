# Engineering concerns

Added 2026-10-16 with concepts 1 to 59, extended on 2026-10-19 with 60 to 111. A list of 111 backend concepts, taken from a handwritten list Miguel shared. It is a hard rule for every current and future coding project: each concept is considered, and the answer is written down.

## The rule

1. Every project keeps a concerns record, in its design doc or `PROJECT.md`. The record has one row per number below.
2. Each row gets one of three answers, and a reason in one sentence:
   - Applies: the project handles it. Name the file or doc that shows how.
   - Not applicable: the project does not have this problem. Say why.
   - Deferred: it will matter later. Say what event makes it matter.
3. Considered does not mean built. Most projects will build a small part of the list. The rule is that nothing on the list is skipped by accident.
4. A new project starts its record before the first feature. An existing project adds the record when someone asks for an audit. Nobody audits a project unprompted.
5. When a concept causes a real fault, the fault is recorded against its number, with the date and the fix.
6. An answer of "applies" must point at evidence: a test, a doc or a config. A claim without evidence stays "deferred".

## The 111 concepts, each as a question

### Traffic and edges

1. Rate limiting: what stops one caller from using all the capacity?
2. Caching: what is cached, for how long, and who can read a stale value?
3. Load balancing: how does a request choose an instance?
4. Reverse proxies: what sits in front of the app, and what does it change on the request?
5. API gateways: where do authentication, routing and quotas live?
48. CDN: which responses can be served from a node near the user?
49. Edge caching: what is cached at the edge, and who can clear it?
50. Cache invalidation: what event removes a cached value, and what if that event is lost?

### Delivery and operations

6. CI/CD: what must pass before code reaches each environment?
7. Docker: does the image build from a clean checkout, and is it small and pinned?
8. Kubernetes: if this runs there, what are the resource limits, probes and rollout rules?
51. Feature flags: which changes can be turned off without a deploy?
52. Blue-green deployment: can the old version keep running until the new one is proven?
53. Canary releases: can a small share of traffic try a change first?
54. Rolling deployments: what happens to requests in flight while instances restart?
55. Rollbacks: how do we return to the last good version, and does the data allow it?
56. Health checks: what does "healthy" mean, and who reads it?
57. Liveness and readiness probes: when is an instance alive, and when is it ready for traffic?
58. Monitoring: which numbers tell us the system is wrong before a user does?
59. Logging: can one request be followed across every log line, without secrets in them?

### Service calls and failure

9. Service discovery: how does one service find another?
10. Circuit breakers: when a dependency fails, what stops us from calling it again at once?
11. Timeouts: does every outside call have a limit?
12. Retries: which failures are safe to retry, and how many times?
13. Exponential backoff: do retries wait longer each time, with jitter?
14. Idempotency: can the same request run twice without a second effect?
44. Backpressure: when input outruns processing, what slows or sheds it?

### Messaging and workflows

15. Message queues: what happens to a message if the consumer is down?
16. Pub/sub: who receives an event, and who may not?
17. Event-driven architecture: can we replay events, and in what order do they arrive?
18. Distributed transactions: if two systems must change together, what guarantees they do?
19. Saga pattern: if step three fails, what undoes steps one and two?
20. Dead letter queues: where does a message go after it keeps failing, and who looks?
21. Cron jobs: what if a job runs twice, or overlaps itself, or does not run?

### Real-time connections

22. WebSockets: how are connections counted, authenticated and closed?
23. Long polling: how long does a request wait, and what holds a thread while it waits?
24. Server-sent events: how does a client resume after a dropped stream?

### Data access and storage

25. Database indexing: which queries have an index, and what does each index cost on write?
26. Query optimization: have the slowest queries been read with their plans?
27. N+1 queries: does a list endpoint run one query or one per row?
28. Connection pooling: what is the pool size, and what happens when it runs out?
29. Read replicas: which reads can accept lag?
30. Sharding: what is the shard key, and what breaks when one shard grows?
31. Partitioning: how is a large table split, and how is old data removed?
32. Replication: what is copied, how far behind can the copy be, and what happens at failover?
33. Leader election: who decides which instance leads, and what if two think they lead?
34. CAP theorem: when the network splits, do we choose consistency or availability, and is that written down?
35. Eventual consistency: where can a reader see old data, and does the user know?

### Concurrency

36. Optimistic locking: how is a lost update detected?
37. Pessimistic locking: which rows are locked, for how long, in what order?
38. Distributed locks: what expires the lock, and what if the holder is slow, not dead?
39. Race conditions: which two actions can interleave, and what test forces them to?
40. Deadlocks: do all paths take locks in the same order, and is a deadlock retried?
41. Memory leaks: what grows over days, and what alert shows it?
42. Garbage collection: what do pauses and heap size look like under load?
43. Thread safety: which objects are shared between threads, and are they safe to share?

### Scale and performance

45. Autoscaling: what number triggers a new instance, and what limits the growth?
46. Horizontal scaling: can a second instance run without breaking the first?
47. Vertical scaling: what is the largest single machine we would accept, and what is the limit after it?
91. Latency: how long does a typical request take, measured where the user sits?
92. Throughput: how many requests or jobs per second can it handle before it degrades?
93. P99 latency: how slow is the slowest 1 in 100 requests, and is that written as a target?
94. Tail latency: which dependency makes the slowest requests slow, and does one slow call hold up a whole page?

### Observability and reliability targets

60. Distributed tracing: can one request be followed across every service it touches?
61. Metrics: which counters, gauges and timers exist, and who reads them?
62. Alerts: what wakes someone, and is every alert something a person can act on?
63. SLOs: what level of service is promised, in numbers?
64. SLIs: which measurement proves the SLO is met?
65. Error budgets: how much failure is allowed before new work stops for reliability work?
66. Observability: can we answer a new question about production without shipping new code?

### Security

67. Secrets management: where do secrets live, who can read them, and how are they rotated?
68. IAM: which person or service can do what, and is it the least they need?
69. OAuth: which flows are used, and are tokens stored and refreshed safely?
70. JWT rotation: how are signing keys rotated, and how long does a stolen token work?
71. TLS: is every connection that leaves a machine encrypted, with valid certificates?
72. Encryption at rest: which stored data is encrypted, and who holds the key?
73. Encryption in transit: does data stay encrypted between our own services too?
74. WAF: what filters malicious requests before they reach the app?
75. DDoS protection: what absorbs a flood of traffic, and what does it cost us?
76. CORS: which origins may call the API from a browser, and is the list as short as it can be?
77. CSRF: can another site make a signed-in user's browser change something here?
78. SQL injection: is every query parameterised, including dynamic sorting and filters?
79. XSS: is every piece of user text escaped where it is shown?
80. SSRF: can a user make the server fetch a URL of their choosing, including internal ones?

### Change, recovery and cost

81. Database migrations: does every schema change run forward safely on live data?
82. Schema versioning: which version of the schema is live, and can old code read it during a rollout?
83. Disaster recovery: if the whole environment is lost, how long until we are back, and with how much data lost?
84. Backups: when was a restore last tested, not only a backup taken?
85. Failover: what takes over when the primary fails, and has the switch been tried?
86. Multi-region deployment: does any user or law require more than one region?
87. Chaos engineering: which failures have we caused on purpose to see what happens?
88. Cost optimization: what does one user or one job cost to serve, and what is the largest line?
89. Cold starts: how slow is the first request after idle, and does a user ever feel it?
90. Serverless limits: which time, memory or connection limits could a function hit?

### Networking and protocols

95. Network partitions: what does each service do when it cannot reach another?
96. Clock skew: which logic depends on two machines agreeing on the time?
97. DNS: what happens when a name stops resolving or resolves to an old address?
98. TCP vs UDP: does each connection need ordered, reliable delivery, or speed?
99. HTTP/2 and HTTP/3: does the client and proxy support them, and does it matter here?
100. gRPC: would a typed, binary contract between services help, or is JSON over HTTP enough?
101. Webhooks: are incoming webhooks verified, idempotent and retried safely; are outgoing ones signed?

### Versioning, build and infrastructure

102. API versioning: how does an old client keep working when the API changes?
103. Semantic versioning: do version numbers tell users which changes break them?
104. Infrastructure as code: can the whole environment be rebuilt from files in a repo?
105. Terraform: if used, where does its state live, and who can apply it?
106. Helm charts: if Kubernetes is used, are its manifests packaged and versioned?
107. Build caching: how long does a clean build take, and is the cache safe to trust?
108. Dependency hell: are versions pinned, updates reviewed, and conflicts visible?

### Incidents

109. Production incidents: how is an incident declared, and who leads it?
110. On-call: who is reachable when something breaks, and what do they need to fix it?
111. Postmortems: after an incident, is the cause written down without blame, with actions that get done?

## Record format

Put this table in each project's design doc, one row per concept, 1 to 111.

```
| # | Concept | Answer | Reason and evidence | Date |
|---|---------|--------|---------------------|------|
| 14 | Idempotency | Applies | Key plus request hash, see docs/backend.md | 2026-10-16 |
```

## Link to the other rules

- `docs/understanding-first.md` says how to work: stay close to the code, check the output against the goal.
- This file says what to consider: the 111 concepts.
- A concept that was considered and recorded counts as understood. A concept that was skipped counts as a risk.