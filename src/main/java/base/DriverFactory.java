package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import utils.GenericUtil;

import java.net.URL;
import java.time.Instant;
import java.util.Properties;

public class DriverFactory {

    private static final Properties config = GenericUtil.getPropertiesFile("config.properties");
    private static final Logger LOGGER = LogManager.getLogger(DriverFactory.class);

    public static WebDriver createDriver() {
        return createDriver("default-session");
    }

    public static WebDriver createDriver(String sessionName) {
        String browser = getConfigValue("browser", "chrome");
        return createDriver(sessionName, browser);
    }

    public static WebDriver createDriver(String sessionName, String browser) {
        // Mobile always uses BrowserStack — detect from browser parameter
        if (browser != null && (browser.equalsIgnoreCase("mobile_android")
                || browser.equalsIgnoreCase("mobile_ios"))) {
            return createMobileDriver(sessionName, browser);
        }

        String execution = getConfigValue("execution", "local");

        if ("browserstack".equalsIgnoreCase(execution)) {
            return createBrowserStackDriver(sessionName, browser);
        }

        WebDriver driver = "grid".equalsIgnoreCase(execution)
                ? createRemoteDriver(browser)
                : createLocalDriver(browser);
        boolean healEnabled = Boolean.parseBoolean(getConfigValue("heal.enabled", "true"));
        return healEnabled ? HealeniumWebDriverFactory.wrapDriver(driver) : driver;
    }

    private static WebDriver createMobileDriver(String sessionName, String browser) {
        String username = getConfigValue("BROWSERSTACK_USERNAME", null);
        String accessKey = getConfigValue("BROWSERSTACK_ACCESS_KEY", null);

        if (username == null || username.isEmpty()) {
            throw new RuntimeException("BROWSERSTACK_USERNAME is not set. Set env var or Jenkins credential.");
        }
        if (accessKey == null || accessKey.isEmpty()) {
            throw new RuntimeException("BROWSERSTACK_ACCESS_KEY is not set. Set env var or Jenkins credential.");
        }

        MutableCapabilities bstackOptions = new MutableCapabilities();
        bstackOptions.setCapability("userName", username);
        bstackOptions.setCapability("accessKey", accessKey);
        bstackOptions.setCapability("buildName", BUILD_NAME);
        bstackOptions.setCapability("sessionName", sessionName);
        bstackOptions.setCapability("projectName",
                getConfigValue("project.name", "HealGrid"));
        bstackOptions.setCapability("realMobile", "true");

        MutableCapabilities browserCaps;

        if ("mobile_ios".equalsIgnoreCase(browser)) {
            bstackOptions.setCapability("deviceName",
                    getConfigValue("bs.device", "iPhone 14"));
            bstackOptions.setCapability("osVersion",
                    getConfigValue("bs.os.version", "16"));
            browserCaps = new MutableCapabilities();
            browserCaps.setCapability("browserName", "Safari");
            LOGGER.info("Creating mobile iOS driver: device={}, osVersion={}",
                    bstackOptions.getCapability("deviceName"),
                    bstackOptions.getCapability("osVersion"));
        } else {
            // mobile_android (default)
            bstackOptions.setCapability("deviceName",
                    getConfigValue("bs.device", "Samsung Galaxy S23"));
            bstackOptions.setCapability("osVersion",
                    getConfigValue("bs.os.version", "13.0"));
            browserCaps = new ChromeOptions();
            LOGGER.info("Creating mobile Android driver: device={}, osVersion={}",
                    bstackOptions.getCapability("deviceName"),
                    bstackOptions.getCapability("osVersion"));
        }

        browserCaps.setCapability("bstack:options", bstackOptions);

        try {
            URL hubUrl = new URL("https://hub.browserstack.com/wd/hub");
            RemoteWebDriver remoteDriver = new RemoteWebDriver(hubUrl, browserCaps);
            BrowserStackSessionContext.setSessionId(remoteDriver.getSessionId().toString());
            LOGGER.info("Mobile driver created, session: {}", remoteDriver.getSessionId());
            return remoteDriver;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mobile RemoteWebDriver", e);
        }
    }

    private static WebDriver createBrowserStackDriver(String sessionName, String browser) {
        String username = getConfigValue("BROWSERSTACK_USERNAME", null);
        String accessKey = getConfigValue("BROWSERSTACK_ACCESS_KEY", null);

        if (username == null || username.isEmpty()) {
            throw new RuntimeException("BROWSERSTACK_USERNAME is not set. Set env var or Jenkins credential.");
        }
        if (accessKey == null || accessKey.isEmpty()) {
            throw new RuntimeException("BROWSERSTACK_ACCESS_KEY is not set. Set env var or Jenkins credential.");
        }

        MutableCapabilities bstackOptions = new MutableCapabilities();
        bstackOptions.setCapability("userName", username);
        bstackOptions.setCapability("accessKey", accessKey);
        bstackOptions.setCapability("buildName", BUILD_NAME);
        bstackOptions.setCapability("sessionName", sessionName);
        bstackOptions.setCapability("projectName",
                getConfigValue("project.name", "HealGrid"));

        String effectiveBrowser = (browser != null && !browser.isEmpty())
                ? browser
                : getConfigValue("browser", "chrome");
        MutableCapabilities browserCaps;

        switch (effectiveBrowser.toLowerCase()) {
            case "firefox":
                browserCaps = new FirefoxOptions();
                break;
            case "chrome":
            default:
                browserCaps = new ChromeOptions();
                break;
        }
        browserCaps.setCapability("bstack:options", bstackOptions);

        try {
            URL hubUrl = new URL("https://hub.browserstack.com/wd/hub");
            RemoteWebDriver remoteDriver = new RemoteWebDriver(hubUrl, browserCaps);
            BrowserStackSessionContext.setSessionId(remoteDriver.getSessionId().toString());
            return remoteDriver;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create BrowserStack RemoteWebDriver", e);
        }
    }

    private static WebDriver createRemoteDriver(String browser) {
        try {
            String gridUrl = System.getProperty("grid.url",
                    config.getProperty("grid.url", "http://localhost:4444"));
            URL url = new URL(gridUrl);
            switch (browser.toLowerCase()) {
                case "chrome":
                    return new RemoteWebDriver(url, getChromeOptions());
                case "firefox":
                    return new RemoteWebDriver(url, new FirefoxOptions());
                default:
                    throw new RuntimeException("Unsupported browser: " + browser);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create RemoteWebDriver", e);
        }
    }

    private static WebDriver createLocalDriver(String browser) {
        switch (browser.toLowerCase()) {
            case "chrome":
                LOGGER.info("Creating local ChromeDriver");
                return new ChromeDriver(getChromeOptions());
            case "firefox":
                LOGGER.info("Creating local FirefoxDriver");
                FirefoxOptions ffOptions = new FirefoxOptions();
                ffOptions.setPageLoadStrategy(PageLoadStrategy.EAGER);
                return new FirefoxDriver(ffOptions);
            default:
                throw new RuntimeException("Unsupported browser: " + browser);
        }
    }

    private static ChromeOptions getChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        boolean isHeadless = Boolean.parseBoolean(getConfigValue("headless", "false"));
        if (isHeadless) {
            options.addArguments("--headless");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
        }
        return options;
    }

    private static String getConfigValue(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) return value;
        value = System.getenv(key);
        if (value != null) return value;
        value = config.getProperty(key);
        if (value != null) return value;
        return defaultValue;
    }

    private static final String BUILD_NAME = getConfigValue(
            "build.name",
            "HealGrid-Build-" + Instant.now().toEpochMilli()
    );
}