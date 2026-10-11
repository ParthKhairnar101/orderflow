package com.parth.orderflow;

import com.parth.orderflow.order.CreateOrderItemRequest;
import com.parth.orderflow.order.CreateOrderRequest;
import com.parth.orderflow.order.ConcurrentOrderUpdateException;
import com.parth.orderflow.order.OrderRepository;
import com.parth.orderflow.order.OrderResponse;
import com.parth.orderflow.order.OrderService;
import com.parth.orderflow.product.InsufficientInventoryException;
import com.parth.orderflow.product.Product;
import com.parth.orderflow.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderflowIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("orderflow_test")
            .withUsername("orderflow")
            .withPassword("orderflow_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE demo_activity SET last_activity_at = CURRENT_TIMESTAMP WHERE id = 1");
    }

    @Test
    void createsReadsAndReplaysSameIdempotencyKeyWithoutReservingTwice() throws Exception {
        Product product = product("SKU-ORDER-1", "29.95", 10);
        CreateOrderRequest request = request(product, 3);

        MvcResult created = postOrder("order-key-1", request, "operator");
        assertEquals(201, created.getResponse().getStatus());
        String id = field(created, "id");
        assertTrue(created.getResponse().getContentAsString().contains("\"totalAmount\":89.85"));
        assertTrue(created.getResponse().getContentAsString().contains("\"status\":\"CREATED\""));
        assertEquals(7, available(product));

        MvcResult replay = postOrder("order-key-1", request, "operator");
        assertEquals(201, replay.getResponse().getStatus());
        assertEquals(id, field(replay, "id"));
        assertEquals(1, orders.count());
        assertEquals(7, available(product));

        MvcResult fetched = mvc.perform(get("/api/v1/orders/{id}", id).with(httpBasic("operator", "operator-local-only")))
                .andReturn();
        assertEquals(200, fetched.getResponse().getStatus());
        assertTrue(fetched.getResponse().getContentAsString().contains(product.getSku()));

        MvcResult listed = mvc.perform(get("/api/v1/orders").with(httpBasic("admin", "admin-local-only")))
                .andReturn();
        assertEquals(200, listed.getResponse().getStatus());
        assertTrue(listed.getResponse().getContentAsString().contains(id));
    }

    @Test
    void rejectsChangedPayloadForUsedKeyAndInvalidOrderLines() throws Exception {
        Product product = product("SKU-ORDER-2", "5.00", 10);
        MvcResult created = postOrder("order-key-2", request(product, 2), "admin");
        String id = field(created, "id");

        assertEquals(409, postOrder("order-key-2", request(product, 3), "admin")
                .getResponse().getStatus());
        assertEquals(8, available(product));
        assertEquals(400, postOrder(null, request(product, 1), "admin").getResponse().getStatus());
        assertEquals(400, postOrder("invalid-quantity", request(product, 0), "admin")
                .getResponse().getStatus());

        CreateOrderRequest duplicateLines = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(product.getId(), 1),
                new CreateOrderItemRequest(product.getId(), 1)));
        assertEquals(400, postOrder("duplicate-lines", duplicateLines, "admin").getResponse().getStatus());
        CreateOrderRequest unknownProduct = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(UUID.randomUUID(), 1)));
        assertEquals(404, postOrder("missing-product", unknownProduct, "admin").getResponse().getStatus());
        assertEquals(1, orders.count());
        assertNotNull(orders.findById(UUID.fromString(id)).orElseThrow());
    }

    @Test
    void insufficientInventoryRollsBackEarlierReservationsAndDoesNotPersistOrder() throws Exception {
        Product one = product("SKU-ROLLBACK-A", "10.00", 1);
        Product two = product("SKU-ROLLBACK-B", "20.00", 1);
        List<Product> sorted = List.of(one, two).stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
        sorted.get(0).changeAvailableQuantity(10);
        sorted.get(1).changeAvailableQuantity(1);
        products.saveAllAndFlush(sorted);

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(sorted.get(0).getId(), 2),
                new CreateOrderItemRequest(sorted.get(1).getId(), 2)));
        MvcResult response = postOrder("rollback-key", request, "admin");

        assertEquals(409, response.getResponse().getStatus());
        assertEquals(0, orders.count());
        assertEquals(10, available(sorted.get(0)));
        assertEquals(1, available(sorted.get(1)));
    }

    @Test
    void cancelRestoresInventoryOnlyOnceAndUnknownOrderIsNotFound() throws Exception {
        Product product = product("SKU-CANCEL-1", "7.50", 8);
        String id = field(postOrder("cancel-key", request(product, 3), "operator"), "id");
        assertEquals(5, available(product));

        MvcResult cancelled = mvc.perform(post("/api/v1/orders/{id}/cancel", id)
                        .with(httpBasic("operator", "operator-local-only")))
                .andReturn();
        assertEquals(200, cancelled.getResponse().getStatus());
        assertTrue(cancelled.getResponse().getContentAsString().contains("\"status\":\"CANCELLED\""));
        assertTrue(cancelled.getResponse().getContentAsString().contains("\"cancelledAt\":"));
        assertEquals(8, available(product));

        MvcResult repeated = mvc.perform(post("/api/v1/orders/{id}/cancel", id)
                        .with(httpBasic("admin", "admin-local-only")))
                .andReturn();
        assertEquals(200, repeated.getResponse().getStatus());
        assertEquals(8, available(product));

        MvcResult missing = mvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID())
                        .with(httpBasic("admin", "admin-local-only")))
                .andReturn();
        assertEquals(404, missing.getResponse().getStatus());
    }

    @Test
    void orderRoutesEnforceAuthenticationAndAllowOperatorToCreateAndCancel() throws Exception {
        assertEquals(401, mvc.perform(get("/api/v1/orders")).andReturn().getResponse().getStatus());
        Product product = product("SKU-SECURITY-1", "1.00", 2);
        MvcResult created = postOrder("security-key", request(product, 1), "operator");
        assertEquals(201, created.getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/v1/orders/{id}/cancel", field(created, "id"))
                        .with(httpBasic("operator", "operator-local-only")))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void productRoutesCoverCreateReadInventoryValidationAndRoleChecks() throws Exception {
        assertEquals(200, mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus());
        String body = "{\"sku\":\"SKU-PRODUCT-API\",\"name\":\"API Product\","
                + "\"description\":\"Product endpoint test\",\"unitPrice\":12.50,"
                + "\"availableQuantity\":20}";
        MvcResult created = mvc.perform(post("/api/v1/products")
                        .with(httpBasic("admin", "admin-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertEquals(201, created.getResponse().getStatus());
        String id = field(created, "id");

        assertEquals(409, mvc.perform(post("/api/v1/products")
                        .with(httpBasic("admin", "admin-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/v1/products")
                        .with(httpBasic("operator", "operator-local-only")))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/v1/products/{id}", id)
                        .with(httpBasic("admin", "admin-local-only")))
                .andReturn().getResponse().getStatus());

        MvcResult updated = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/products/{id}/inventory", id)
                        .with(httpBasic("admin", "admin-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availableQuantity\":14}"))
                .andReturn();
        assertEquals(200, updated.getResponse().getStatus());
        assertTrue(updated.getResponse().getContentAsString().contains("\"availableQuantity\":14"));
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/products/{id}/inventory", id)
                        .with(httpBasic("operator", "operator-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availableQuantity\":15}"))
                .andReturn().getResponse().getStatus());
        String invalidProduct = "{\"sku\":\"SKU-PRODUCT-INVALID\",\"name\":\"Invalid\","
                + "\"unitPrice\":-1,\"availableQuantity\":0}";
        assertEquals(400, mvc.perform(post("/api/v1/products")
                        .with(httpBasic("admin", "admin-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalidProduct))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/products/{id}/inventory", id)
                        .with(httpBasic("admin", "admin-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availableQuantity\":-1}"))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/v1/products/{id}", UUID.randomUUID())
                        .with(httpBasic("admin", "admin-local-only")))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void servesPortfolioFrontendWithoutOpeningProtectedApiRoutes() throws Exception {
        MvcResult page = mvc.perform(get("/")).andReturn();
        assertEquals(200, page.getResponse().getStatus());
        assertEquals("index.html", page.getResponse().getForwardedUrl());
        assertTrue(mvc.perform(get("/index.html")).andReturn().getResponse()
                .getContentAsString().contains("id=\"app-view\""));

        MvcResult stylesheet = mvc.perform(get("/styles.css")).andReturn();
        assertEquals(200, stylesheet.getResponse().getStatus());
        assertTrue(stylesheet.getResponse().getContentAsString().contains("--shadow:0 8px 30px rgba(31,46,37,.045)"));
        assertTrue(stylesheet.getResponse().getContentAsString().contains(".sidebar{position:fixed"));
        assertEquals(200, mvc.perform(get("/app.js")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/v1/products")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/v1/session")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/v1/demo/status")).andReturn().getResponse().getStatus());
        assertTrue(mvc.perform(get("/api/v1/session").with(httpBasic("admin", "admin-local-only")))
                .andReturn().getResponse().getContentAsString().contains("\"administrator\":true"));
        assertTrue(mvc.perform(get("/api/v1/session").with(httpBasic("operator", "operator-local-only")))
                .andReturn().getResponse().getContentAsString().contains("\"administrator\":false"));
    }

    @Test
    void publicDemoIsAnonymousAndIsolatedFromRoleProtectedApiData() throws Exception {
        MvcResult demoCreated = mvc.perform(post("/api/v1/demo/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SHARED-SKU\",\"name\":\"Public demo item\","
                                + "\"unitPrice\":12.00,\"availableQuantity\":6}"))
                .andReturn();
        assertEquals(201, demoCreated.getResponse().getStatus());
        String demoId = field(demoCreated, "id");

        String privateBody = "{\"sku\":\"SHARED-SKU\",\"name\":\"Private item\","
                + "\"unitPrice\":99.00,\"availableQuantity\":1}";
        MvcResult privateCreated = mvc.perform(post("/api/v1/products")
                        .with(httpBasic("admin", "admin-local-only"))
                        .contentType(MediaType.APPLICATION_JSON).content(privateBody))
                .andReturn();
        assertEquals(201, privateCreated.getResponse().getStatus());

        String publicProducts = mvc.perform(get("/api/v1/demo/products")).andReturn()
                .getResponse().getContentAsString();
        String privateProducts = mvc.perform(get("/api/v1/products")
                        .with(httpBasic("admin", "admin-local-only"))).andReturn()
                .getResponse().getContentAsString();
        assertTrue(publicProducts.contains(demoId));
        assertTrue(publicProducts.contains("Public demo item"));
        assertTrue(!publicProducts.contains("Private item"));
        assertTrue(privateProducts.contains("Private item"));
        assertTrue(!privateProducts.contains("Public demo item"));
        assertEquals(200, mvc.perform(get("/api/v1/demo/products"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void demoOrdersReserveAndRestoreOnlyDemoInventory() throws Exception {
        MvcResult createdProduct = mvc.perform(post("/api/v1/demo/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"DEMO-ORDER-ITEM\",\"name\":\"Demo order item\","
                                + "\"unitPrice\":10.00,\"availableQuantity\":5}"))
                .andReturn();
        UUID productId = UUID.fromString(field(createdProduct, "id"));
        String body = "{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":2}]}";

        MvcResult order = mvc.perform(post("/api/v1/demo/orders")
                        .header("Idempotency-Key", "public-demo-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertEquals(201, order.getResponse().getStatus());
        String orderId = field(order, "id");
        assertTrue(order.getResponse().getContentAsString().contains("\"totalAmount\":20.00"));

        assertEquals(404, mvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(httpBasic("admin", "admin-local-only")))
                .andReturn().getResponse().getStatus());
        assertTrue(mvc.perform(get("/api/v1/orders").with(httpBasic("admin", "admin-local-only")))
                .andReturn().getResponse().getContentAsString().equals("[]"));

        assertEquals(200, mvc.perform(post("/api/v1/demo/orders/{id}/cancel", orderId))
                .andReturn().getResponse().getStatus());
        assertEquals(5, jdbc.queryForObject(
                "SELECT available_quantity FROM products WHERE id = ? AND demo_mode = TRUE", Integer.class,
                productId));
    }

    @Test
    void idleDemoExpiryDeletesChangesAndRestoresTheSeedCatalog() throws Exception {
        mvc.perform(post("/api/v1/demo/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TEMP-ITEM\",\"name\":\"Temporary item\","
                                + "\"unitPrice\":2.00,\"availableQuantity\":1}"))
                .andReturn();
        jdbc.update("UPDATE demo_activity SET last_activity_at = "
                + "CURRENT_TIMESTAMP - INTERVAL '21 minutes' WHERE id = 1");

        MvcResult resetCatalog = mvc.perform(get("/api/v1/demo/products")).andReturn();
        assertEquals(200, resetCatalog.getResponse().getStatus());
        String productsJson = resetCatalog.getResponse().getContentAsString();
        assertTrue(productsJson.contains("DEMO-KEY-1001"));
        assertTrue(productsJson.contains("DEMO-LAMP-2001"));
        assertTrue(productsJson.contains("DEMO-HUB-3001"));
        assertTrue(!productsJson.contains("Temporary item"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE demo_mode = TRUE", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE demo_mode = TRUE", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_items oi "
                + "JOIN orders o ON oi.order_id = o.id WHERE o.demo_mode = TRUE", Integer.class));
    }

    @Test
    void onlyOneConcurrentOrderCanReserveTheLastUnit() throws Exception {
        Product product = product("SKU-CONCURRENT-1", "15.00", 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreateOutcome> first = executor.submit(() -> concurrentCreate(ready, start, "concurrent-a", product));
            Future<CreateOutcome> second = executor.submit(() -> concurrentCreate(ready, start, "concurrent-b", product));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<CreateOutcome> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(outcome -> outcome.response() != null).count());
            assertEquals(1, outcomes.stream().filter(outcome -> outcome.failure() != null).count());
            assertTrue(outcomes.stream().filter(outcome -> outcome.failure() != null)
                    .anyMatch(outcome -> outcome.failure() instanceof InsufficientInventoryException
                            || outcome.failure() instanceof ConcurrentOrderUpdateException));
            assertEquals(0, available(product));
            assertEquals(1, orders.count());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentRetriesWithSameKeyReturnSameOrderAndReserveOnce() throws Exception {
        Product product = product("SKU-CONCURRENT-IDEMPOTENT", "8.00", 2);
        CreateOrderRequest request = request(product, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreateOutcome> first = executor.submit(() -> concurrentCreate(ready, start, "same-key", request));
            Future<CreateOutcome> second = executor.submit(() -> concurrentCreate(ready, start, "same-key", request));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            OrderResponse one = first.get(30, TimeUnit.SECONDS).response();
            OrderResponse two = second.get(30, TimeUnit.SECONDS).response();

            assertNotNull(one);
            assertNotNull(two);
            assertEquals(one.id(), two.id());
            assertEquals(1, orders.count());
            assertEquals(1, available(product));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private CreateOutcome concurrentCreate(CountDownLatch ready, CountDownLatch start,
                                           String key, Product product) {
        return concurrentCreate(ready, start, key, request(product, 1));
    }

    private CreateOutcome concurrentCreate(CountDownLatch ready, CountDownLatch start,
                                           String key, CreateOrderRequest request) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start in time.");
            }
            return new CreateOutcome(orderService.create(key, request), null);
        } catch (RuntimeException exception) {
            return new CreateOutcome(null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CreateOutcome(null, new IllegalStateException("Concurrent request was interrupted.", exception));
        }
    }

    private Product product(String sku, String price, int quantity) {
        return products.saveAndFlush(Product.create(sku, sku + " name", null,
                new BigDecimal(price), quantity));
    }

    private int available(Product product) {
        return products.findById(product.getId()).orElseThrow().getAvailableQuantity();
    }

    private CreateOrderRequest request(Product product, int quantity) {
        return new CreateOrderRequest(List.of(new CreateOrderItemRequest(product.getId(), quantity)));
    }

    private MvcResult postOrder(String key, CreateOrderRequest request, String username) throws Exception {
        StringBuilder body = new StringBuilder("{\"items\":[");
        for (int index = 0; index < request.items().size(); index++) {
            if (index > 0) body.append(',');
            CreateOrderItemRequest item = request.items().get(index);
            body.append("{\"productId\":\"").append(item.productId())
                    .append("\",\"quantity\":").append(item.quantity()).append('}');
        }
        body.append("]}");

        var builder = post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body.toString());
        if (key != null) builder.header("Idempotency-Key", key);
        if (username != null) builder.with(httpBasic(username,
                username.equals("admin") ? "admin-local-only" : "operator-local-only"));
        return mvc.perform(builder).andReturn();
    }

    private String field(MvcResult result, String name) throws Exception {
        String json = result.getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\\\"" + name + "\\\":\\\"([^\\\"]+)\\\"")
                .matcher(json);
        assertTrue(matcher.find(), "Expected field '" + name + "' in response: " + json);
        return matcher.group(1);
    }

    private record CreateOutcome(OrderResponse response, RuntimeException failure) {
    }
}
