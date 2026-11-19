package kitchen.sink.member.controller;

import kitchen.sink.member.dto.request.MemberRequest;
import kitchen.sink.member.dto.response.MemberResponse;
import kitchen.sink.member.dto.response.PaginatedResponse;
import kitchen.sink.member.exception.GlobalExceptionHandler;
import kitchen.sink.member.exception.MemberNotFoundException;
import kitchen.sink.member.service.MemberService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false) // security rules are covered in SecurityConfigTest
@ContextConfiguration(classes = {MemberController.class, GlobalExceptionHandler.class})
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberService memberService;

    @Test
    void shouldReturn200WithPagedMembers_whenParamsAreProvided() throws Exception {
        when(memberService.getMembers(new MemberRequest(2, 5))).thenReturn(
                new PaginatedResponse<>(
                        List.of(new MemberResponse("Ann", "Lee", "1234567890", "ann@example.com")), 5, 2, 11, 3));

        mockMvc.perform(get("/members").param("pageNo", "2").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].firstName").value("Ann"))
                .andExpect(jsonPath("$.data[0].lastName").value("Lee"))
                .andExpect(jsonPath("$.data[0].phoneNo").value("1234567890"))
                .andExpect(jsonPath("$.data[0].email").value("ann@example.com"))
                .andExpect(jsonPath("$.pageNo").value(2))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.lastPage").value(3))
                .andExpect(jsonPath("$.totalRecords").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void shouldDefaultToFirstPageOfTen_whenParamsAreMissing() throws Exception {
        when(memberService.getMembers(new MemberRequest(1, 10))).thenReturn(
                new PaginatedResponse<>(List.of(), 10, 1, 0, 0));

        mockMvc.perform(get("/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNo").value(1))
                .andExpect(jsonPath("$.pageSize").value(10));

        verify(memberService).getMembers(new MemberRequest(1, 10));
    }

    @ParameterizedTest(name = "pageNo={0}, pageSize={1}")
    @CsvSource({
            "0, 10",
            "-1, 10",
            "1, 0",
            "1, -5",
            "1, 51"
    })
    void shouldReturn400AndSkipService_whenPagingParamIsBelowOne(String pageNo, String pageSize) throws Exception {
        mockMvc.perform(get("/members").param("pageNo", pageNo).param("pageSize", pageSize))
                .andExpect(status().isBadRequest());

        verify(memberService, never()).getMembers(any());
    }

    @Test
    void shouldReturn400_whenPagingParamIsNotANumber() throws Exception {
        mockMvc.perform(get("/members").param("pageNo", "abc"))
                .andExpect(status().isBadRequest());

        verify(memberService, never()).getMembers(any());
    }

    @Test
    void shouldReturn200WithMember_whenEmailExists() throws Exception {
        when(memberService.findByEmail("ann@example.com"))
                .thenReturn(new MemberResponse("Ann", "Lee", "1234567890", "ann@example.com"));

        mockMvc.perform(get("/members/{email}", "ann@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ann"))
                .andExpect(jsonPath("$.lastName").value("Lee"))
                .andExpect(jsonPath("$.phoneNo").value("1234567890"))
                .andExpect(jsonPath("$.email").value("ann@example.com"));
    }

    @Test
    @Tag("critical")
    void shouldReturn404_whenMemberDoesNotExist() throws Exception {
        when(memberService.findByEmail("test1@example.com"))
                .thenThrow(new MemberNotFoundException("Member not found"));

        mockMvc.perform(get("/members/{email}", "test1@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Member not found"));
    }

    @Test
    void shouldReturn400_whenPathIsNotAnEmail() throws Exception {
        mockMvc.perform(get("/members/{email}", "not-an-email"))
                .andExpect(status().isBadRequest());

        verify(memberService, never()).findByEmail(any());
    }
}
