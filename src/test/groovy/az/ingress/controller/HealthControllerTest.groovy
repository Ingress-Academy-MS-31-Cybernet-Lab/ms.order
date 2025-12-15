package az.ingress.controller

import az.ingress.exception.ErrorHandler
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class HealthControllerTest extends Specification {
    HealthController healthController
    MockMvc mockMvc

    def setup() {
        healthController = new HealthController()
        mockMvc = MockMvcBuilders.standaloneSetup(healthController)
                                 .setControllerAdvice(new ErrorHandler())
                                 .build()
    }

    def "TestCheckHealthy"() {
        given:
        def url = "/health"

        when:
        def actual = mockMvc.perform(get(url))

        then:
        actual.andExpect(
                status().isOk()
        )
    }
}