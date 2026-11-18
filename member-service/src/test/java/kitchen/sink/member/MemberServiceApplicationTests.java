package kitchen.sink.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the whole application (MongoDB is only contacted when a query runs, so none is needed here). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:jwt-test.properties")
class MemberServiceApplicationTests {

    @Autowired MockMvc mockMvc;

    @Test
    void contextLoads_andTheMemberApisAreSecured() throws Exception {
        mockMvc.perform(get("/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/members/ann@example.com")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/members").header("Authorization", "Bearer " + TestTokens.forged()))
                .andExpect(status().isUnauthorized());
    }
}
