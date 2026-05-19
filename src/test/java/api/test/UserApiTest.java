package api.test;

import api.base.BaseApiTest;
import api.service.UserApi;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.testng.ITestContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

import static org.hamcrest.Matchers.*;

@Epic("API Testing")
@Feature("User Endpoints")
public class UserApiTest extends BaseApiTest {
    private UserApi userApi;

    @BeforeClass
    @Override
    public void setup(ITestContext context) {
        super.setup(context);
        userApi = new UserApi(requestSpec);
    }

    @Override
    protected String getBaseUri() {
        return "https://jsonplaceholder.typicode.com";
    }

    static final int STATUS_CODE_200 = 200;
    static final int STATUS_CODE_201 = 201;
    static final int STATUS_CODE_404 = 404;

    @Story("Get list of users")
    @Test
    public void getUsers_returns200() {
        userApi.getUsers(2)
                .then()
                .statusCode(STATUS_CODE_200);
    }

    @Story("Get single user with correct Id")
    @Test
    public void getSingleUser_returnsCorrectId() {
        userApi.getUserById(2)
                .then()
                .statusCode(STATUS_CODE_200)
                .body("id", equalTo(2))
                .body("name", notNullValue())
                .body("email", notNullValue())
                .body(matchesJsonSchemaInClasspath("schemas/user-schema.json"));
    }

    @Story("Create user")
    @Test
    public void createUser_returns201() {
        userApi.createUser("Jishu", "QA Engineer")
                .then()
                .statusCode(STATUS_CODE_201)
                .body("name", equalTo("Jishu"));
    }

    @Story("Verify 404 when user not found")
    @Test
    public void getNonExistentUser_returns404() {
        userApi.getUserById(9999)
                .then()
                .statusCode(STATUS_CODE_404);
    }

    @Story("Verify threshold response time")
    @Test
    public void getUsers_respondsWithinThreshold() {
        userApi.getUsers(2)
                .then()
                .statusCode(STATUS_CODE_200)
                .time(lessThan(3000L));
    }
}