package com.ecommerce.product;
import com.ecommerce.product.config.*;
import com.ecommerce.product.controller.ProductController;
import com.ecommerce.product.dto.response.*;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService service;
    @MockitoBean JwtDecoder decoder;
    static final String CREATE = """
        {"sku":"MOBILE-001","name":"Samsung","description":"Phone","price":100.00,"initialQuantity":10}
        """;
    ProductResponse response() {
        return new ProductResponse(1L, "MOBILE-001", "Samsung", "Phone", BigDecimal.TEN, ProductStatus.ACTIVE, null, null);
    }
    @Test void get_200() throws Exception {
        when(service.getProductById(1L)).thenReturn(response());
        mvc.perform(get("/api/products/1").header("X-Correlation-Id", "test-123"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("MOBILE-001"))
            .andExpect(header().string("X-Correlation-Id", "test-123"));
    }
    @Test void create_201() throws Exception {
        when(service.createProduct(any())).thenReturn(response());
        mvc.perform(post("/api/products").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content(CREATE))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/products/1"));
    }
    @Test void update_200() throws Exception {
        when(service.updateProduct(eq(1L), any())).thenReturn(response());
        mvc.perform(put("/api/products/1").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Samsung\",\"price\":10}"))
            .andExpect(status().isOk());
    }
    @Test void delete_204() throws Exception {
        mvc.perform(delete("/api/products/1").with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))))
            .andExpect(status().isNoContent());
    }
    @Test void invalidPrice_400() throws Exception {
        mvc.perform(post("/api/products").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content(CREATE.replace("100.00", "0")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.price").exists());
        verifyNoInteractions(service);
    }
    @Test void create_401() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(CREATE))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
    @Test void create_403() throws Exception {
        mvc.perform(post("/api/products").with(jwt().authorities(createAuthorityList("ROLE_SERVICE")))
            .contentType(MediaType.APPLICATION_JSON).content(CREATE)).andExpect(status().isForbidden());
    }
    @Test void get_404() throws Exception {
        when(service.getProductById(1L)).thenThrow(new ProductNotFoundException(1L));
        mvc.perform(get("/api/products/1")).andExpect(status().isNotFound()).andExpect(jsonPath("$.correlationId").exists());
    }
    @Test void duplicateSku_409() throws Exception {
        when(service.createProduct(any())).thenThrow(new DuplicateSkuException("MOBILE-001"));
        mvc.perform(post("/api/products").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content(CREATE)).andExpect(status().isConflict());
    }
    @Test void invalidPaginationAndSort_400() throws Exception {
        mvc.perform(get("/api/products?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?sort=password,asc")).andExpect(status().isBadRequest());
    }
    @Test void listAndSearch_200() throws Exception {
        var page = new PageResponse<>(List.of(response()), 0, 10, 1, 1, true);
        when(service.getAllProducts(any())).thenReturn(page);
        when(service.searchProducts(eq("sam"), any())).thenReturn(page);
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/products/search?keyword=sam")).andExpect(status().isOk());
    }
    @Test void invalidToken_401() throws Exception {
        when(decoder.decode("bad")).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("Invalid"));
        mvc.perform(get("/api/products/1").header("Authorization", "Bearer bad"))
            .andExpect(status().isUnauthorized());
    }
    @Test void rolesClaimIsMapped() throws Exception {
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("admin-token").header("alg", "RS256")
            .subject("admin").claim("roles", List.of("ADMIN")).build();
        when(decoder.decode("admin-token")).thenReturn(token);
        when(service.createProduct(any())).thenReturn(response());
        mvc.perform(post("/api/products").header("Authorization", "Bearer admin-token")
            .contentType(MediaType.APPLICATION_JSON).content(CREATE)).andExpect(status().isCreated());
    }
    @Test void skuUpdateRejected_400() throws Exception {
        mvc.perform(put("/api/products/1").with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"CHANGED\",\"name\":\"Phone\",\"price\":10}"))
            .andExpect(status().isBadRequest());
    }
}

