package kitchen.sink.demo.client;

import kitchen.sink.demo.dto.request.CreateMemberRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for member-service ({@code app.member-service.url}). It carries no password or hash.
 *
 * <p>FUTURE SCOPE: member-service will allow {@code POST /members} to service clients only, so this call must then
 * send a service-client token (client-credentials grant), for example through a Feign {@code RequestInterceptor}.
 */
@FeignClient(name = "member-service", url = "${app.member-service.url}")
public interface MemberServiceClient {

    /** Creates the member in member-service; a 409 (already exists) or any other error becomes a FeignException. */
    @PostMapping("/members")
    void createMember(@RequestBody CreateMemberRequest request);
}
