package net.alex9849.cocktailmaker.endpoints;

import jakarta.validation.Valid;
import net.alex9849.cocktailmaker.payload.dto.settings.ReversePumpSettings;
import net.alex9849.cocktailmaker.payload.dto.settings.ReversePumpSettingsDto;
import net.alex9849.cocktailmaker.service.PumpService;
import net.alex9849.cocktailmaker.service.SystemService;
import net.alex9849.cocktailmaker.service.pumps.PumpMaintenanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/system/")
public class SystemEndpoint {

    @Autowired
    private SystemService systemService;

    @Autowired
    private PumpService pumpService;

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "/pythonlibraries", method = RequestMethod.GET)
    public ResponseEntity<?> getPythonLibraries() throws IOException {
        return ResponseEntity.ok(systemService.getInstalledPythonLibraries());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "/audiodevices", method = RequestMethod.GET)
    public ResponseEntity<?> getAudioDevices() throws IOException {
        return ResponseEntity.ok(systemService.getAudioDevices());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "settings/reversepumping", method = RequestMethod.PUT)
    public ResponseEntity<?> setReversePumpSettings(@RequestBody @Valid ReversePumpSettingsDto.Request.Create settings) {
        if(settings.isEnable() && settings.getSettings() == null) {
            throw new IllegalStateException("Settings-Details are null!");
        }
        ReversePumpSettings reversePumpSettings = pumpService.fromDto(settings);
        pumpService.setReversePumpingSettings(reversePumpSettings);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "settings/reversepumping", method = RequestMethod.GET)
    public ResponseEntity<?> getReversePumpSettings() {;
        return ResponseEntity.ok(new ReversePumpSettingsDto.Response.Detailed(pumpService.getReversePumpingSettings()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = "/shutdown", method = RequestMethod.PUT)
    public ResponseEntity<?> shutdown() throws IOException {
        systemService.shutdown();
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "settings/global", method = RequestMethod.GET)
    public ResponseEntity<?> getGlobalSettings() {;
        return ResponseEntity.ok(systemService.getGlobalSettings());
    }
}
