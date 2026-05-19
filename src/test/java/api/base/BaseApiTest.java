package api.base;

import config.ConfigResolver;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;

public abstract class BaseApiTest {
    private static final Logger LOGGER = LogManager.getLogger(BaseApiTest.class);
    private static boolean suiteInfoLogged = false;
    protected RequestSpecification requestSpec;

    protected abstract String getBaseUri();

    public void setup(ITestContext context) {
        synchronized (BaseApiTest.class) {
            if (!suiteInfoLogged) {
                suiteInfoLogged = true;
                ConfigResolver.setSuiteParameters(
                        context.getSuite().getXmlSuite().getParameters()
                );
                LOGGER.info("TestNG suite: {}", context.getSuite().getName());
                String cmd = System.getProperty("sun.java.command", "IDE or unknown");
                LOGGER.info("Command: {}", cmd);
                LOGGER.info("Execution mode: {}", ConfigResolver.get("execution", "local"));
            }
        }

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        requestSpec = new RequestSpecBuilder()
                .setBaseUri(getBaseUri())
                .setContentType(ContentType.JSON)
                .build();
    }
}