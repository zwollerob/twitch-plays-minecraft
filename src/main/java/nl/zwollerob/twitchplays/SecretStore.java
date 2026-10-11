package nl.zwollerob.twitchplays;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Stores tokens encrypted with Windows DPAPI: only the same Windows user on the same PC can
 * read them back, so a copied file (backup, NAS, shared folder) is useless elsewhere.
 * It does not protect against programs running as that same user.
 * Other systems store plain text, as before.
 */
public final class SecretStore {
	private static final String PREFIX = "dpapi:";
	/** Extra input for DPAPI, so other programs can't decrypt our files with a plain DPAPI call. */
	private static final byte[] ENTROPY = "twitchplays-secret-v1".getBytes(StandardCharsets.UTF_8);

	private SecretStore() {
	}

	/**
	 * Reads a secret. Returns "" when the file is missing or can't be decrypted (e.g. copied from
	 * another PC). A plain-text file from an older version is encrypted in place.
	 */
	public static String read(Path file) {
		String stored;
		try {
			if (!Files.exists(file)) {
				return "";
			}
			stored = Files.readString(file).strip();
		} catch (IOException e) {
			TwitchPlaysClient.LOGGER.warn("Could not read {}", file, e);
			return "";
		}
		if (!stored.startsWith(PREFIX)) {
			if (!stored.isEmpty() && Platform.isWindows()) {
				try {
					write(file, stored);
				} catch (IOException e) {
					TwitchPlaysClient.LOGGER.warn("Could not encrypt {}", file, e);
				}
			}
			return stored;
		}
		try {
			byte[] encrypted = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			byte[] plain = Crypt32Util.cryptUnprotectData(encrypted, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (Exception | LinkageError e) {
			TwitchPlaysClient.LOGGER.warn("Could not decrypt {} (made by another Windows user or PC?)", file);
			return "";
		}
	}

	public static void write(Path file, String secret) throws IOException {
		String stored = secret;
		if (Platform.isWindows()) {
			try {
				byte[] encrypted = Crypt32Util.cryptProtectData(secret.getBytes(StandardCharsets.UTF_8), ENTROPY,
						WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "Twitch Plays Minecraft", null);
				stored = PREFIX + Base64.getEncoder().encodeToString(encrypted);
			} catch (Exception | LinkageError e) {
				throw new IOException("Windows kon het token niet versleutelen: " + e.getMessage(), e);
			}
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, stored);
	}
}
