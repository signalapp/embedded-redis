package redis.embedded;

import com.google.common.io.Resources;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.exceptions.RedisBuildingException;
import redis.embedded.util.Architecture;
import redis.embedded.util.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RedisServerTest {

	private RedisServer redisServer;

	@Test(timeout = 1500L)
	public void testSimpleRun() throws Exception {
		redisServer = new RedisServer(6379);
		redisServer.start();
		Thread.sleep(1000L);
		redisServer.stop();
	}

	@Test(expected = RuntimeException.class)
	public void shouldNotAllowMultipleRunsWithoutStop() {
		try {
			redisServer = new RedisServer(6379);
			redisServer.start();
			redisServer.start();
		} finally {
			redisServer.stop();
		}
	}

	@Test
	public void shouldAllowSubsequentRuns() {
		redisServer = new RedisServer(6379);
		redisServer.start();
		redisServer.stop();

		redisServer.start();
		redisServer.stop();

		redisServer.start();
		redisServer.stop();
	}

	@Test
	public void testSimpleOperationsAfterRun() {
		redisServer = new RedisServer(6379);
		redisServer.start();

		JedisPool pool = null;
		Jedis jedis = null;
		try {
			pool = new JedisPool("localhost", 6379);
			jedis = pool.getResource();
			jedis.mset("abc", "1", "def", "2");

			assertEquals("1", jedis.mget("abc").get(0));
			assertEquals("2", jedis.mget("def").get(0));
			assertNull(jedis.mget("xyz").get(0));
		} finally {
			if (jedis != null) {
				jedis.close();
			}
			if (pool != null) {
				pool.destroy();
			}
			redisServer.stop();
		}
	}

    @Test
    public void shouldIndicateInactiveBeforeStart() {
        redisServer = new RedisServer(6379);
        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldIndicateActiveAfterStart() {
        redisServer = new RedisServer(6379);
        redisServer.start();
        assertTrue(redisServer.isActive());
        redisServer.stop();
    }

    @Test
    public void shouldIndicateInactiveAfterStop() {
        redisServer = new RedisServer(6379);
        redisServer.start();
        redisServer.stop();
        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldOverrideDefaultExecutable() {
        RedisExecProvider customProvider = RedisExecProvider.defaultProvider()
                .override(OS.UNIX, Architecture.x86, Resources.getResource("redis-server-6.0.5-32").getFile())
                .override(OS.UNIX, Architecture.x86_64, Resources.getResource("redis-server-6.0.5").getFile())
                .override(OS.MAC_OS_X, Resources.getResource("redis-server-6.0.5").getFile());

        redisServer = new RedisServerBuilder()
                .redisExecProvider(customProvider)
                .build();
    }

    @Test(expected = RedisBuildingException.class)
    public void shouldFailWhenBadExecutableGiven() {
        RedisExecProvider buggyProvider = RedisExecProvider.defaultProvider()
                .override(OS.UNIX, "some")
                .override(OS.WINDOWS, Architecture.x86, "some")
                .override(OS.WINDOWS, Architecture.x86_64, "some")
                .override(OS.MAC_OS_X, "some");

        redisServer = new RedisServerBuilder()
                .redisExecProvider(buggyProvider)
                .build();
    }

	@Test
	public void testAwaitRedisServerReady() throws Exception {
		String readyPattern =  RedisServer.builder().build().redisReadyPattern();

		assertReadyPattern(new BufferedReader(
						new InputStreamReader(getClass()
								.getResourceAsStream("/redis-2.x-standalone-startup-output.txt"))),
				readyPattern);

		assertReadyPattern(new BufferedReader(
						new InputStreamReader(getClass()
								.getResourceAsStream("/redis-3.x-standalone-startup-output.txt"))),
				readyPattern);

		assertReadyPattern(new BufferedReader(
						new InputStreamReader(getClass()
								.getResourceAsStream("/redis-4.x-standalone-startup-output.txt"))),
				readyPattern);

		assertReadyPattern(new BufferedReader(
						new InputStreamReader(getClass()
								.getResourceAsStream("/redis-6.x-standalone-startup-output.txt"))),
				readyPattern);
	}

	private void assertReadyPattern(BufferedReader reader, String readyPattern) throws IOException {
		String outputLine;
		do {
			outputLine = reader.readLine();
			assertNotNull(outputLine);
		} while (!outputLine.matches(readyPattern));
	}
}
