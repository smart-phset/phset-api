package com.smart.phset.api.feature.iot;

import com.smart.phset.api.config.SecurityConfig;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = IotController.class, properties = "app.mqtt.api-key=test-iot-key")
@Import({SecurityConfig.class, IotProperties.class})
class IotErrorDispatchTests {
    @Autowired MockMvc mvc;
    @MockitoBean DeviceStateRepository states;
    @MockitoBean MqttGateway mqtt;

    @Test void brokerUnavailableSurvivesContainerErrorDispatch() throws Exception {
        when(mqtt.command("grow-room-1", "fan", false))
                .thenThrow(new MqttGateway.BrokerUnavailableException());
        var response = mvc.perform(post("/api/iot/devices/grow-room-1/actuators/fan")
                        .header("X-SmartPhset-IoT-Key", "test-iot-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"on\":false}"))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse();

        // MockMvc does not perform the servlet container's sendError redispatch.
        // Exercise that second request through the security chain and Boot /error.
        mvc.perform(post("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, response.getStatus())
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI,
                                "/api/iot/devices/grow-room-1/actuators/fan")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test void authenticationAndNotFoundErrorsSurviveDispatch() throws Exception {
        for (int code : new int[]{401, 404}) {
            mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
                            .requestAttr(RequestDispatcher.ERROR_REQUEST_URI,
                                    "/api/iot/devices/grow-room-1/state")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code));
        }
    }

    @Test void ordinaryErrorAndUnrelatedRequestsRemainProtected() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isForbidden());
        mvc.perform(get("/api/private")).andExpect(status().isForbidden());
        verifyNoInteractions(mqtt);
    }
}
