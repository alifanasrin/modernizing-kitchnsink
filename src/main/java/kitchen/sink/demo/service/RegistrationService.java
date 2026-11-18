package kitchen.sink.demo.service;

import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;

public interface RegistrationService {


    /** Registers a new member and sends the verification email. */
    RegistrationResponse registerUser(RegistrationRequest request);
}
