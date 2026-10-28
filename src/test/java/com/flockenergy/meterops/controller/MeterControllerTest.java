package com.flockenergy.meterops.controller;

import com.flockenergy.meterops.dto.*;
import com.flockenergy.meterops.exception.ResourceNotFoundException;
import com.flockenergy.meterops.service.MeterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeterController.class)
class MeterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeterService meterService;

    @Test
    void listMeters_returnsPagedResult() throws Exception {
        var meter = new MeterResponse(
                "J100000", "SE33962", "HPL", "single",
                "Installed", "Whole Current", "legacy", "DT-001",
                null, null);
        var page = new PageResponse<>(List.of(meter), 1, 1, 20, 1);

        when(meterService.listMeters(null, 1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/meters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].meterId").value("J100000"));
    }

    @Test
    void getMeterDetail_returnsNotFoundForUnknown() throws Exception {
        when(meterService.getMeterDetail("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Meter not found: UNKNOWN"));

        mockMvc.perform(get("/api/meters/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Meter not found: UNKNOWN"));
    }

    @Test
    void getMeterConsumption_returnsReadings() throws Exception {
        var reading = new ConsumptionReading("24/06/2026 00:00", "6850.78", "7398.84", "236");
        when(meterService.getConsumption("J100002")).thenReturn(List.of(reading));

        mockMvc.perform(get("/api/meters/J100002/consumption"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meterId").value("J100002"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.readings[0].kwh").value("6850.78"));
    }

    @Test
    void listMeters_badPageParameter_returns400() throws Exception {
        mockMvc.perform(get("/api/meters").param("page", "0"))
                .andExpect(status().isBadRequest());
    }
}
