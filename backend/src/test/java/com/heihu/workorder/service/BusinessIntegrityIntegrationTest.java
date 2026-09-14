package com.heihu.workorder.service;

import com.heihu.workorder.dto.InboundReceiptRequest;
import com.heihu.workorder.dto.InboundReceiptResponse;
import com.heihu.workorder.dto.OutboundReceiptRequest;
import com.heihu.workorder.dto.OutboundReceiptResponse;
import com.heihu.workorder.dto.ProductRequest;
import com.heihu.workorder.dto.WorkOrderReportRequest;
import com.heihu.workorder.dto.WorkOrderRequest;
import com.heihu.workorder.enums.TimeRange;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.model.WorkOrderReport;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.InboundReceiptRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UnitRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses a private in-memory database and real service transactions, never the configured business DB. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:business_integrity_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_TIMEOUT=10000",
        "spring.cache.type=none",
        "logging.file.name=output/business-integrity-test.log"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BusinessIntegrityIntegrationTest {
    @Autowired private FactoryRepository factories;
    @Autowired private UserRepository users;
    @Autowired private UnitRepository units;
    @Autowired private ProductRepository products;
    @Autowired private WorkOrderRepository orders;
    @Autowired private WorkOrderReportRepository reports;
    @Autowired private InboundReceiptRepository inbounds;
    @Autowired private OutboundReceiptRepository outbounds;
    @Autowired private InboundReceiptService inboundService;
    @Autowired private OutboundReceiptService outboundService;
    @Autowired private WorkOrderReportService reportService;
    @Autowired private WorkOrderService orderService;
    @Autowired private ProductService productService;
    @Autowired private HomeDashboardService dashboard;

    private Factory factory;
    private FactoryUser actor;
    private Product product;
    private WorkOrder order;

    @BeforeEach
    void fixtures() {
        factory = createFactory();
        actor = new FactoryUser();
        actor.setFactory(factory);
        actor.setUsername("worker");
        actor.setName("Worker");
        actor.setRole(Role.ADMIN);
        actor.setEnabled(true);
        actor.setPasswordHash("test-only-unused-hash");
        actor.setCreatedAt(LocalDateTime.now());
        actor.setUpdatedAt(LocalDateTime.now());
        actor = users.saveAndFlush(actor);
        product = createProduct(factory, 0);
        order = createOrder("EXECUTING", 100);
    }

    @RepeatedTest(3)
    void concurrentApprovalAndRejectionHaveExactlyOneWinner() throws Exception {
        Long id = reportService.createReport(factory.getId(), actor.getId(), reportRequest()).getId();
        List<Throwable> results = together(
                () -> reportService.approve(factory.getId(), actor.getId(), id, null),
                () -> reportService.reject(factory.getId(), actor.getId(), id, null));
        assertThat(results.stream().filter(value -> value == null).count()).isEqualTo(1);
        WorkOrderReport saved = reports.findById(id).get();
        int expected = saved.getStatus() == ReportStatus.APPROVED ? 4 : 0;
        assertThat(orders.findById(order.getId()).get().getGoodQuantity()).isEqualTo(expected);
        assertThat(saved.getStatus()).isIn(ReportStatus.APPROVED, ReportStatus.REJECTED);
    }

    @Test
    void concurrentApprovalsOfDifferentReportsDoNotLoseQuantities() throws Exception {
        Long first = reportService.createReport(factory.getId(), actor.getId(), reportRequest()).getId();
        Long second = reportService.createReport(factory.getId(), actor.getId(), reportRequest()).getId();
        assertThat(together(
                () -> reportService.approve(factory.getId(), actor.getId(), first, null),
                () -> reportService.approve(factory.getId(), actor.getId(), second, null)))
                .containsOnlyNulls();
        assertThat(orders.findById(order.getId()).get().getGoodQuantity()).isEqualTo(8);
        assertThat(reports.findById(first).get().getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(reports.findById(second).get().getStatus()).isEqualTo(ReportStatus.APPROVED);
    }

    @Test
    void concurrentInboundCreationUsesUniqueCodesAndAddsBothQuantities() throws Exception {
        assertThat(together(
                () -> inboundService.create(factory.getId(), actor.getId(), inbound(product.getId(), 3, null)),
                () -> inboundService.create(factory.getId(), actor.getId(), inbound(product.getId(), 7, null))))
                .containsOnlyNulls();
        assertThat(stock()).isEqualTo(10);
        assertThat(inbounds.findByFactoryId(factory.getId(), Pageable.unpaged()).getTotalElements()).isEqualTo(2);
    }

    @Test
    void concurrentDeletionOfOneOutboundReceiptRestoresStockOnlyOnce() throws Exception {
        inboundService.create(factory.getId(), actor.getId(), inbound(product.getId(), 20, null));
        Long id = outboundService.create(factory.getId(), actor.getId(), outbound(product.getId(), 5, null)).getId();
        List<Throwable> results = together(
                () -> { outboundService.delete(factory.getId(), id); return null; },
                () -> { outboundService.delete(factory.getId(), id); return null; });
        assertThat(results.stream().filter(value -> value == null).count()).isEqualTo(1);
        assertThat(stock()).isEqualTo(20);
        assertThat(outbounds.existsById(id)).isFalse();
    }

    @Test
    void concurrentDeletionOfOneInboundReceiptRevertsStockOnlyOnce() throws Exception {
        inboundService.create(factory.getId(), actor.getId(), inbound(product.getId(), 40, null));
        Long id = inboundService.create(factory.getId(), actor.getId(), inbound(product.getId(), 10, null)).getId();
        List<Throwable> results = together(
                () -> { inboundService.delete(factory.getId(), id); return null; },
                () -> { inboundService.delete(factory.getId(), id); return null; });
        assertThat(results.stream().filter(value -> value == null).count()).isEqualTo(1);
        assertThat(stock()).isEqualTo(40);
        assertThat(inbounds.existsById(id)).isFalse();
    }

    @Test
    void aLaterInvalidLineRollsBackAllInventoryChanges() {
        Product full = createProduct(factory, Integer.MAX_VALUE);
        InboundReceiptRequest request = inbound(product.getId(), 1, "rollback");
        request.setItems(Arrays.asList(request.getItems().get(0), inbound(full.getId(), 1, null).getItems().get(0)));
        assertThatThrownBy(() -> inboundService.create(factory.getId(), actor.getId(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("库存数量");
        assertThat(stock()).isZero();
        assertThat(products.findById(full.getId()).get().getInventoryQty()).isEqualTo(Integer.MAX_VALUE);
        assertThat(inbounds.findByFactoryIdAndCode(factory.getId(), "rollback")).isEmpty();
    }

    @Test
    void aStaleProductFormCannotOverwriteNewInventoryAndResponsesCarryCurrentVersion() {
        Long previousVersion = products.findById(product.getId()).get().getVersion();
        inboundService.create(factory.getId(), actor.getId(), inbound(product.getId(), 10, null));
        ProductRequest request = new ProductRequest();
        request.setName("Renamed");
        request.setUnitId(product.getUnit().getId());
        request.setVersion(previousVersion);
        request.setInventoryQty(0);
        assertThatThrownBy(() -> productService.update(factory.getId(), actor.getId(), "Worker", product.getId(), request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(stock()).isEqualTo(10);
        Product current = products.findById(product.getId()).get();
        request.setVersion(current.getVersion());
        request.setInventoryQty(null);
        assertThat(productService.update(factory.getId(), actor.getId(), "Worker", product.getId(), request).getVersion())
                .isGreaterThan(current.getVersion());
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void receiptCodesAreUniqueWithinAFactoryButNotGlobally() {
        Factory otherFactory = createFactory();
        Product other = createProduct(otherFactory, 0);
        InboundReceiptResponse first = inboundService.create(factory.getId(), null, inbound(product.getId(), 10, "SHARED"));
        InboundReceiptResponse second = inboundService.create(otherFactory.getId(), null, inbound(other.getId(), 10, "SHARED"));
        assertThat(first.getId()).isNotEqualTo(second.getId());
        OutboundReceiptResponse firstOut = outboundService.create(factory.getId(), null, outbound(product.getId(), 1, "SHARED"));
        OutboundReceiptResponse secondOut = outboundService.create(otherFactory.getId(), null, outbound(other.getId(), 1, "SHARED"));
        assertThat(firstOut.getId()).isNotEqualTo(secondOut.getId());
        assertThatThrownBy(() -> inboundService.create(factory.getId(), null, inbound(product.getId(), 10, "SHARED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已存在");
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void lastMonthReportsExcludeTheCurrentMonthAtTheExactBoundary() {
        LocalDateTime thisMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        WorkOrderReport first = createReport(thisMonth.minusMonths(1));
        WorkOrderReport last = createReport(thisMonth.minusNanos(1000));
        createReport(thisMonth);
        assertThat(reportService.listMobile(factory.getId(), actor.getId(), null, "lastMonth", PageRequest.of(0, 20)).getContent())
                .extracting("id").containsExactlyInAnyOrder(first.getId(), last.getId());
        assertThat(reportService.listAdmin(factory.getId(), null, null, null, "lastMonth", PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(2);
    }

    @Test
    void dashboardUnderstandsStatusAliasesAndExcludesAllTerminalStatesFromOverdue() {
        LocalDateTime now = LocalDateTime.now();
        order.setPlanEnd(now.minusHours(1));
        orders.saveAndFlush(order);
        WorkOrder legacy = createOrder("IN_PROGRESS", 100);
        legacy.setPlanEnd(now.minusHours(1));
        orders.saveAndFlush(legacy);
        for (String status : Arrays.asList("COMPLETED", "FINISHED", "CLOSED", "CANCELED", "CANCELLED")) {
            WorkOrder terminal = createOrder(status, 100);
            terminal.setPlanEnd(now.minusHours(1));
            orders.saveAndFlush(terminal);
        }
        assertThat(orders.findInProgress(factory.getId(), Pageable.unpaged())).hasSize(2);
        assertThat(orders.findOverdueUnfinished(factory.getId(), now, Pageable.unpaged())).hasSize(2);
        assertThat(orders.countOverdueUnfinishedByFactory(factory.getId(), now)).isEqualTo(2);
    }

    @Test
    void dashboardTotalsDoNotWrapWhenTheSumExceedsAnInteger() {
        order.setPlannedQuantity(Integer.MAX_VALUE);
        orders.saveAndFlush(order);
        createOrder("EXECUTING", Integer.MAX_VALUE);
        assertThat(dashboard.getPerformance(factory.getId(), TimeRange.TODAY, LocalDateTime.now()).getPlannedQuantity())
                .isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void closingAnOrderStoresCompletionTimeAndReturnsTheFlushedVersion() {
        WorkOrderRequest request = new WorkOrderRequest();
        request.setProductId(product.getId());
        request.setPlannedQuantity(100);
        request.setStatus("CLOSED");
        request.setVersion(order.getVersion());
        Long responseVersion = orderService.update(factory.getId(), order.getId(), request).getVersion();
        WorkOrder saved = orders.findById(order.getId()).get();
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(responseVersion).isEqualTo(saved.getVersion()).isGreaterThan(order.getVersion());
    }

    private Factory createFactory() {
        Factory result = new Factory();
        result.setCode("AUDIT" + UUID.randomUUID().toString().substring(0, 8));
        result.setName(result.getCode());
        result.setEnabled(true);
        result.setCreatedAt(LocalDateTime.now());
        return factories.saveAndFlush(result);
    }

    private Product createProduct(Factory owner, int stock) {
        Unit unit = new Unit();
        unit.setFactory(owner);
        unit.setName("unit-" + UUID.randomUUID().toString().substring(0, 8));
        unit.setCreatedAt(LocalDateTime.now());
        units.saveAndFlush(unit);
        Product result = new Product();
        result.setFactory(owner);
        result.setUnit(unit);
        result.setCode("P" + UUID.randomUUID().toString().substring(0, 8));
        result.setName(result.getCode());
        result.setAttribute("自制");
        result.setInventoryQty(stock);
        result.setCreatedAt(LocalDateTime.now());
        return products.saveAndFlush(result);
    }

    private WorkOrder createOrder(String status, int planned) {
        WorkOrder result = new WorkOrder();
        result.setFactory(factory);
        result.setProduct(product);
        result.setCode("WO" + UUID.randomUUID().toString().substring(0, 8));
        result.setProductCode(product.getCode());
        result.setProductName(product.getName());
        result.setPlannedQuantity(planned);
        result.setStatus(status);
        result.setPriority("NORMAL");
        result.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"Cut\",\"plannedQuantity\":100}]");
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());
        return orders.saveAndFlush(result);
    }

    private WorkOrderReport createReport(LocalDateTime createdAt) {
        WorkOrderReport result = new WorkOrderReport();
        result.setFactory(factory);
        result.setWorkOrder(order);
        result.setWorkOrderCode(order.getCode());
        result.setReporter(actor);
        result.setReporterName(actor.getName());
        result.setProcessSequenceNo(1);
        result.setProcessName("Cut");
        result.setReportQuantity(1);
        result.setGoodQuantity(1);
        result.setCreatedAt(createdAt);
        result.setUpdatedAt(createdAt);
        return reports.saveAndFlush(result);
    }

    private WorkOrderReportRequest reportRequest() {
        WorkOrderReportRequest request = new WorkOrderReportRequest();
        request.setWorkOrderId(order.getId());
        request.setProcessSequenceNo(1);
        request.setReportQuantity(4);
        request.setGoodQuantity(4);
        return request;
    }

    private InboundReceiptRequest inbound(Long productId, int quantity, String code) {
        InboundReceiptRequest request = new InboundReceiptRequest();
        request.setCode(code);
        request.setInboundType("采购入库");
        request.setInboundTime("2026-09-11 12:00:00");
        InboundReceiptRequest.InboundReceiptItemRequest item = new InboundReceiptRequest.InboundReceiptItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(Collections.singletonList(item));
        return request;
    }

    private OutboundReceiptRequest outbound(Long productId, int quantity, String code) {
        OutboundReceiptRequest request = new OutboundReceiptRequest();
        request.setCode(code);
        request.setOutboundType("销售出库");
        request.setOutboundTime("2026-09-11 12:00:00");
        OutboundReceiptRequest.OutboundReceiptItemRequest item = new OutboundReceiptRequest.OutboundReceiptItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(Collections.singletonList(item));
        return request;
    }

    private int stock() { return products.findById(product.getId()).get().getInventoryQty(); }

    private List<Throwable> together(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> a = executor.submit(concurrentAction(first, ready, start));
            Future<Throwable> b = executor.submit(concurrentAction(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return Arrays.asList(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<Throwable> concurrentAction(Callable<?> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency gate timed out");
                action.call();
                return null;
            } catch (Exception ex) {
                return ex;
            }
        };
    }
}
