package com.heihu.workorder.service;

import com.heihu.workorder.model.*;
import com.heihu.workorder.repository.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false", "spring.jpa.show-sql=false"})
@ActiveProfiles("test")
@Import(NotificationService.class)
class NotificationSnapshotIntegrationTest {
    @Autowired private NotificationService service;
    @Autowired private FactoryRepository factories;
    @Autowired private UserRepository users;
    @Autowired private WorkOrderRepository orders;
    @Autowired private WorkOrderReportRepository reports;
    @Autowired private NotificationEventRepository events;
    @Autowired private NotificationRecipientRepository recipients;

    @Test
    void sourceStillPendingBeyondSnapshotLimitIsNotResolvedAndOnlyRealCompletionClosesIt() {
        Factory factory = factory("snapshot");
        FactoryUser admin = admin(factory);
        LocalDateTime now = LocalDateTime.now();
        WorkOrder lastOrder = null;
        WorkOrderReport lastReport = null;
        for (int i = 0; i < 51; i++) {
            lastOrder = order(factory, "WO-" + i, now.minusDays(2).plusMinutes(i));
            lastReport = report(factory, admin, lastOrder, now.minusMinutes(i));
        }
        NotificationEvent approval = event(factory, "REPORT_PENDING_APPROVAL", lastReport.getId(),
                "report-approval:" + lastReport.getId());
        NotificationEvent overdue = event(factory, "WORK_ORDER_OVERDUE", lastOrder.getId(),
                "workorder-overdue:" + lastOrder.getId());
        recipient(admin, approval);
        recipient(admin, overdue);

        service.unreadCount(currentUser(admin));

        assertThat(events.findById(approval.getId()).get().getResolvedAt()).isNull();
        assertThat(events.findById(overdue.getId()).get().getResolvedAt()).isNull();

        lastReport.setStatus(ReportStatus.APPROVED);
        reports.saveAndFlush(lastReport);
        lastOrder.setStatus("COMPLETED");
        orders.saveAndFlush(lastOrder);
        service.unreadCount(currentUser(admin));

        assertThat(events.findById(approval.getId()).get().getResolvedAt()).isNotNull();
        assertThat(events.findById(overdue.getId()).get().getResolvedAt()).isNotNull();
    }

    @Test
    void snapshotResolutionDoesNotTouchOtherFactoryAndCannotBorrowItsSource() {
        Factory factory = factory("scope-one");
        Factory other = factory("scope-two");
        FactoryUser admin = admin(factory);
        FactoryUser otherAdmin = admin(other);
        WorkOrder otherOrder = order(other, "OTHER", LocalDateTime.now().minusDays(1));
        NotificationEvent otherEvent = event(other, "WORK_ORDER_OVERDUE", otherOrder.getId(), "other-overdue");
        NotificationEvent invalidLocalEvent = event(factory, "WORK_ORDER_OVERDUE", otherOrder.getId(), "wrong-source");
        recipient(admin, invalidLocalEvent);
        recipient(otherAdmin, otherEvent);

        service.unreadCount(currentUser(admin));

        assertThat(events.findById(otherEvent.getId()).get().getResolvedAt()).isNull();
        assertThat(events.findById(invalidLocalEvent.getId()).get().getResolvedAt()).isNotNull();
    }

    private Factory factory(String code) {
        Factory factory = new Factory();
        factory.setCode(code);
        factory.setName(code);
        factory.setCreatedAt(LocalDateTime.now());
        return factories.saveAndFlush(factory);
    }

    private FactoryUser admin(Factory factory) {
        FactoryUser user = new FactoryUser();
        user.setFactory(factory);
        user.setUsername("admin");
        user.setName("Admin");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setPasswordHash("test-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return users.saveAndFlush(user);
    }

    private WorkOrder order(Factory factory, String code, LocalDateTime planEnd) {
        WorkOrder order = new WorkOrder();
        order.setFactory(factory);
        order.setCode(code);
        order.setStatus("EXECUTING");
        order.setPriority("NORMAL");
        order.setPlanEnd(planEnd);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return orders.saveAndFlush(order);
    }

    private WorkOrderReport report(Factory factory, FactoryUser reporter, WorkOrder order, LocalDateTime createdAt) {
        WorkOrderReport report = new WorkOrderReport();
        report.setFactory(factory);
        report.setReporter(reporter);
        report.setReporterName(reporter.getName());
        report.setWorkOrder(order);
        report.setWorkOrderCode(order.getCode());
        report.setProcessSequenceNo(1);
        report.setProcessName("Assembly");
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        return reports.saveAndFlush(report);
    }

    private NotificationEvent event(Factory factory, String type, Long sourceId, String key) {
        NotificationEvent event = new NotificationEvent();
        event.setFactory(factory);
        event.setEventType(type);
        event.setSourceId(sourceId);
        event.setDedupeKey(key);
        event.setTitle("Pending work");
        event.setContent("Pending work");
        event.setPriority("HIGH");
        event.setCreatedAt(LocalDateTime.now());
        return events.saveAndFlush(event);
    }

    private void recipient(FactoryUser user, NotificationEvent event) {
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setUser(user);
        recipient.setEvent(event);
        recipient.setState(NotificationRecipientState.UNREAD);
        recipient.setCreatedAt(LocalDateTime.now());
        recipient.setUpdatedAt(LocalDateTime.now());
        recipients.saveAndFlush(recipient);
    }

    private AuthenticatedUser currentUser(FactoryUser user) {
        return new AuthenticatedUser(user.getId(), user.getFactory().getId(), user.getFactory().getCode(),
                user.getRole(), user.getName());
    }
}
