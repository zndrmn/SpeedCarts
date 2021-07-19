package nl.andrewlalis.speed_carts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contains all configuration options and the logic for loading config.
 */
public class Config {
	private static final Path CONFIG_FILE = Path.of("config", "speed_carts.yaml");

	private final double defaultSpeed;
	private final double minimumSpeed;
	private final double maximumSpeed;
	private final String signRegex;

	public Config() {
		try {
			this.ensureConfigExists();
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			JsonNode configJson = mapper.readTree(Files.newInputStream(CONFIG_FILE));
			this.defaultSpeed = configJson.get("defaultSpeed").asDouble();
			this.minimumSpeed = configJson.get("minimumSpeed").asDouble();
			this.maximumSpeed = configJson.get("maximumSpeed").asDouble();
			this.signRegex = configJson.get("signRegex").asText();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void ensureConfigExists() throws IOException {
		if (!Files.exists(CONFIG_FILE)) {
			var out = Files.newOutputStream(CONFIG_FILE);
			var defaultConfigInputStream = SpeedCarts.class.getClassLoader().getResourceAsStream("default_config.yaml");
			if (defaultConfigInputStream == null) {
				throw new IOException("Could not load default_config.yaml");
			}
			defaultConfigInputStream.transferTo(out);
			defaultConfigInputStream.close();
			out.close();
		}
	}

	public double getDefaultSpeed() {
		return defaultSpeed;
	}

	public double getMinimumSpeed() {
		return minimumSpeed;
	}

	public double getMaximumSpeed() {
		return maximumSpeed;
	}

	public String getSignRegex() {
		return signRegex;
	}
}
