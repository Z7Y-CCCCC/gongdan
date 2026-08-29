package com.heihu.workorder.service;

import com.heihu.workorder.dto.NotificationListResponse;
import com.heihu.workorder.dto.NotificationResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.NotificationEvent;
import com.heihu.workorder.model.NotificationRecipient;
import com.heihu.workorder.model.NotificationRecipientState;
import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.model.WorkOrderReport;
import com.heihu.workorder.repository.NotificationEventRepository;
import com.heihu.workorder.repository.NotificationRecipientRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotificationService {

    private static final String TYPE_APPROVAL = "REPORT_PENDING_APPROVAL";
    private static final String TYPE_OVERDUE = "WORK_ORDER_OVERDUE";
    private static final String TYPE_REPORT = "REPORT_SUBMITTED";
    private static final List<String> SNAPSHOT_TYPES = Arrays.asList(TYPE_APPROVAL, TYPE_OVERDUE);

    private final NotificationEventRepository eventRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderReportRepository reportRepository;

    public NotificationService(
            NotificationEventRepository eventRepository,
            NotificationRecipientRepository recipientRepository,
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            WorkOrderRepository workOrderRepository,
            WorkOrderReportRepository reportRepository
    ) {
        this.eventRepository = eventRepository;
        this.recipientRepository = recipientRepository;
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional
    public NotificationListResponse list(
            AuthenticatedUser currentUser,
            String scope,
            boolean unreadOnly,
            String eventType,
            String priority,
            String keyword,
            int page,
            int size
    ) {
        synchronizeLegacySnapshot(currentUser.getFactoryId());
        LocalDateTime now = LocalDateTime.now();
        int safePage = Math.max(0, page - 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        boolean includeHistory = "all".equalsIgnoreCase(scope) || "history".equalsIgnoreCase(scope);
        boolean includeArchived = "history".equalsIgnoreCase(scope);
        boolean archivedOnly = "history".equalsIgnoreCase(scope);
        Page<NotificationRecipient> result = recipientRepository.search(
                currentUser.getUserId(),
                currentUser.getFactoryId(),
                includeHistory,
                includeArchived,
                archivedOnly,
                unreadOnly,
                normalize(eventType),
                normalize(priority),
                normalize(keyword),
                now,
                PageRequest.of(safePage, safeSize, Sort.unsorted())
        );
        long unreadCount = recipientRepository.countUnread(currentUser.getUserId(), currentUser.getFactoryId(), now);
        return new NotificationListResponse(
                result.getContent().stream().map(NotificationResponse::from).collect(Collectors.toList()),
                result.getTotalElements(),
                unreadCount
        );
    }

    @Transactional
    public long unreadCount(AuthenticatedUser currentUser) {
        synchronizeLegacySnapshot(currentUser.getFactoryId());
        return recipientRepository.countUnread(
                currentUser.getUserId(),
                currentUser.getFactoryId(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public NotificationResponse markRead(AuthenticatedUser currentUser, Long recipientId) {
        NotificationRecipient recipient = getRecipient(currentUser, recipientId);
        if (recipient.getReadAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            recipient.setReadAt(now);
            if (recipient.getState() == NotificationRecipientState.UNREAD) {
                recipient.setState(NotificationRecipientState.READ);
            }
            recipient.setUpdatedAt(now);
            recipientRepository.save(recipient);
        }
        return NotificationResponse.from(recipient);
    }

    @Transactional
    public NotificationResponse markUnread(AuthenticatedUser currentUser, Long recipientId) {
        NotificationRecipient recipient = getRecipient(currentUser, recipientId);
        recipient.setReadAt(null);
        recipient.setState(NotificationRecipientState.UNREAD);
        recipient.setUpdatedAt(LocalDateTime.now());
        return NotificationResponse.from(recipientRepository.save(recipient));
    }

    @Transactional
    public NotificationResponse archive(AuthenticatedUser currentUser, Long recipientId) {
        NotificationRecipient recipient = getRecipient(currentUser, recipientId);
        LocalDateTime now = LocalDateTime.now();
        recipient.setArchivedAt(now);
        recipient.setReadAt(recipient.getReadAt() == null ? now : recipient.getReadAt());
        recipient.setState(NotificationRecipientState.ARCHIVED);
        recipient.setUpdatedAt(now);
        return NotificationResponse.from(recipientRepository.save(recipient));
    }

    @Transactional
    public int markAllRead(AuthenticatedUser currentUser) {
        return recipientRepository.markAllRead(
                currentUser.getUserId(),
                currentUser.getFactoryId(),
                LocalDateTime.now(),
                NotificationRecipientState.READ
        );
    }

    /**
     * Publishes a report event in the same transaction as the report action.
     * The dedupe key makes retries safe and keeps the bell count stable.
     */
    public void publishReportSubmitted(WorkOrderReport report) {
        if (report == null || report.getFactory() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<FactoryUser> admins = enabledAdmins(report.getFactory().getId());
        if (report.getStatus() == ReportStatus.PENDING) {
            NotificationEvent pendingEvent = upsertEvent(
                    report.getFactory(),
                    TYPE_APPROVAL,
                    "待审批报工",
                    safe(report.getWorkOrderCode()) + " / " + safe(report.getProcessName()) + " / " + safe(report.getReporterName()),
                    "HIGH",
                    "WORK_ORDER_REPORT",
                    report.getId(),
                    "production",
                    "workbench",
                    "{\"id\":" + report.getId() + "}",
                    "report-approval:" + report.getId(),
                    report.getCreatedAt() == null ? now : report.getCreatedAt(),
                    null
            );
            ensureRecipients(pendingEvent, admins, now);
        }
        NotificationEvent event = upsertEvent(
                report.getFactory(),
                TYPE_REPORT,
                "今日报工",
                safe(report.getReporterName()) + " 提交 " + number(report.getGoodQuantity()) + " 良品 / " + number(report.getDefectQuantity()) + " 不良",
                "NORMAL",
                "WORK_ORDER_REPORT",
                report.getId(),
                "production",
                "reporting",
                "{\"id\":" + report.getId() + "}",
                "report-submitted:" + report.getId(),
                report.getCreatedAt() == null ? now : report.getCreatedAt(),
                now.plusDays(3)
        );
        ensureRecipients(event, admins, now);
    }

    public void publishReportDecision(WorkOrderReport report, boolean approved) {
        if (report == null || report.getFactory() == null || report.getReporter() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String title = approved ? "报工已通过" : "报工被驳回";
        String remark = StringUtils.hasText(report.getApprovalRemark()) ? " / " + report.getApprovalRemark() : "";
        NotificationEvent event = upsertEvent(
                report.getFactory(),
                approved ? "REPORT_APPROVED" : "REPORT_REJECTED",
                title,
                safe(report.getWorkOrderCode()) + " / " + safe(report.getProcessName()) + " / 审批人 " + safe(report.getApproverName()) + remark,
                approved ? "NORMAL" : "HIGH",
                "WORK_ORDER_REPORT",
                report.getId(),
                "production",
                "reporting",
                "{\"id\":" + report.getId() + "}",
                (approved ? "report-approved:" : "report-rejected:") + report.getId(),
                now,
                now.plusDays(14)
        );
        resolveEvent(report.getFactory().getId(), "report-approval:" + report.getId(), now);
        List<FactoryUser> recipients = report.getReporter().isEnabled()
                ? Collections.singletonList(report.getReporter())
                : Collections.emptyList();
        ensureRecipients(event, recipients, now);
    }

    @Scheduled(
            fixedDelayString = "${app.notifications.snapshot-delay-ms:300000}",
            initialDelayString = "${app.notifications.snapshot-initial-delay-ms:60000}"
    )
    public void synchronizeSnapshotsOnSchedule() {
        for (Factory factory : factoryRepository.findAll()) {
            if (factory.isEnabled()) {
                synchronizeLegacySnapshot(factory.getId());
            }
        }
    }

    private NotificationRecipient getRecipient(AuthenticatedUser currentUser, Long recipientId) {
        return recipientRepository.findByIdAndUserIdAndEventFactoryId(
                        recipientId,
                        currentUser.getUserId(),
                        currentUser.getFactoryId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "通知不存在或无权访问"));
    }

    /**
     * Materializes the three notifications that previously existed only as frontend-derived data.
     * This keeps the first migration backward compatible while new domain events are introduced.
     */
    private void synchronizeLegacySnapshot(Long factoryId) {
        List<FactoryUser> recipients = enabledAdmins(factoryId);
        if (recipients.isEmpty()) {
            return;
        }
        Factory factory = recipients.get(0).getFactory();
        LocalDateTime now = LocalDateTime.now();
        Set<String> activeSnapshotKeys = new HashSet<>();

        Page<WorkOrderReport> pendingReports = reportRepository.search(
                factoryId,
                ReportStatus.PENDING,
                "",
                "",
                null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        for (WorkOrderReport report : pendingReports.getContent()) {
            String dedupeKey = "report-approval:" + report.getId();
            activeSnapshotKeys.add(dedupeKey);
            NotificationEvent event = upsertEvent(
                    factory,
                    TYPE_APPROVAL,
                    "待审批报工",
                    safe(report.getWorkOrderCode()) + " / " + safe(report.getProcessName()) + " / " + safe(report.getReporterName()),
                    "HIGH",
                    "WORK_ORDER_REPORT",
                    report.getId(),
                    "production",
                    "workbench",
                    "{\"id\":" + report.getId() + "}",
                    dedupeKey,
                    now,
                    null
            );
            ensureRecipients(event, recipients, now);
        }

        List<WorkOrder> overdueOrders = workOrderRepository.findOverdueUnfinished(
                factoryId,
                now,
                PageRequest.of(0, 50)
        );
        for (WorkOrder order : overdueOrders) {
            String dedupeKey = "workorder-overdue:" + order.getId();
            activeSnapshotKeys.add(dedupeKey);
            NotificationEvent event = upsertEvent(
                    factory,
                    TYPE_OVERDUE,
                    "逾期工单",
                    safe(order.getCode()) + " / " + safe(order.getProductName()) + " / 计划完工 " + format(order.getPlanEnd()),
                    "HIGH",
                    "WORK_ORDER",
                    order.getId(),
                    "production",
                    "workorder",
                    "{\"id\":" + order.getId() + "}",
                    dedupeKey,
                    order.getPlanEnd() == null ? now : order.getPlanEnd(),
                    null
            );
            ensureRecipients(event, recipients, now);
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<WorkOrderReport> todayReports = reportRepository.findRecentSince(
                factoryId,
                todayStart.minusSeconds(1),
                PageRequest.of(0, 50)
        );
        for (WorkOrderReport report : todayReports) {
            String dedupeKey = "report-submitted:" + report.getId();
            NotificationEvent event = upsertEvent(
                    factory,
                    TYPE_REPORT,
                    "今日报工",
                    safe(report.getReporterName()) + " 提交 " + number(report.getGoodQuantity()) + " 良品 / " + number(report.getDefectQuantity()) + " 不良",
                    "NORMAL",
                    "WORK_ORDER_REPORT",
                    report.getId(),
                    "production",
                    "reporting",
                    "{\"id\":" + report.getId() + "}",
                    dedupeKey,
                    report.getCreatedAt() == null ? now : report.getCreatedAt(),
                    now.plusDays(3)
            );
            ensureRecipients(event, recipients, now);
        }

        resolveMissingSnapshotEvents(factoryId, activeSnapshotKeys, now);
    }

    private NotificationEvent upsertEvent(
            Factory factory,
            String eventType,
            String title,
            String content,
            String priority,
            String sourceType,
            Long sourceId,
            String targetMain,
            String targetSub,
            String targetParamsJson,
            String dedupeKey,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        NotificationEvent event = eventRepository.findByFactoryIdAndDedupeKey(factory.getId(), dedupeKey)
                .orElseGet(NotificationEvent::new);
        event.setFactory(factory);
        event.setEventType(eventType);
        event.setTitle(title);
        event.setContent(content);
        event.setPriority(priority);
        event.setSourceType(sourceType);
        event.setSourceId(sourceId);
        event.setTargetMain(targetMain);
        event.setTargetSub(targetSub);
        event.setTargetParamsJson(targetParamsJson);
        event.setDedupeKey(dedupeKey);
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(createdAt);
        }
        event.setExpiresAt(expiresAt);
        event.setResolvedAt(null);
        return eventRepository.save(event);
    }

    private void ensureRecipients(NotificationEvent event, List<FactoryUser> users, LocalDateTime now) {
        for (FactoryUser user : users) {
            if (event.getId() == null || !recipientRepository.existsByEventIdAndUserId(event.getId(), user.getId())) {
                NotificationRecipient recipient = new NotificationRecipient();
                recipient.setEvent(event);
                recipient.setUser(user);
                recipient.setState(NotificationRecipientState.UNREAD);
                recipient.setCreatedAt(now);
                recipient.setUpdatedAt(now);
                recipientRepository.save(recipient);
            }
        }
    }

    private List<FactoryUser> enabledAdmins(Long factoryId) {
        return userRepository.findByFactoryIdAndEnabledTrue(factoryId).stream()
                .filter(user -> user.getRole() == com.heihu.workorder.model.Role.ADMIN)
                .collect(Collectors.toList());
    }

    private void resolveMissingSnapshotEvents(Long factoryId, Set<String> activeKeys, LocalDateTime now) {
        for (NotificationEvent event : eventRepository.findOpenByFactoryIdAndEventTypeIn(factoryId, SNAPSHOT_TYPES)) {
            if (!activeKeys.contains(event.getDedupeKey())) {
                event.setResolvedAt(now);
                eventRepository.save(event);
            }
        }
    }

    private void resolveEvent(Long factoryId, String dedupeKey, LocalDateTime now) {
        eventRepository.findByFactoryIdAndDedupeKey(factoryId, dedupeKey).ifPresent(event -> {
            event.setResolvedAt(now);
            eventRepository.save(event);
        });
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String number(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String format(LocalDateTime value) {
        return value == null ? "-" : value.toString().replace('T', ' ').substring(0, Math.min(16, value.toString().length()));
    }
}
