package kitchen.sink.demo.controller;

import jakarta.validation.Valid;
import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import kitchen.sink.demo.service.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/register")
public class RegistrationController {
    private final RegistrationService registrationService;
    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** Registers a new member and sends the verification email. */
    @PostMapping
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody  RegistrationRequest request){
        log.debug("registration request received");
        RegistrationResponse response = registrationService.registerUser(request);
        return ResponseEntity.ok(response);
    }

}
