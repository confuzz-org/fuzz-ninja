package ninja.utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

@Singleton
public class NinjaPropertiesImpl implements NinjaProperties {
	private static final Logger log = LoggerFactory
			.getLogger(NinjaPropertiesImpl.class);
	private final String ERROR_KEY_NOT_FOUND = "Key %s does not exist. Please include it in your application.conf. Otherwise this app will not work";

	private final Properties allCurrentNinjaProperties;

	private final String mode;

	// we are using a private logger in this case as NinjaPropertiesImpl is NOT
	// loaded
	// by guice.
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Inject
	public NinjaPropertiesImpl() {
		this.allCurrentNinjaProperties = new Properties();

		// get system variables... load application conf files...
		if (System.getProperty("mode") != null) {
			mode = System.getProperty("mode");
		} else {
			mode = "dev";
		}

		// 1. load application.conf
		Optional<Properties> applicationProperties = loadPropertiesInUtf8(CONF_FILE_LOCATION_BY_CONVENTION);

		if (!applicationProperties.isPresent()) {
			throw new RuntimeException("No basic configuration file found. Please make sure you got a file called: " + CONF_FILE_LOCATION_BY_CONVENTION);
		}
		
		// 2. Add all properties that are relevant for this mode:
		allCurrentNinjaProperties.putAll(getAllPropertiesOfThatMode(
				applicationProperties.get(), mode));

		// 3. load an external configuration file:
		// get system variables... load application conf files...
		if (System.getProperty(NINJA_EXTERNAL_CONF) != null) {

			String ninjaExternalConf = System.getProperty(NINJA_EXTERNAL_CONF);

			Optional<Properties> externalConfiguration = loadPropertiesInUtf8(ninjaExternalConf);
			
			if (!applicationProperties.isPresent()) {
				throw new RuntimeException("A system property called " + NINJA_EXTERNAL_CONF + " was set. But the correspinding file cannot be found. Make sure it is visible to this application and on the classpath.");
			}

			allCurrentNinjaProperties.putAll(externalConfiguration.get());

		}

	}

	@Override
	public String get(String key) {
		return allCurrentNinjaProperties.getProperty(key);

	}

	@Override
	public String getOrDie(String key) {

		String value = get(key);

		if (value == null) {
			log.error(String.format(ERROR_KEY_NOT_FOUND, key));
			throw new RuntimeException(String.format(ERROR_KEY_NOT_FOUND, key));
		} else {
			return value;
		}

	}

	@Override
	public Integer getInteger(String key) {

		String value = allCurrentNinjaProperties.getProperty(key);

		if (value == null) {
			return null;
		} else {

			try {
				return new Integer(value);
			} catch (NumberFormatException e) {
				return null;
			}

		}

	}

	@Override
	public Integer getIntegerOrDie(String key) {

		Integer value = getInteger(key);

		if (value == null) {
			log.error(String.format(ERROR_KEY_NOT_FOUND, key));
			throw new RuntimeException(String.format(ERROR_KEY_NOT_FOUND, key));
		} else {
			return value;
		}

	}

	@Override
	public Boolean getBooleanOrDie(String key) {

		Boolean value = getBoolean(key);

		if (value == null) {
			log.error(String.format(ERROR_KEY_NOT_FOUND, key));
			throw new RuntimeException(String.format(ERROR_KEY_NOT_FOUND, key));
		} else {
			return value;
		}

	}

	@Override
	public Boolean getBoolean(String key) {

		String value = allCurrentNinjaProperties.getProperty(key);

		if (value == null) {
			return null;
		} else {

			// make sure to return null when appropriate
			if (!(value.equals("true") || value.equals("false"))) {
				return null;
			}

			// seems to be true or false => therefore we parse the boolean
			return Boolean.parseBoolean(value);
		}

	}

	public void bindProperties(Binder binder) {
		Names.bindProperties(binder, allCurrentNinjaProperties);
	}

	/**
	 * This properties loader uses UTF-8... that's important...
	 * 
	 * @param classLoaderUrl
	 * @return
	 */
	private Optional<Properties> loadPropertiesInUtf8(String classLoaderUrl) {
		Properties props = new Properties();
		URL resource = getClass().getClassLoader().getResource(classLoaderUrl);

		try {
			props.load(new InputStreamReader(resource.openStream(), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			props = null;
			logger.error("Unsupported encoding while loading configuration file", e);
		} catch (IOException e) {
			props = null;
			logger.error("Could not find configuration file. Was looking for " + classLoaderUrl, e);
		} catch (NullPointerException e) {
			props = null;
			logger.error("Could not find configuration file. Was looking for " + classLoaderUrl, e);
		}

		return Optional.fromNullable(props);
	}

	/**
	 * Of course we want to have environments. By default ninja supports 3
	 * modes: - "test" - "dev" - "prod"
	 * 
	 * We are using one application.conf file containing all relevant
	 * configuration properties. As convention you have to use "%" when
	 * prefixing a property.
	 * 
	 * For instance if we are in mode "test" This: myproperty = funk is
	 * overwritten by %test.myproperty = funkier
	 * 
	 */
	private Properties getAllPropertiesOfThatMode(Properties properties,
			String mode) {

		Properties returnProtperties = new Properties();

		// The hashmap we get from properties is not ordered.
		// We therefore do two passes
		// Pass 1: Add all non % arguments
		// Pass 2: Add all % arguments matching this mode
		// => therefore matching % arguments will override the original ones
		for (Map.Entry<Object, Object> entry : properties.entrySet()) {

			if (!entry.getKey().toString().startsWith("%")) {
				returnProtperties.put(entry.getKey(), entry.getValue());

			}
		}

		// Pass 2: Add all % arguments matching this mode
		for (Map.Entry<Object, Object> entry : properties.entrySet()) {

			if (entry.getKey().toString().startsWith("%")) {

				// check if this is the mode I want to use...
				String myModePrefix = "%" + mode;

				if (entry.getKey().toString().startsWith(myModePrefix)) {
					// please replace me:
					// %test.myproperty=test should become
					// myproperty=test
					String keyToReplace = "%" + mode + "\\.";

					String newKeyName = entry.getKey().toString()
							.replaceFirst(keyToReplace, "");

					returnProtperties.put(newKeyName, entry.getValue());
				} // else do nothing... that's a property I don't want to
					// use....

			}
		}

		return returnProtperties;

	}

}
