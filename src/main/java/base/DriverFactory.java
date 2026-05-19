package base;

import config.ConfigResolver;
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

import java.net.URL;
import java.time.Instant;

public class DriverFactory {
    private static final Logger LOGGER = LogManager.getLogger(DriverFactory.class);

    public static WebDriver createDriver() {
        return createDriver("default-session");
    }

    public static WebDriver createDriver(String sessionName) {
        String browser = ConfigResolver.get("browser", "chrome");
        return createDriver(sessionName, browser);
    }

    public static WebDriver createDriver(String sessionName, String browser) {
        // Mobile always uses BrowserStack — detect from browser parameter
        if (browser != null && (browser.equalsIgnoreCase("mobile_android")
                || browser.equalsIgnoreCase("mobile_ios"))) {
            return createMobileDriver(sessionName, browser);
        }

        String execution = ConfigResolver.get("execution", "local");

        if ("browserstack".equalsIgnoreCase(execution)) {
            return createBrowserStackDriver(sessionName, browser);
        }

        WebDriver driver = "grid".equalsIgnoreCase(execution)
                ? createRemoteDriver(browser)
                : createLocalDriver(browser);
        boolean healEnabled = Boolean.parseBoolean(ConfigResolver.get("heal.enabled", "true"));
        return healEnabled ? HealeniumWebDriverFactory.wrapDriver(driver) : driver;
    }

    private static WebDriver createMobileDriver(String sessionName, String browser) {
        String username = System.getenv("BROWSERSTACK_USERNAME");
        String accessKey = System.getenv("BROWSERSTACK_ACCESS_KEY");

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
                ConfigResolver.get("project.name", "HealGrid"));
        bstackOptions.setCapability("realMobile", "true");

        MutableCapabilities browserCaps;

        if ("mobile_ios".equalsIgnoreCase(browser)) {
            bstackOptions.setCapability("deviceName",
                    ConfigResolver.get("bs.device", "iPhone 14"));
            bstackOptions.setCapability("osVersion",
                    ConfigResolver.get("bs.os.version", "16"));
            browserCaps = new MutableCapabilities();
            browserCaps.setCapability("browserName", "Safari");
            LOGGER.info("Creating mobile iOS driver: device={}, osVersion={}",
                    bstackOptions.getCapability("deviceName"),
                    bstackOptions.getCapability("osVersion"));
        } else {
            // mobile_android (default)
            bstackOptions.setCapability("deviceName",
                    ConfigResolver.get("bs.device", "Samsung Galaxy S23"));
            bstackOptions.setCapability("osVersion",
                    ConfigResolver.get("bs.os.version", "13.0"));
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
        String username = System.getenv("BROWSERSTACK_USERNAME");
        String accessKey = System.getenv("BROWSERSTACK_ACCESS_KEY");

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
                ConfigResolver.get("project.name", "HealGrid"));

        String effectiveBrowser = (browser != null && !browser.isEmpty())
                ? browser
                : ConfigResolver.get("browser", "chrome");
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
            String gridUrl = ConfigResolver.get("grid.url", "http://localhost:4444");
            URL url = new URL(gridUrl);
            RemoteWebDriver remoteDriver;
            switch (browser.toLowerCase()) {
                case "chrome":
                    remoteDriver = new RemoteWebDriver(url, getChromeOptions());
                    break;
                case "firefox":
                    remoteDriver = new RemoteWebDriver(url, new FirefoxOptions());
                    break;
                default:
                    throw new RuntimeException("Unsupported browser: " + browser);
            }
            LOGGER.info("Grid session assigned: {} | browser: {} | thread: {}",
                    remoteDriver.getSessionId(), browser,
                    Thread.currentThread().getId());
            return remoteDriver;
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
        boolean isHeadless = Boolean.parseBoolean(ConfigResolver.get("headless", "false"));
        if (isHeadless) {
            options.addArguments("--headless");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
        }
        return options;
    }

    private static final String BUILD_NAME = ConfigResolver.get(
            "build.name",
            "HealGrid-Build-" + Instant.now().toEpochMilli()
    );
}
