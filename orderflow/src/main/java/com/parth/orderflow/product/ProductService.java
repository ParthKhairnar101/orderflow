package com.parth.orderflow.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        return create(request, false);
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, boolean demoMode) {
        if (products.existsBySkuAndDemoMode(request.sku(), demoMode)) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = Product.create(
                request.sku(),
                request.name(),
                request.description(),
                request.unitPrice(),
                request.availableQuantity() == null ? 0 : request.availableQuantity(),
                demoMode
        );

        // The database unique constraint remains authoritative when concurrent
        // requests both pass the friendly pre-check above.
        return ProductResponse.from(products.saveAndFlush(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return get(id, false);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id, boolean demoMode) {
        return ProductResponse.from(findProduct(id, demoMode));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return list(false);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(boolean demoMode) {
        return products.findAllByDemoModeOrderBySkuAsc(demoMode).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse setInventory(UUID id, int availableQuantity) {
        return setInventory(id, availableQuantity, false);
    }

    @Transactional
    public ProductResponse setInventory(UUID id, int availableQuantity, boolean demoMode) {
        Product product = findProduct(id, demoMode);
        product.changeAvailableQuantity(availableQuantity);
        // Flush here so an optimistic-lock conflict becomes a 409 response
        // while this service method is still handling the request.
        return ProductResponse.from(products.saveAndFlush(product));
    }

    private Product findProduct(UUID id, boolean demoMode) {
        return products.findByIdAndDemoMode(id, demoMode).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
