package kitchen.sink.demo.controller;

import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import kitchen.sink.demo.exception.GlobalExceptionHandler;
import kitchen.sink.demo.service.RegistrationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false) // security rules are covered in SecurityConfigTest
@ContextConfiguration(classes = {RegistrationController.class, GlobalExceptionHandler.class})
class RegistrationControllerTest {

    private static final String VALID_BODY =
            "{\"firstName\":\"Ann\",\"lastName\":\"Lee\",\"email\":\"ann@example.com\",\"phoneNo\":\"1234567890\",\"password\":\"s3cretPassphrase15\"}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegistrationService registrationService;

    @Test
    @Tag("critical")
    void shouldReturn200WithMessage_whenRequestIsValid() throws Exception {
        when(registrationService.registerUser(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse("Registered"));

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registered"));

        verify(registrationService).registerUser(
                new RegistrationRequest("Ann", "Lee", "ann@example.com", "1234567890", "s3cretPassphrase15"));
    }

    @Test
    @Tag("critical")
    void shouldReturn409_whenEmailAlreadyExists() throws Exception {
        when(registrationService.registerUser(any(RegistrationRequest.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Member Already Exist"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            blank name              | '' | Lee | ann@example.com | 1234567890 | s3cretPass
            name with digits        | Ann1 | Lee | ann@example.com | 1234567890 | s3cretPass
            name over 25 chars      | abcdefghijklmnopqrstuvwxyz | Lee | ann@example.com | 1234567890 | s3cretPass
            blank last name         | Ann | '' | ann@example.com | 1234567890 | s3cretPass
            last name with digits   | Ann | Lee2 | ann@example.com | 1234567890 | s3cretPass
            last name over 25 chars | Ann | abcdefghijklmnopqrstuvwxyz | ann@example.com | 1234567890 | s3cretPass
            blank email             | Ann | Lee | '' | 1234567890 | s3cretPass
            malformed email         | Ann | Lee | not-an-email | 1234567890 | s3cretPass
            blank phone             | Ann | Lee | ann@example.com | '' | s3cretPass
            phone under 10 digits   | Ann | Lee | ann@example.com | 123456789 | s3cretPass
            phone over 12 digits    | Ann | Lee | ann@example.com | 1234567890123 | s3cretPass
            phone with non-digits   | Ann | Lee | ann@example.com | 12345abcde | s3cretPass
            blank password          | Ann | Lee | ann@example.com | 1234567890 | ''
            password under 15 chars | Ann | Lee | ann@example.com | 1234567890 | short
            password over 64 chars | Ann | Lee | ann@example.com | 1234567890 | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            """)
    @Tag("critical")
    void shouldReturn400AndSkipService_whenFieldIsInvalid(String scenario, String name, String lastName, String email, String phoneNo,
            String password)
            throws Exception {
        String body = "{\"firstName\":\"" + name + "\",\"lastName\":\"" + lastName + "\",\"email\":\"" + email + "\",\"phoneNo\":\"" + phoneNo
                + "\",\"password\":\"" + password + "\"}";

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(registrationService, never()).registerUser(any());
    }

    @Test
    void shouldReturn400_whenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(registrationService, never()).registerUser(any());
    }
}
