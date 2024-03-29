embedded-redis
==============

Redis embedded server for Java integration testing

Fork Notes
==============
This repository is a fork of https://github.com/ozimov/embedded-redis, which is in turn a fork of https://github.com/kstyrc/embedded-redis. We've updated the embedded Redis binaries to version 7.0.15 so we can write tests that use recent Redis features without imposing dependencies that are not well-encapsulated by a single Maven/Gradle build.

Maven dependency
==============

Maven Central:
```xml
<dependency>
  <groupId>org.signal</groupId>
  <artifactId>embedded-redis</artifactId>
  <version>0.9.0</version>
</dependency>
```

Usage
==============

Running RedisServer is as simple as:
```java
RedisServer redisServer = new RedisServer(6379);
redisServer.start();
// do some work
redisServer.stop();
```

You can also provide RedisServer with your own executable:
```java
// 1) given explicit file (os-independence broken!)
RedisServer redisServer = new RedisServer("/path/to/your/redis", 6379);

// 2) given os-independent matrix
RedisExecProvider customProvider = RedisExecProvider.defaultProvider()
  .override(OS.UNIX, "/path/to/unix/redis")
  .override(OS.UNIX, Architecture.x86_64, "/path/to/unix/redis.x86_64")
  .override(OS.UNIX, Architecture.arm64, "/path/to/unix/redis.arm64")
  .override(OS.UNIX, Architecture.x86, "/path/to/unix/redis.i386")
  .override(OS.MAC_OS_X, Architecture.x86_64, "/path/to/macosx/redis-x86_64")
  .override(OS.MAC_OS_X, Architecture.arm64, "/path/to/macosx/redis.arm64")
  
RedisServer redisServer = new RedisServer(customProvider, 6379);
```

You can also use fluent API to create RedisServer:
```java
RedisServer redisServer = RedisServer.builder()
  .redisExecProvider(customRedisProvider)
  .port(6379)
  .slaveOf("locahost", 6378)
  .configFile("/path/to/your/redis.conf")
  .build();
```

Or even create simple redis.conf file from scratch:
```java
RedisServer redisServer = RedisServer.builder()
  .redisExecProvider(customRedisProvider)
  .port(6379)
  .setting("bind 127.0.0.1") // good for local development on Windows to prevent security popups
  .slaveOf("locahost", 6378)
  .setting("daemonize no")
  .setting("appendonly no")
  .setting("maxmemory 128M")
  .build();
```

## Setting up a cluster

Our Embedded Redis has support for HA Redis clusters with Sentinels and master-slave replication

#### Using ephemeral ports
A simple redis integration test with Redis cluster on ephemeral ports, with setup similar to that from production would look like this:
```java
public class SomeIntegrationTestThatRequiresRedis {
  private RedisCluster cluster;
  private Set<String> jedisSentinelHosts;

  @Before
  public void setup() throws Exception {
    //creates a cluster with 3 sentinels, quorum size of 2 and 3 replication groups, each with one master and one slave
    cluster = RedisCluster.builder().ephemeral().sentinelCount(3).quorumSize(2)
                    .replicationGroup("master1", 1)
                    .replicationGroup("master2", 1)
                    .replicationGroup("master3", 1)
                    .build();
    cluster.start();

    //retrieve ports on which sentinels have been started, using a simple Jedis utility class
    jedisSentinelHosts = JedisUtil.sentinelHosts(cluster);
  }
  
  @Test
  public void test() throws Exception {
    // testing code that requires redis running
    JedisSentinelPool pool = new JedisSentinelPool("master1", jedisSentinelHosts);
  }
  
  @After
  public void tearDown() throws Exception {
    cluster.stop();
  }
}
```

#### Retrieving ports
The above example starts Redis cluster on ephemeral ports, which you can later get with ```cluster.ports()```,
which will return a list of all ports of the cluster. You can also get ports of sentinels with ```cluster.sentinelPorts()```
or servers with ```cluster.serverPorts()```. ```JedisUtil``` class contains utility methods for use with Jedis client.

#### Using predefined ports
You can also start Redis cluster on predefined ports and even mix both approaches:
```java
public class SomeIntegrationTestThatRequiresRedis {
  private RedisCluster cluster;

  @Before
  public void setup() throws Exception {
    final List<Integer> sentinels = Arrays.asList(26739, 26912);
    final List<Integer> group1 = Arrays.asList(6667, 6668);
    final List<Integer> group2 = Arrays.asList(6387, 6379);
    //creates a cluster with 3 sentinels, quorum size of 2 and 3 replication groups, each with one master and one slave
    cluster = RedisCluster.builder().sentinelPorts(sentinels).quorumSize(2)
                    .serverPorts(group1).replicationGroup("master1", 1)
                    .serverPorts(group2).replicationGroup("master2", 1)
                    .ephemeralServers().replicationGroup("master3", 1)
                    .build();
    cluster.start();
  }
//(...)
```
The above will create and start a cluster with sentinels on ports ```26739, 26912```, first replication group on ```6667, 6668```,
second replication group on ```6387, 6379``` and third replication group on ephemeral ports.

Redis version
==============

By default, RedisServer runs an OS-specific executable enclosed in in the `embedded-redis` jar. The jar includes:

- Redis 7.0.15 for Linux/Unix (i386, x86_64 and arm64)
- Redis 7.0.15 for macOS (x86_64 and arm64e AKA Apple Silicon)

The enclosed binaries are built from source from the [`7.0.15` tag](https://github.com/redis/redis/releases/tag/7.0.15) in the official Redis repository. The Linux and Darwin/macOS binaries are statically-linked amd64 and x86 executables built using the [build-server-binaries.sh](src/main/docker/build-server-binaries.sh) script included in this repository at `/src/main/docker`.  Windows binaries are not included because Windows is not officially supported by Redis.

Note: the `build-server-binaries.sh` script attempts to build all of the above noted OS and architectures, which means that it expects the local Docker daemon to support all of them.  Docker Desktop on macOS and Windows supports multi-arch builds out of the box; Docker on Linux may require [additional configuration](https://docs.docker.com/buildx/working-with-buildx/).

Callers may provide a path to a specific `redis-server` executable if needed.


License
==============
Licensed under the Apache License, Version 2.0

The included Redis binaries are covered by [Redis’s license](https://github.com/redis/redis/blob/4930d19e70c391750479951022e207e19111eb55/COPYING):

> Copyright (c) 2006-2020, Salvatore Sanfilippo
> All rights reserved.
>
> Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
>
>    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
>    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
>    * Neither the name of Redis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


Contributors
==============
 * Krzysztof Styrc ([@kstyrc](https://github.com/kstyrc))
 * Piotr Turek ([@turu](https://github.com/turu))
 * anthonyu ([@anthonyu](https://github.com/anthonyu))
 * Artem Orobets ([@enisher](https://github.com/enisher))
 * Sean Simonsen ([@SeanSimonsen](https://github.com/SeanSimonsen))
 * Rob Winch ([@rwinch](https://github.com/rwinch))
 * Jon Chambers ([@jchambers](https://github.com/jchambers))
 * Chris Eager ([@eager](https://github.com/eager))

Changelog
==============

### 0.9.0
 * Updated to Redis 7.0.15
 * Updated Guava to 33
 * Updated JUnit to 4.13.2

### 0.8.3
 * Updated to Redis 6.2.7
 * Statically link Linux binaries with OpenSSL instead of LibreSSL to avoid `openssl.cnf` incompatibilities

### 0.8.2
 * Updated to Redis 6.2.6
 * Added native support for Apple Silicon (darwin/arm64) and Linux aarch64
 * Compiled Redis servers with TLS support

### 0.8.1
 * Include statically-linked Redis binaries
 * Update still more dependencies

### 0.8
 * Updated to Redis 6.0.5
 * Dropped support for Windows
 * Updated to Guava 29

### 0.7
 * Updated dependencies
 * Fixed an incorrect maximum memory setting
 * Add support for more Redis versions
 * Bind to 127.0.0.1 by default
 * Clean up gracefully at JVM exit

### 0.6
 * Support JDK 6 +

### 0.5
 * OS detection fix
 * redis binary per OS/arch pair
 * Updated to 2.8.19 binary for Windows

### 0.4 
 * Updated for Java 8
 * Added Sentinel support
 * Ability to create arbitrary clusters on arbitrary (ephemeral) ports
 * Updated to latest guava 
 * Throw an exception if redis has not been started
 * Redis errorStream logged to System.out

### 0.3
 * Fluent API for RedisServer creation

### 0.2
 * Initial decent release
