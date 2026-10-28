package com.flockenergy.meterops.controller;

import com.flockenergy.meterops.dto.PageResponse;
import com.flockenergy.meterops.dto.TransformerResponse;
import com.flockenergy.meterops.service.TransformerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransformerController.class)
class TransformerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransformerService transformerService;

    @Test
    void listTransformers_returnsPaged() throws Exception {
        var dt = new TransformerResponse("DT-001", "Malviya Nagar DT 1", "F-001", 100);
        var page = new PageResponse<>(List.of(dt), 1, 1, 20, 1);

        when(transformerService.listTransformers(1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/transformers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("DT-001"))
                .andExpect(jsonPath("$.data[0].capacityKva").value(100));
    }
}
