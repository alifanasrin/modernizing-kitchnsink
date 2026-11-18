package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.dto.request.MemberRequest;
import kitchen.sink.demo.dto.response.MemberResponse;
import kitchen.sink.demo.dto.response.PaginatedResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.exception.MemberNotFoundException;
import kitchen.sink.demo.repository.MemberRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock
    MemberRepository memberRepository;

    @InjectMocks
    MemberServiceImpl service;

    private static Member ann() {
        return new Member("ann@example.com", "1234567890", "Ann", "Lee");
    }

    @Test
    void shouldQueryZeroBasedPageSortedByLastAndFirstName_whenPageNoIsOneBased() {
        when(memberRepository.findAllByOrderByLastNameAscFirstNameAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        service.getMembers(new MemberRequest(2, 5));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findAllByOrderByLastNameAscFirstNameAsc(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void shouldReturnOneBasedPagingMetadata_whenMembersExist() {
        List<Member> members = List.of(ann());
        List<MemberResponse> responses = List.of(new MemberResponse("Ann", "Lee", "1234567890", "ann@example.com"));
        when(memberRepository.findAllByOrderByLastNameAscFirstNameAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(members, PageRequest.of(1, 5), 11));

        PaginatedResponse<MemberResponse> result = service.getMembers(new MemberRequest(2, 5));

        assertThat(result.data()).isEqualTo(responses);
        assertThat(result.pageNo()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(5);
        assertThat(result.totalRecords()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.lastPage()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyPage_whenNoMembersExist() {
        when(memberRepository.findAllByOrderByLastNameAscFirstNameAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PaginatedResponse<MemberResponse> result = service.getMembers(new MemberRequest(1, 10));

        assertThat(result.data()).isEmpty();
        assertThat(result.pageNo()).isEqualTo(1);
        assertThat(result.totalRecords()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.lastPage()).isZero();
    }

    @Test
    void shouldReturnMember_whenEmailExists() {
        when(memberRepository.findByEmail("ann@example.com")).thenReturn(Optional.of(ann()));

        MemberResponse result = service.findByEmail(" Ann@Example.COM ");

        assertThat(result).isEqualTo(new MemberResponse("Ann", "Lee", "1234567890", "ann@example.com"));
    }

    @Test
    @Tag("critical")
    void shouldThrowMemberNotFound_whenMemberDoesNotExist() {
        when(memberRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByEmail("nobody@example.com"))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessage("Member Not Found with email nobody@example.com");
    }
}
