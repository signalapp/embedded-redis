package redis.embedded;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.util.JarUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RedisTlsTest {

	private RedisServer redisServer;

	private static File certFile;
	private static File keyFile;
	private static File caCertFile;

	@BeforeClass
	public static void setupClass() throws Exception {
		certFile = getFile("redis.crt");
		keyFile = getFile("redis.key");
		caCertFile = getFile("ca.crt");

		File trustStore = File.createTempFile("embedded-redis-test-truststore", ".jks");
		trustStore.deleteOnExit();

		Certificate cert;
		try (FileInputStream certInput = new FileInputStream(certFile)) {
			cert = CertificateFactory.getInstance("X.509").generateCertificate(certInput);
		}

		KeyStore keyStore = KeyStore.getInstance("jks");
		keyStore.load(null, null);

		keyStore.setCertificateEntry("testCaCert", cert);
		try (FileOutputStream out = new FileOutputStream(trustStore)) {
			keyStore.store(out, new char[0]);
		}

		System.setProperty("javax.net.ssl.trustStore", trustStore.getAbsolutePath());
		System.setProperty("javax.net.ssl.trustStoreType", "jks");
	}

	@Before
	public void setup() throws Exception {

		redisServer = RedisServer.builder()
				.port(0) // disable non-tls
				.tlsPort(6380)
				.setting("tls-cert-file " + certFile.getAbsolutePath())
				.setting("tls-key-file " + keyFile.getAbsolutePath())
				.setting("tls-ca-cert-file " + caCertFile.getAbsolutePath())
				.setting("tls-auth-clients no") // disable mTLS, for simplicity
				.build();
	}


	@Test(timeout = 1500L)
	public void testSimpleRun() throws Exception {
		redisServer.start();
		Thread.sleep(1000L);
		redisServer.stop();
	}

	@Test
	public void testSimpleOperationsAfterRun() {
		redisServer.start();

		try (JedisPool pool = new JedisPool("localhost", redisServer.tlsPorts().get(0), true);
			 Jedis jedis = pool.getResource()) {
			jedis.mset("abc", "1", "def", "2");

			assertEquals("1", jedis.mget("abc").get(0));
			assertEquals("2", jedis.mget("def").get(0));
			assertNull(jedis.mget("xyz").get(0));
		} finally {
			redisServer.stop();
		}
	}

	private static File getFile(String path) throws IOException {
		return fileExists(path) ?
				new File(path) :
				JarUtil.extractFileFromJar(path);
	}

	private static boolean fileExists(String path) {
		return new File(path).exists();
	}
}
