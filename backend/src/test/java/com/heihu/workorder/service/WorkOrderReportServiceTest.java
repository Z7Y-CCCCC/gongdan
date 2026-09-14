package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.dto.WorkOrderReportRequest;
import com.heihu.workorder.dto.WorkOrderReportResponse;
import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.Department;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.model.WorkOrderReport;
import com.heihu.workorder.model.WorkOrderReportImage;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderReportImageRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderReportServiceTest {

    @TempDir
    Path uploadDir;

    @Mock
    private WorkOrderReportRepository reportRepository;

    @Mock
    private WorkOrderReportImageRepository imageRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private WorkOrderReportService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderReportService(
                reportRepository,
                imageRepository,
                workOrderRepository,
                userRepository,
                new LocalReportImageStorage(uploadDir.toString()),
                notificationService
        );
    }

    @Test
    void uploadImageRejectsSpoofedImageContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evil.png",
                "image/png",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.uploadImage(1L, 2L, file))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userRepository, imageRepository);
    }

    @Test
    void uploadImageSanitizesOriginalNameAndStoresServerGeneratedFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\..\\report.png",
                "image/png;charset=utf-8",
                pngBytes()
        );
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("FAC");
        factory.setName("Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        FactoryUser user = new FactoryUser();
        user.setId(2L);
        user.setFactory(factory);
        user.setUsername("worker");
        user.setName("Worker");
        user.setPasswordHash("hash");
        user.setRole(Role.PRODUCTION);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user));
        when(imageRepository.save(any(WorkOrderReportImage.class))).thenAnswer(invocation -> {
            WorkOrderReportImage image = invocation.getArgument(0);
            image.setId(99L);
            return image;
        });

        service.uploadImage(1L, 2L, file);

        ArgumentCaptor<WorkOrderReportImage> captor = ArgumentCaptor.forClass(WorkOrderReportImage.class);
        verify(imageRepository, times(2)).save(captor.capture());
        WorkOrderReportImage saved = captor.getAllValues().get(1);
        assertThat(saved.getOriginalFileName()).isEqualTo("report.png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getStoredFileName()).endsWith(".png");
        assertThat(saved.getStoredFileName()).doesNotContain("..");
        assertThat(saved.getAccessPath()).isEqualTo("/api/mobile/reports/images/99/file");
        assertThat(Files.exists(uploadDir.resolve(saved.getStoredFileName()))).isTrue();
    }

    @Test
    void getImageDownloadRequiresImageInFactoryAndReadableFile() throws Exception {
        Files.write(uploadDir.resolve("stored.png"), pngBytes());
        WorkOrderReportImage image = new WorkOrderReportImage();
        image.setId(10L);
        image.setFactory(factory(1L));
        image.setUploader(user(2L, image.getFactory()));
        image.setOriginalFileName("report.png");
        image.setStoredFileName("stored.png");
        image.setAccessPath("/api/reports/images/10/file");
        image.setFileSize(12L);
        image.setContentType("image/png");
        image.setCreatedAt(LocalDateTime.now());

        when(imageRepository.findById(10L)).thenReturn(Optional.of(image));

        WorkOrderReportService.ReportImageDownload download = service.getImageDownload(1L, 10L);

        assertThat(download.getImage()).isSameAs(image);
        assertThat(download.getResource().exists()).isTrue();
    }

    @Test
    void uploadDoesNotLeaveAnOrphanFileWhenSavingMetadataFails() throws Exception {
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, factory(1L))));
        when(imageRepository.save(any())).thenThrow(new IllegalStateException("metadata unavailable"));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes());
        assertThatThrownBy(() -> service.uploadImage(1L, 2L, file)).isInstanceOf(IllegalStateException.class);
        try (java.util.stream.Stream<Path> files = Files.list(uploadDir)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void aLaterTransactionRollbackAlsoRemovesTheUploadedFile() throws Exception {
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, factory(1L))));
        when(imageRepository.save(any(WorkOrderReportImage.class))).thenAnswer(invocation -> {
            WorkOrderReportImage image = invocation.getArgument(0);
            image.setId(99L);
            return image;
        });
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.uploadImage(1L, 2L, new MockMultipartFile("file", "photo.png", "image/png", pngBytes()));
            try (java.util.stream.Stream<Path> files = Files.list(uploadDir)) {
                assertThat(files.count()).isEqualTo(1);
            }
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            try (java.util.stream.Stream<Path> files = Files.list(uploadDir)) {
                assertThat(files.count()).isZero();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getImageDownloadRejectsCrossFactoryImage() {
        WorkOrderReportImage image = new WorkOrderReportImage();
        image.setId(10L);
        image.setFactory(factory(9L));
        image.setStoredFileName("stored.png");

        when(imageRepository.findById(10L)).thenReturn(Optional.of(image));

        assertThatThrownBy(() -> service.getImageDownload(1L, 10L))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createReportUsesSelectedEnabledReporterInSameFactory() {
        Factory factory = factory(1L);
        FactoryUser currentUser = user(2L, factory);
        FactoryUser selectedReporter = user(3L, factory);
        selectedReporter.setName("Selected Worker");
        WorkOrder order = workOrder(5L, factory);

        WorkOrderReportRequest request = new WorkOrderReportRequest();
        request.setWorkOrderId(order.getId());
        request.setProcessSequenceNo(1);
        request.setProcessName("裁切");
        request.setReportQuantity(4);
        request.setGoodQuantity(4);
        request.setDefectQuantity(0);
        request.setReporterUserId(selectedReporter.getId());
        request.setImageIds(Collections.emptyList());

        when(userRepository.findByIdAndFactoryId(currentUser.getId(), factory.getId())).thenReturn(Optional.of(currentUser));
        when(userRepository.findByIdAndFactoryId(selectedReporter.getId(), factory.getId())).thenReturn(Optional.of(selectedReporter));
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(order.getId(), factory.getId())).thenReturn(Optional.of(order));
        when(reportRepository.save(any(WorkOrderReport.class))).thenAnswer(invocation -> {
            WorkOrderReport report = invocation.getArgument(0);
            report.setId(9L);
            return report;
        });
        when(imageRepository.findByReportIdOrderByCreatedAtAsc(9L)).thenReturn(Collections.emptyList());

        WorkOrderReportResponse response = service.createReport(factory.getId(), currentUser.getId(), request);

        assertThat(response.getReporterId()).isEqualTo(selectedReporter.getId());
        assertThat(response.getReporterName()).isEqualTo("Selected Worker");
    }

    @Test
    void createReportRejectsQuantityBreakdownMismatch() {
        Factory factory = factory(1L);
        FactoryUser currentUser = user(2L, factory);
        WorkOrderReportRequest request = new WorkOrderReportRequest();
        request.setWorkOrderId(5L);
        request.setProcessSequenceNo(1);
        request.setReportQuantity(5);
        request.setGoodQuantity(4);
        request.setDefectQuantity(0);

        when(userRepository.findByIdAndFactoryId(currentUser.getId(), factory.getId())).thenReturn(Optional.of(currentUser));

        assertThatThrownBy(() -> service.createReport(factory.getId(), currentUser.getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("良品数与不良数之和必须等于报工数")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(workOrderRepository, reportRepository);
    }

    @Test
    void cleanupExpiredPendingImagesDeletesFileAndDatabaseRecord() throws Exception {
        Files.write(uploadDir.resolve("pending.png"), pngBytes());
        WorkOrderReportImage image = new WorkOrderReportImage();
        image.setId(12L);
        image.setFactory(factory(1L));
        image.setUploader(user(2L, image.getFactory()));
        image.setOriginalFileName("pending.png");
        image.setStoredFileName("pending.png");
        image.setAccessPath("/api/reports/images/12/file");
        image.setFileSize(12L);
        image.setContentType("image/png");
        image.setCreatedAt(LocalDateTime.now().minusHours(25));

        when(imageRepository.findByReportIsNullAndCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(image));

        int removed = service.cleanupExpiredPendingImages();

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(uploadDir.resolve("pending.png"))).isFalse();
        verify(imageRepository).deleteAll(Collections.singletonList(image));
    }

    @Test
    void createReportRejectsUnknownOrDisabledProcessBeforeSaving() {
        WorkOrder order = stubOrderAndActor();
        WorkOrderReportRequest request = validReportRequest();
        request.setProcessSequenceNo(9);
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("工序");
        request.setProcessSequenceNo(1);
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"裁切\",\"enabled\":false}]");
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("工序");
        verifyNoInteractions(reportRepository);
    }

    @Test
    void createReportCannotBypassTaskPermissionsWithForgedReporterOrProcessName() {
        WorkOrder order = stubOrderAndActor();
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"裁切\",\"reportPermission\":\"worker3\"}]");
        FactoryUser selected = user(3L, order.getFactory());
        when(userRepository.findByIdAndFactoryId(3L, 1L)).thenReturn(Optional.of(selected));
        WorkOrderReportRequest request = validReportRequest();
        request.setReporterUserId(3L);
        request.setProcessName("随意伪造的工序");
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void createReportChecksSelectedReporterAsWellAsSubmitter() {
        WorkOrder order = stubOrderAndActor();
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"裁切\",\"reportPermission\":\"worker2\"}]");
        when(userRepository.findByIdAndFactoryId(3L, 1L)).thenReturn(Optional.of(user(3L, order.getFactory())));
        WorkOrderReportRequest request = validReportRequest();
        request.setReporterUserId(3L);
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void createReportRejectsNegativeQuantitiesRatherThanClampingThemToZero() {
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, factory(1L))));
        WorkOrderReportRequest request = validReportRequest();
        request.setDefectQuantity(-1);
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("负数");
        verifyNoInteractions(workOrderRepository, reportRepository);
    }

    @Test
    void createReportRejectsClosedOrderAndReversedTimes() {
        WorkOrder order = stubOrderAndActor();
        WorkOrderReportRequest request = validReportRequest();
        order.setStatus("CLOSED");
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("不能报工");
        order.setStatus("EXECUTING");
        request.setStartTime("2026-09-11 12:00:00");
        request.setEndTime("2026-09-11 11:00:00");
        assertThatThrownBy(() -> service.createReport(1L, 2L, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("结束时间");
        verifyNoInteractions(reportRepository);
    }

    @Test
    void createReportAlwaysUsesTheServerSideProcessName() {
        stubOrderAndActor();
        WorkOrderReportRequest request = validReportRequest();
        request.setProcessName("伪造工序");
        when(reportRepository.save(any(WorkOrderReport.class))).thenAnswer(invocation -> {
            WorkOrderReport report = invocation.getArgument(0);
            report.setId(9L);
            return report;
        });
        assertThat(service.createReport(1L, 2L, request).getProcessName()).isEqualTo("裁切");
    }

    @Test
    void reporterListIsRestrictedToTheActualTaskAndDepartment() {
        WorkOrder order = workOrder(5L, factory(1L));
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"裁切\",\"reportPermission\":\"车间A\"}]");
        Department department = new Department();
        department.setName("车间A");
        FactoryUser allowed = user(2L, order.getFactory());
        allowed.setDepartment(department);
        FactoryUser other = user(3L, order.getFactory());
        when(workOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findByFactoryIdAndEnabledTrue(1L)).thenReturn(java.util.Arrays.asList(allowed, other));

        assertThat(service.listReporters(1L, 5L, 1)).extracting("id").containsExactly(2L);
        assertThatThrownBy(() -> service.listReporters(9L, 5L, 1))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void approvingTheSameReportTwiceCannotApplyQuantitiesTwice() {
        WorkOrder order = workOrder(5L, factory(1L));
        WorkOrderReport report = pendingReport(order);
        when(reportRepository.findByIdAndFactoryIdForUpdate(9L, 1L)).thenReturn(Optional.of(report));
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(5L, 1L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, order.getFactory())));
        when(reportRepository.save(report)).thenReturn(report);

        service.approve(1L, 2L, 9L, null);
        assertThat(order.getGoodQuantity()).isEqualTo(4);
        assertThatThrownBy(() -> service.approve(1L, 2L, 9L, null)).isInstanceOf(ApiException.class);
        verify(workOrderRepository, times(1)).save(order);
        verify(reportRepository, times(1)).save(report);
    }

    @Test
    void approvalRejectsOverflowAndCannotInventARemovedTask() {
        WorkOrder order = workOrder(5L, factory(1L));
        order.setGoodQuantity(Integer.MAX_VALUE);
        WorkOrderReport report = pendingReport(order);
        when(reportRepository.findByIdAndFactoryIdForUpdate(9L, 1L)).thenReturn(Optional.of(report));
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(5L, 1L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, order.getFactory())));
        assertThatThrownBy(() -> service.approve(1L, 2L, 9L, null)).isInstanceOf(ApiException.class)
                .hasMessageContaining("数量");
        report.setStatus(ReportStatus.PENDING);
        order.setTaskDataJson("[]");
        assertThatThrownBy(() -> service.approve(1L, 2L, 9L, null)).isInstanceOf(ApiException.class)
                .hasMessageContaining("工序");
        verify(reportRepository, never()).save(any());
    }

    private WorkOrder stubOrderAndActor() {
        WorkOrder order = workOrder(5L, factory(1L));
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, order.getFactory())));
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(5L, 1L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void approvalCannotCreditADifferentProcessThatReusesTheOldSequenceNumber() {
        WorkOrder order = workOrder(5L, factory(1L));
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"焊接\",\"plannedQuantity\":100}]");
        WorkOrderReport report = pendingReport(order);
        when(reportRepository.findByIdAndFactoryIdForUpdate(9L, 1L)).thenReturn(Optional.of(report));
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(5L, 1L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user(2L, order.getFactory())));
        assertThatThrownBy(() -> service.approve(1L, 2L, 9L, null)).isInstanceOf(ApiException.class)
                .hasMessageContaining("工序已移除或变更");
        assertThat(order.getGoodQuantity()).isZero();
        verify(workOrderRepository, never()).save(any());
    }

    private WorkOrderReportRequest validReportRequest() {
        WorkOrderReportRequest request = new WorkOrderReportRequest();
        request.setWorkOrderId(5L);
        request.setProcessSequenceNo(1);
        request.setReportQuantity(4);
        request.setGoodQuantity(4);
        request.setDefectQuantity(0);
        return request;
    }

    private WorkOrderReport pendingReport(WorkOrder order) {
        WorkOrderReport report = new WorkOrderReport();
        report.setId(9L);
        report.setFactory(order.getFactory());
        report.setWorkOrder(order);
        report.setProcessSequenceNo(1);
        report.setProcessName("裁切");
        report.setReporter(user(2L, order.getFactory()));
        report.setStatus(ReportStatus.PENDING);
        report.setReportQuantity(4);
        report.setGoodQuantity(4);
        return report;
    }

    private Factory factory(Long id) {
        Factory factory = new Factory();
        factory.setId(id);
        factory.setCode("FAC" + id);
        factory.setName("Factory " + id);
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        return factory;
    }

    private FactoryUser user(Long id, Factory factory) {
        FactoryUser user = new FactoryUser();
        user.setId(id);
        user.setFactory(factory);
        user.setUsername("worker" + id);
        user.setName("Worker " + id);
        user.setPasswordHash("hash");
        user.setRole(Role.PRODUCTION);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private WorkOrder workOrder(Long id, Factory factory) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setFactory(factory);
        order.setCode("WO-" + id);
        order.setProductCode("P-" + id);
        order.setProductName("Product " + id);
        order.setProductSpec("Spec");
        order.setPlannedQuantity(100);
        order.setGoodQuantity(0);
        order.setDefectQuantity(0);
        order.setStatus("IN_PROGRESS");
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"裁切\",\"plannedQuantity\":100}]");
        order.setPriority("中");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
