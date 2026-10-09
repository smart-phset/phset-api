package com.smart.phset.api.feature.iot;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/iot/devices")
public class IotController {
    private final DeviceStateRepository states;
    private final IotProperties properties;
    private final MqttGateway mqtt;
    private final String key;
    public IotController(DeviceStateRepository states,IotProperties properties,MqttGateway mqtt,
            @Value("${app.mqtt.api-key:}") String key) {
        this.states=states;this.properties=properties;this.mqtt=mqtt;this.key=key;
    }
    @GetMapping("/{device}/state")
    public DeviceState state(@PathVariable String device,
            @RequestHeader(value="X-SmartPhset-IoT-Key",required=false) String supplied) {
        authorize(supplied);known(device);
        return states.findById(device).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"No reported state"));
    }
    @PostMapping("/{device}/actuators/{actuator}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> command(@PathVariable String device,@PathVariable String actuator,
            @RequestHeader(value="X-SmartPhset-IoT-Key",required=false) String supplied,
            @Valid @RequestBody IotMessages.Command command) {
        authorize(supplied);known(device);
        if(!Set.of("fan","light","pump").contains(actuator))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown actuator");
        try {return mqtt.command(device,actuator,command.on());}
        catch(MqttGateway.BrokerUnavailableException error) {throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MQTT unavailable");}
    }
    private void known(String device) {
        if(!properties.known(device)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown device");
    }
    private void authorize(String supplied) {
        if(key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"IoT REST key not configured");
        if(supplied==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid IoT key");
    }
}
