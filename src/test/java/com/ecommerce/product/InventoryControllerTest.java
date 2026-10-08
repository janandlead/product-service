package com.ecommerce.product;
import com.ecommerce.product.config.*;
import com.ecommerce.product.controller.InventoryController;
import com.ecommerce.product.dto.response.*;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
class InventoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryService service;
    @MockitoBean JwtDecoder decoder;
    static final String RESERVE = "{\"orderId\":5001,\"items\":[{\"productId\":1,\"quantity\":2}]}";
    @Test void reserve_200() throws Exception {
        when(service.reserveInventory(any())).thenReturn(new InventoryReservationResponse(5001L, List.of()));
        mvc.perform(post("/internal/api/inventory/reserve").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content(RESERVE)).andExpect(status().isOk());
    }
    @Test void reserve_400() throws Exception {
        mvc.perform(post("/internal/api/inventory/reserve").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content(RESERVE.replace("\"quantity\":2", "\"quantity\":0")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void reserve_401() throws Exception {
        mvc.perform(post("/internal/api/inventory/reserve").contentType(MediaType.APPLICATION_JSON).content(RESERVE))
            .andExpect(status().isUnauthorized());
    }
    @Test void adminCannotReserve_403() throws Exception {
        mvc.perform(post("/internal/api/inventory/reserve").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content(RESERVE)).andExpect(status().isForbidden());
    }
    @Test void insufficientStock_409() throws Exception {
        when(service.reserveInventory(any())).thenThrow(new InsufficientStockException(1L, 1, 2));
        mvc.perform(post("/internal/api/inventory/reserve").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content(RESERVE)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.availableQuantity").value(1)).andExpect(jsonPath("$.requestedQuantity").value(2));
    }
    @Test void optimisticConflict_409() throws Exception {
        when(service.reserveInventory(any())).thenThrow(new OptimisticLockingFailureException("version"));
        mvc.perform(post("/internal/api/inventory/reserve").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content(RESERVE)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONCURRENT_INVENTORY_CONFLICT"));
    }
    @Test void inventory_404() throws Exception {
        when(service.getInventory(1L)).thenThrow(new ProductNotFoundException(1L));
        mvc.perform(get("/internal/api/inventory/1").with(jwt().authorities(createAuthorityList("ROLE_SERVICE"))))
            .andExpect(status().isNotFound());
    }
    @Test void adjustmentRequiresAdmin() throws Exception {
        when(service.adjustInventory(eq(1L), any())).thenReturn(new InventoryResponse(1L, 20, 0, 20));
        String body = "{\"quantityChange\":10,\"reason\":\"Restock\"}";
        mvc.perform(patch("/internal/api/inventory/1/adjust").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/internal/api/inventory/1/adjust").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
    @Test void releaseAndConfirm_200() throws Exception {
        mvc.perform(post("/internal/api/inventory/release").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5001}")).andExpect(status().isOk());
        mvc.perform(post("/internal/api/inventory/confirm").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5001}")).andExpect(status().isOk());
    }
    @Test void inventory_200() throws Exception {
        when(service.getInventory(1L)).thenReturn(new InventoryResponse(1L, 10, 2, 8));
        mvc.perform(get("/internal/api/inventory/1").with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.availableQuantity").value(8));
    }
}

