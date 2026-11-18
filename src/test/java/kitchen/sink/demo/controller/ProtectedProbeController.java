package kitchen.sink.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that is not public, so {@code SecurityConfigTest} can check how tokens are validated. */
@RestController
class ProtectedProbeController {

    @GetMapping({"/probe", "/probe/{id}"})
    String probe() {
        return "ok";
    }
}
