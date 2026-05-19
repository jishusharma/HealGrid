package api.test;

import api.base.BaseApiTest;
import api.service.AuthApi;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.testng.ITestContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.notNullValue;

@Epic("Auth API Testing")
@Feature("User Authentication")
public class AuthApiTest extends BaseApiTest {
    private AuthApi authApi;
    private String token;

    @BeforeClass
    @Override
    public void setup(ITestContext context) {
        super.setup(context);
        RequestSpecification authSpec = new RequestSpecBuilder()
                .addRequestSpecification(requestSpec)
                .addHeader("x-api-key", System.getenv("REQRES_API_KEY"))
                .build();
        authApi = new AuthApi(authSpec);
        token = authApi.login("eve.holt@reqres.in", "cityslicka")
                .then().extract().path("token");
    }

    @Override
    protected String getBaseUri() {
        return "https://reqres.in/api";
    }

    static final int STATUS_CODE_200 = 200;
    static final int STATUS_CODE_400 = 400;

    @Story("Login")
    @Test
    public void valid_login_returns200() {
        authApi.login("eve.holt@reqres.in", "cityslicka")
                .then()
                .statusCode(STATUS_CODE_200)
                .body("token", notNullValue());
    }

    @Story("Authenticated request")
    @Test
    public void authenticatedGetUsers_returns200() {
        authApi.getUsers(token, 1)
                .then()
                .statusCode(STATUS_CODE_200);
    }

    @Story("Invalid credentials")
    @Test
    public void invalidCredentials_returns400() {
        authApi.login("wrong@email.com", "wrongpassword")
                .then()
                .statusCode(STATUS_CODE_400);
    }

}