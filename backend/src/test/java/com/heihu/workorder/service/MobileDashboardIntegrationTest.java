package com.heihu.workorder.service;

import com.heihu.workorder.dto.MobileDashboardResponse;
import com.heihu.workorder.model.*;
import com.heihu.workorder.repository.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
@Import({MobileDashboardService.class, WorkOrderService.class, CustomFieldValueValidator.class,
        com.fasterxml.jackson.databind.ObjectMapper.class})
class MobileDashboardIntegrationTest {
    @Autowired private MobileDashboardService dashboard;
    @Autowired private WorkOrderService orderService;
    @Autowired private FactoryRepository factories;
    @Autowired private UserRepository users;
    @Autowired private WorkOrderRepository orders;
    @Autowired private WorkOrderReportRepository reports;
    @Autowired private javax.persistence.EntityManager entityManager;
    @Autowired private javax.persistence.EntityManagerFactory entityManagerFactory;
    private Factory factory;
    private FactoryUser actor;
    private FactoryUser colleague;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 24, 0, 0);

    @BeforeEach
    void setup() {
        factory = factory("mobile-dashboard");
        actor = user(factory, "worker");
        colleague = user(factory, "colleague");
    }

    @Test
    void aggregatesUseFullFactoryAndActualReporterScopeWithLongSumsAndExclusiveDayEnd() {
        List<WorkOrder> ownOrders = new ArrayList<>();
        List<String> states = Arrays.asList("NOT_STARTED", "EXECUTING", "IN_PROGRESS", "PAUSED",
                "COMPLETED", "FINISHED", "CLOSED", "CANCELED", "CANCELLED");
        for (int i = 0; i < states.size(); i++) {
            ownOrders.add(order(factory, "WO-" + i, states.get(i), i < 2 ? Integer.MAX_VALUE : 1, i < 2 ? Integer.MAX_VALUE : 0));
        }
        WorkOrder order = ownOrders.get(0);
        report(factory, actor, order, "Assembly", ReportStatus.APPROVED, START, 2, 3, actor.getId());
        report(factory, actor, order, "Assembly", ReportStatus.APPROVED, START.plusDays(1).minusNanos(1000), 4, 5, colleague.getId());
        report(factory, actor, order, "Older", ReportStatus.APPROVED, START.minusNanos(1000), 7, 11, actor.getId());
        report(factory, actor, order, "Next", ReportStatus.APPROVED, START.plusDays(1), 13, 17, actor.getId());
        report(factory, actor, order, "Pending", ReportStatus.PENDING, START.plusHours(1), 100, 100, actor.getId());
        report(factory, actor, order, "Rejected", ReportStatus.REJECTED, START.plusHours(2), 100, 100, actor.getId());
        report(factory, colleague, order, "Delegated", ReportStatus.APPROVED, START.plusHours(3), 999, 999, actor.getId());
        Factory other = factory("other-dashboard");
        WorkOrder foreignOrder = order(other, "FOREIGN", "EXECUTING", Integer.MAX_VALUE, Integer.MAX_VALUE);
        report(other, actor, foreignOrder, "Foreign", ReportStatus.APPROVED, START, 999, 999, actor.getId());

        MobileDashboardResponse result = dashboard.get(factory.getId(), actor.getId(), "2026-11-24");

        assertThat(result.getWorkOrderTotal()).isEqualTo(9);
        assertThat(result.getTotalPlanned()).isEqualTo(2L * Integer.MAX_VALUE + 7);
        assertThat(result.getTotalGood()).isEqualTo(2L * Integer.MAX_VALUE);
        assertThat(result.getActiveOrderCount()).isEqualTo(4);
        assertThat(result.getReportTotal()).isEqualTo(6);
        assertThat(result.getPendingReportCount()).isEqualTo(1);
        assertThat(result.getTodayReportCount()).isEqualTo(4);
        assertThat(result.getTodayGood()).isEqualTo(6);
        assertThat(result.getTodayDefect()).isEqualTo(8);
        assertThat(result.getTodayPending()).isEqualTo(1);
        assertThat(result.getTotalDefect()).isEqualTo(36);
        assertThat(result.getRecentOrders()).extracting("code").containsExactly("WO-8", "WO-7", "WO-6", "WO-5", "WO-4");
        assertThat(result.getRankings()).extracting("name").containsExactly("Next", "Older", "Assembly");
        assertThat(result.getRankings()).extracting("value").containsExactly(13L, 7L, 6L);
    }

    @Test
    void approvedRankingAggregatesBeforeTopFiveAndUsesStableTieOrderAndUnnamedGroup() {
        WorkOrder order = order(factory, "RANK", "NOT_STARTED", 1, 0);
        report(factory, actor, order, "Assembly", ReportStatus.APPROVED, START, Integer.MAX_VALUE, 0, actor.getId());
        report(factory, actor, order, "Assembly", ReportStatus.APPROVED, START, Integer.MAX_VALUE, 0, actor.getId());
        report(factory, actor, order, "Next", ReportStatus.APPROVED, START, 11, 0, actor.getId());
        report(factory, actor, order, "Beta", ReportStatus.APPROVED, START, 7, 0, actor.getId());
        report(factory, actor, order, "Alpha", ReportStatus.APPROVED, START, 7, 0, actor.getId());
        report(factory, actor, order, "", ReportStatus.APPROVED, START, 3, 0, actor.getId());
        report(factory, actor, order, " ", ReportStatus.APPROVED, START, 4, 0, actor.getId());
        report(factory, actor, order, "Gamma", ReportStatus.APPROVED, START, 6, 0, actor.getId());
        report(factory, actor, order, "Pending", ReportStatus.PENDING, START, Integer.MAX_VALUE, 0, actor.getId());
        MobileDashboardResponse result = dashboard.get(factory.getId(), actor.getId(), "2026-11-24");
        assertThat(result.getRankings()).extracting("name").containsExactly("Assembly", "Next", "Alpha", "Beta", "未命名工序");
        assertThat(result.getRankings()).extracting("value").containsExactly(2L * Integer.MAX_VALUE, 11L, 7L, 7L, 7L);
    }

    @Test
    void finalMysqlDayIncludesLastMicrosecondWithoutYear10000AndEmptyScopeReturnsZeros() {
        MobileDashboardResponse empty = dashboard.get(factory.getId(), actor.getId(), "9999-12-31");
        assertThat(empty.getReportTotal()).isZero();
        assertThat(empty.getWorkOrderTotal()).isZero();
        assertThat(empty.getTodayGood()).isZero();
        assertThat(empty.getRankings()).isEmpty();
        WorkOrder order = order(factory, "LAST-DAY", "NOT_STARTED", 1, 0);
        report(factory, actor, order, "Assembly", ReportStatus.APPROVED,
                LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000), 5, 1, actor.getId());
        assertThat(dashboard.get(factory.getId(), actor.getId(), "9999-12-31").getTodayGood()).isEqualTo(5);
    }

    @Test
    void reportableStatusGroupIsDatabaseFilteredAndCannotBeSavedAsAnArtificialStatus() {
        for (String status : new String[]{"NOT_STARTED", "EXECUTING", "IN_PROGRESS", "PAUSED", "FINISHED", "CANCELLED"}) {
            order(factory, "TASK-" + status, status, 1, 0);
        }
        Factory other = factory("other-tasks");
        order(other, "TASK-OTHER", "EXECUTING", 1, 0);
        assertThat(orderService.searchMobile(factory.getId(), null, null, "TASK", null, true,
                PageRequest.of(0, 20, Sort.by("id"))).getContent()).extracting("status")
                .containsExactly("NOT_STARTED", "EXECUTING", "IN_PROGRESS");
        assertThatThrownBy(() -> orderService.searchMobile(factory.getId(), null, null, null, "EXECUTING", true,
                PageRequest.of(0, 20))).isInstanceOf(IllegalArgumentException.class);
        assertThat(com.heihu.workorder.util.WorkOrderStates.isKnown("REPORTABLE")).isFalse();
    }

    @Test
    void fiveRecentOrdersWithDistinctRoutesAreHydratedInFourQueriesTotal() {
        Unit unit = new Unit(); unit.setFactory(factory); unit.setName("pcs"); unit.setCreatedAt(START);
        entityManager.persist(unit);
        for (int i = 0; i < 6; i++) {
            ProcessRoute route = new ProcessRoute(); route.setFactory(factory); route.setCode("ROUTE-" + i);
            route.setName("Route " + i); route.setCreatedAt(START); entityManager.persist(route);
            Product product = new Product(); product.setFactory(factory); product.setCode("PRODUCT-" + i);
            product.setName("Product " + i); product.setUnit(unit); product.setAttribute("自制");
            product.setProcessRoute(route); product.setCreatedAt(START); entityManager.persist(product);
            WorkOrder order = order(factory, "ORDER-" + i, "NOT_STARTED", 1, 0);
            order.setProduct(product); order.setCreator(actor); orders.saveAndFlush(order);
        }
        Long factoryId = factory.getId(); Long userId = actor.getId();
        entityManager.flush(); entityManager.clear();
        org.hibernate.stat.Statistics statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();

        MobileDashboardResponse result = dashboard.get(factoryId, userId, "2026-11-24");

        assertThat(result.getRecentOrders()).extracting("routeName")
                .containsExactly("Route 5", "Route 4", "Route 3", "Route 2", "Route 1");
        assertThat(result.getRecentOrders()).extracting("unitName").containsOnly("pcs");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    @Test
    void paginatedOrderUnitsWithDistinctProductsAndUnitsUseTwoQueriesIncludingCount() {
        for (int i = 0; i < 6; i++) {
            Unit unit = new Unit(); unit.setFactory(factory); unit.setName("unit-" + i); unit.setCreatedAt(START);
            entityManager.persist(unit);
            Product product = new Product(); product.setFactory(factory); product.setCode("UNIT-PRODUCT-" + i);
            product.setName("Product " + i); product.setUnit(unit); product.setAttribute("自制");
            product.setCreatedAt(START); entityManager.persist(product);
            WorkOrder order = order(factory, "UNIT-ORDER-" + i, "NOT_STARTED", 1, 0);
            order.setProduct(product); order.setCreator(actor); orders.saveAndFlush(order);
        }
        Long factoryId = factory.getId();
        entityManager.flush(); entityManager.clear();
        org.hibernate.stat.Statistics statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(orderService.list(factoryId, null, null, PageRequest.of(0, 5, Sort.by("id"))).getContent())
                .extracting("unitName").containsExactly("unit-0", "unit-1", "unit-2", "unit-3", "unit-4");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        entityManager.clear(); statistics.clear();
        assertThat(orderService.searchMobile(factoryId, null, null, "UNIT-ORDER", null,
                PageRequest.of(0, 5, Sort.by("id"))).getContent()).extracting("unitName")
                .containsExactly("unit-0", "unit-1", "unit-2", "unit-3", "unit-4");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private Factory factory(String code) {
        Factory factory = new Factory(); factory.setCode(code); factory.setName(code);
        factory.setCreatedAt(START); return factories.saveAndFlush(factory);
    }
    private FactoryUser user(Factory factory, String username) {
        FactoryUser user = new FactoryUser(); user.setFactory(factory); user.setUsername(username); user.setName(username);
        user.setPasswordHash("test-hash"); user.setRole(Role.PRODUCTION); user.setEnabled(true);
        user.setCreatedAt(START); user.setUpdatedAt(START); return users.saveAndFlush(user);
    }
    private WorkOrder order(Factory factory, String code, String status, int planned, int good) {
        WorkOrder order = new WorkOrder(); order.setFactory(factory); order.setCode(code); order.setStatus(status);
        order.setPriority("NORMAL"); order.setPlannedQuantity(planned); order.setGoodQuantity(good);
        order.setCreatedAt(START); order.setUpdatedAt(START); return orders.saveAndFlush(order);
    }
    private void report(Factory factory, FactoryUser reporter, WorkOrder order, String name, ReportStatus status,
                        LocalDateTime time, int good, int defect, Long submitterId) {
        WorkOrderReport report = new WorkOrderReport(); report.setFactory(factory); report.setReporter(reporter);
        report.setReporterName(reporter.getName()); report.setWorkOrder(order); report.setWorkOrderCode(order.getCode());
        report.setProcessSequenceNo(1); report.setProcessName(name); report.setStatus(status); report.setSubmitterId(submitterId);
        report.setGoodQuantity(good); report.setDefectQuantity(defect); report.setCreatedAt(time); report.setUpdatedAt(time);
        reports.saveAndFlush(report);
    }
}
