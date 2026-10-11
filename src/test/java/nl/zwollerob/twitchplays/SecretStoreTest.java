package nl.zwollerob.twitchplays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class SecretStoreTest {
	private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJjaGFubmVsIjoiNjAzYWJjIn0.c2lnbmF0dXJl";

	@Test
	void encryptsOnDiskAndReadsBack(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config").resolve("secret.txt");
		SecretStore.write(file, TOKEN);
		String onDisk = Files.readString(file);
		assertTrue(onDisk.startsWith("dpapi:"), onDisk);
		assertFalse(onDisk.contains(TOKEN));
		assertFalse(onDisk.contains("eyJ"), "no part of the token may be readable");
		assertEquals(TOKEN, SecretStore.read(file));
	}

	@Test
	void encryptsOldPlainTextFiles(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("old.txt");
		Files.writeString(file, TOKEN + "\n");
		assertEquals(TOKEN, SecretStore.read(file));
		assertTrue(Files.readString(file).startsWith("dpapi:"), "plain text should be encrypted after the first read");
		assertEquals(TOKEN, SecretStore.read(file));
	}

	@Test
	void unreadableOrMissingGivesEmpty(@TempDir Path dir) throws Exception {
		assertEquals("", SecretStore.read(dir.resolve("missing.txt")));
		Path broken = dir.resolve("broken.txt");
		Files.writeString(broken, "dpapi:AAAA");
		assertEquals("", SecretStore.read(broken));
	}
}
