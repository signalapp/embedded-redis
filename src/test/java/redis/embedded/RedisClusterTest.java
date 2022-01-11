package redis.embedded;

import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.embedded.util.JedisUtil;

import java.io.Closeable;
import java.net.Inet4Address;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RedisClusterTest {
    private final RedisSentinelBuilder sentinelBuilder = RedisSentinel.builder();
    private String bindAddress;

    private Redis sentinel1;
    private Redis sentinel2;
    private Redis master1;
    private Redis master2;

    private RedisCluster instance;

    @Before
    public void setUp() throws Exception {
        sentinel1 = mock(Redis.class);
        sentinel2 = mock(Redis.class);
        master1 = mock(Redis.class);
        master2 = mock(Redis.class);

        // Jedis translates ”localhost” to getLocalHost().getHostAddress() (see Jedis HostAndPort#getLocalHostQuietly),
        // which can vary from 127.0.0.1 (most notably, Debian/Ubuntu return 127.0.1.1)
        if (bindAddress == null) {
            bindAddress = Inet4Address.getLocalHost().getHostAddress();
            sentinelBuilder.bind(bindAddress);
        }
    }


    @Test
    public void stopShouldStopEntireCluster() throws Exception {
        //given
        final List<Redis> sentinels = Arrays.asList(sentinel1, sentinel2);
        final List<Redis> servers = Arrays.asList(master1, master2);
        instance = new RedisCluster(sentinels, servers);

        //when
        instance.stop();

        //then
        for(Redis s : sentinels) {
            verify(s).stop();
        }
        for(Redis s : servers) {
            verify(s).stop();
        }
    }

    @Test
    public void startShouldStartEntireCluster() throws Exception {
        //given
        final List<Redis> sentinels = Arrays.asList(sentinel1, sentinel2);
        final List<Redis> servers = Arrays.asList(master1, master2);
        instance = new RedisCluster(sentinels, servers);

        //when
        instance.start();

        //then
        for(Redis s : sentinels) {
            verify(s).start();
        }
        for(Redis s : servers) {
            verify(s).start();
        }
    }

    @Test
    public void isActiveShouldCheckEntireClusterIfAllActive() throws Exception {
        //given
        given(sentinel1.isActive()).willReturn(true);
        given(sentinel2.isActive()).willReturn(true);
        given(master1.isActive()).willReturn(true);
        given(master2.isActive()).willReturn(true);
        final List<Redis> sentinels = Arrays.asList(sentinel1, sentinel2);
        final List<Redis> servers = Arrays.asList(master1, master2);
        instance = new RedisCluster(sentinels, servers);

        //when
        instance.isActive();

        //then
        for(Redis s : sentinels) {
            verify(s).isActive();
        }
        for(Redis s : servers) {
            verify(s).isActive();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithSingleMasterNoSlavesCluster() throws Exception {
        //given
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).sentinelCount(1).replicationGroup("ourmaster", 0).build();
        cluster.start();

        //when
        JedisSentinelPool pool = null;
        Jedis jedis = null;
        try {
            pool = new JedisSentinelPool("ourmaster", Sets.newHashSet("localhost:26379"));
            jedis = testPool(pool);
        } finally {
            closeQuietly(jedis, pool);
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithSingleMasterAndOneSlave() throws Exception {
        //given
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).sentinelCount(1).replicationGroup("ourmaster", 1).build();
        cluster.start();

        //when
        JedisSentinelPool pool = null;
        Jedis jedis = null;
        try {
            pool = new JedisSentinelPool("ourmaster", Sets.newHashSet("localhost:26379"));
            jedis = testPool(pool);
        } finally {
            closeQuietly(jedis, pool);
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithSingleMasterMultipleSlaves() throws Exception {
        //given
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).sentinelCount(1).replicationGroup("ourmaster", 2).build();
        cluster.start();

        //when
        JedisSentinelPool pool = null;
        Jedis jedis = null;
        try {
            pool = new JedisSentinelPool("ourmaster", Sets.newHashSet("localhost:26379"));
            jedis = testPool(pool);
        } finally {
            closeQuietly(jedis, pool);
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithTwoSentinelsSingleMasterMultipleSlaves() throws Exception {
        //given
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).sentinelCount(2).replicationGroup("ourmaster", 2).build();
        cluster.start();

        //when
        JedisSentinelPool pool = null;
        Jedis jedis = null;
        try {
            pool = new JedisSentinelPool("ourmaster", Sets.newHashSet("localhost:26379", "localhost:26380"));
            jedis = testPool(pool);
        } finally {
            closeQuietly(jedis, pool);
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithTwoPredefinedSentinelsSingleMasterMultipleSlaves() throws Exception {
        //given
        List<Integer> sentinelPorts = Arrays.asList(26381, 26382);
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).sentinelPorts(sentinelPorts).replicationGroup("ourmaster", 2).build();
        cluster.start();
        final Set<String> sentinelHosts = JedisUtil.portsToJedisHosts(sentinelPorts);

        //when
        JedisSentinelPool pool = null;
        Jedis jedis = null;
        try {
            pool = new JedisSentinelPool("ourmaster", sentinelHosts);
            jedis = testPool(pool);
        } finally {
            closeQuietly(jedis, pool);
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithThreeSentinelsThreeMastersOneSlavePerMasterCluster() throws Exception {
        //given
        final String master1 = "master1";
        final String master2 = "master2";
        final String master3 = "master3";
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).sentinelCount(3).quorumSize(2)
                .replicationGroup(master1, 1)
                .replicationGroup(master2, 1)
                .replicationGroup(master3, 1)
                .build();
        cluster.start();

        //when
        JedisSentinelPool pool1 = null;
        JedisSentinelPool pool2 = null;
        JedisSentinelPool pool3 = null;
        Jedis jedis1 = null;
        Jedis jedis2 = null;
        Jedis jedis3 = null;
        try {
            pool1 = new JedisSentinelPool(master1, Sets.newHashSet("localhost:26379", "localhost:26380", "localhost:26381"));
            pool2 = new JedisSentinelPool(master2, Sets.newHashSet("localhost:26379", "localhost:26380", "localhost:26381"));
            pool3 = new JedisSentinelPool(master3, Sets.newHashSet("localhost:26379", "localhost:26380", "localhost:26381"));
            jedis1 = testPool(pool1);
            jedis2 = testPool(pool2);
            jedis3 = testPool(pool3);
        } finally {
            closeQuietly(jedis1, pool1, jedis2, pool2, jedis3, pool3);
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithThreeSentinelsThreeMastersOneSlavePerMasterEphemeralCluster() throws Exception {
        //given
        final String master1 = "master1";
        final String master2 = "master2";
        final String master3 = "master3";
        final RedisCluster cluster = RedisCluster.builder().withSentinelBuilder(sentinelBuilder).ephemeral().sentinelCount(3).quorumSize(2)
                .replicationGroup(master1, 1)
                .replicationGroup(master2, 1)
                .replicationGroup(master3, 1)
                .build();
        cluster.start();
        final Set<String> sentinelHosts = JedisUtil.sentinelHosts(cluster);

        //when
        JedisSentinelPool pool1 = null;
        JedisSentinelPool pool2 = null;
        JedisSentinelPool pool3 = null;
        Jedis jedis1 = null;
        Jedis jedis2 = null;
        Jedis jedis3 = null;
        try {
            pool1 = new JedisSentinelPool(master1, sentinelHosts);
            pool2 = new JedisSentinelPool(master2, sentinelHosts);
            pool3 = new JedisSentinelPool(master3, sentinelHosts);
            jedis1 = testPool(pool1);
            jedis2 = testPool(pool2);
            jedis3 = testPool(pool3);
        } finally {
            closeQuietly(jedis1, pool1, jedis2, pool2, jedis3, pool3);
            cluster.stop();
        }
    }

    private Jedis testPool(JedisSentinelPool pool) {
        Jedis jedis;
        jedis = pool.getResource();
        jedis.mset("abc", "1", "def", "2");

        //then
        assertEquals("1", jedis.mget("abc").get(0));
        assertEquals("2", jedis.mget("def").get(0));
        assertEquals(null, jedis.mget("xyz").get(0));
        return jedis;
    }

    private void closeQuietly(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            IOUtils.closeQuietly(closeable, null);
        }
    }

}
