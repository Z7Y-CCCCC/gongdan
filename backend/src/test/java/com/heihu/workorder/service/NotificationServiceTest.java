package com.heihu.workorder.service;

import com.heihu.workorder.dto.NotificationListResponse;
import com.heihu.workorder.dto.NotificationResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.NotificationEvent;
import com.heihu.workorder.model.NotificationRecipient;
import com.heihu.workorder.model.NotificationRecipientState;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.NotificationEventRepository;
import com.heihu.workorder.repository.NotificationRecipientRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationEventRepository eventRepository;

    @Mock
    private NotificationRecipientRepository recipientRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private WorkOrderReportRepository reportRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                eventRepository,
                recipientRepository,
                factoryRepository,
                userRepository,
                workOrderRepository,
                reportRepository
        );
    }

    @Test
    void archivedScopePassesArchivedOnlyFilter() {
        AuthenticatedUser currentUser = currentUser();
        when(userRepository.findByFactoryIdAndEnabledTrue(1L)).thenReturn(Collections.emptyList());
        when(recipientRepository.search(
                eq(7L), eq(1L), eq(true), eq(true), eq(true), eq(false), eq(""), eq(""), eq(""),
                any(LocalDateTime.class), any(Pageable.class)
        )).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(recipientRepository.countUnread(eq(7L), eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        NotificationListResponse response = service.list(currentUser, "history", false, "", "", "", 1, 20);

        assertThat(response.getItems()).isEmpty();
        verify(recipientRepository).search(
                eq(7L), eq(1L), eq(true), eq(true), eq(true), eq(false), eq(""), eq(""), eq(""),
                any(LocalDateTime.class), any(Pageable.class)
        );
    }

    @Test
    void markReadChangesOnlyTheCurrentUsersRecipient() {
        NotificationRecipient recipient = recipient();
        when(recipientRepository.findByIdAndUserIdAndEventFactoryId(12L, 7L, 1L))
                .thenReturn(Optional.of(recipient));
        when(recipientRepository.save(recipient)).thenReturn(recipient);

        NotificationResponse response = service.markRead(currentUser(), 12L);

        assertThat(response.isUnread()).isFalse();
        assertThat(recipient.getState()).isEqualTo(NotificationRecipientState.READ);
        assertThat(recipient.getReadAt()).isNotNull();
        verify(recipientRepository).findByIdAndUserIdAndEventFactoryId(12L, 7L, 1L);
    }

    @Test
    void markAllReadUsesOnlyActiveUnreadRows() {
        when(recipientRepository.markAllRead(
                eq(7L), eq(1L), any(LocalDateTime.class), eq(NotificationRecipientState.READ)
        )).thenReturn(3);

        assertThat(service.markAllRead(currentUser())).isEqualTo(3);
        verify(recipientRepository).markAllRead(
                eq(7L), eq(1L), any(LocalDateTime.class), eq(NotificationRecipientState.READ)
        );
    }

    @Test
    void repeatedReportEventReusesExistingEventAndRecipient() {
        Factory factory = new Factory();
        factory.setId(1L);
        FactoryUser admin = new FactoryUser();
        admin.setId(7L);
        admin.setFactory(factory);
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        when(userRepository.findByFactoryIdAndEnabledTrue(1L)).thenReturn(Collections.singletonList(admin));

        NotificationEvent event = new NotificationEvent();
        event.setId(22L);
        event.setFactory(factory);
        when(eventRepository.findByFactoryIdAndDedupeKey(1L, "report-submitted:9"))
                .thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(recipientRepository.existsByEventIdAndUserId(22L, 7L)).thenReturn(true);

        com.heihu.workorder.model.WorkOrderReport report = new com.heihu.workorder.model.WorkOrderReport();
        report.setId(9L);
        report.setFactory(factory);
        report.setReporterName("张三");
        report.setGoodQuantity(10);
        report.setDefectQuantity(1);
        report.setWorkOrderCode("WO-9");
        report.setProcessName("装配");
        report.setCreatedAt(LocalDateTime.now());
        report.setStatus(com.heihu.workorder.model.ReportStatus.APPROVED);

        service.publishReportSubmitted(report);

        verify(eventRepository).findByFactoryIdAndDedupeKey(1L, "report-submitted:9");
        verify(recipientRepository).existsByEventIdAndUserId(22L, 7L);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(7L, 1L, "HHGD", Role.ADMIN, "管理员");
    }

    private NotificationRecipient recipient() {
        NotificationEvent event = new NotificationEvent();
        event.setId(21L);
        event.setFactory(factory());
        event.setEventType("REPORT_PENDING_APPROVAL");
        event.setTitle("待审批报工");
        event.setContent("WO-1 / 装配");
        event.setPriority("HIGH");
        event.setCreatedAt(LocalDateTime.now());

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(12L);
        recipient.setEvent(event);
        recipient.setState(NotificationRecipientState.UNREAD);
        recipient.setCreatedAt(LocalDateTime.now());
        recipient.setUpdatedAt(LocalDateTime.now());
        return recipient;
    }

    private Factory factory() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("测试工厂");
        return factory;
    }
}
