package com.ecommerce.product;
import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.entity.*;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.*;
import com.ecommerce.product.service.impl.ProductServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository products;
    @Mock InventoryRepository inventories;
    ProductServiceImpl service;
    Product product;
    @BeforeEach void setup() {
        service = new ProductServiceImpl(products, inventories, new ProductMapper());
        product = new Product("MOBILE-001", "Samsung", "Phone", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(product, "id", 1L);
    }
    ProductCreateRequest request() { return new ProductCreateRequest("mobile-001", "Samsung", "Phone", new BigDecimal("100"), 10); }
    @Test void createProduct_success() {
        when(products.saveAndFlush(any())).thenReturn(product);
        assertThat(service.createProduct(request()).sku()).isEqualTo("MOBILE-001");
        ArgumentCaptor<Inventory> inventory = ArgumentCaptor.forClass(Inventory.class);
        verify(inventories).save(inventory.capture());
        assertThat(inventory.getValue().getAvailableQuantity()).isEqualTo(10);
    }
    @Test void createProduct_duplicateSku() {
        when(products.existsBySku("MOBILE-001")).thenReturn(true);
        assertThatThrownBy(() -> service.createProduct(request())).isInstanceOf(DuplicateSkuException.class);
        verifyNoInteractions(inventories);
    }
    @Test void createProduct_invalidPrice() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new ProductCreateRequest("SKU", "Name", null, BigDecimal.ZERO, 0)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("price"));
        }
    }
    @Test void getProduct_success() {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        assertThat(service.getProductById(1L).name()).isEqualTo("Samsung");
    }
    @Test void getProduct_notFound() {
        assertThatThrownBy(() -> service.getProductById(1L)).isInstanceOf(ProductNotFoundException.class);
    }
    @Test void deletedProduct_notFound() {
        product.delete(); when(products.findById(1L)).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.getProductById(1L)).isInstanceOf(ProductNotFoundException.class);
    }
    @Test void updateProduct_success() {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        var result = service.updateProduct(1L, new ProductUpdateRequest("Ultra", "Updated", new BigDecimal("120")));
        assertThat(result.name()).isEqualTo("Ultra");
        assertThat(result.sku()).isEqualTo("MOBILE-001");
    }
    @Test void deleteProduct_success() {
        when(products.findById(1L)).thenReturn(Optional.of(product)); service.deleteProduct(1L);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
        verify(products, never()).delete(any());
    }
    @Test void searchProducts_success() {
        var pageable = PageRequest.of(0, 10);
        when(products.search("sam", pageable)).thenReturn(new PageImpl<>(List.of(product), pageable, 1));
        assertThat(service.searchProducts("sam", pageable).totalElements()).isEqualTo(1);
    }
    @Test void searchProducts_escapesWildcards() {
        var pageable = PageRequest.of(0, 10);
        when(products.search("!%!_!!", pageable)).thenReturn(Page.empty(pageable));
        service.searchProducts("%_!", pageable);
        verify(products).search("!%!_!!", pageable);
    }
}

