package com.heihu.workorder.service;

import com.heihu.workorder.controller.MobileReportController;
import com.heihu.workorder.dto.WorkOrderReportRequest;
import com.heihu.workorder.exception.ApiException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Normal image resources and two private factory fixtures; no crafted executable content. */
@ExtendWith(MockitoExtension.class)
class ReportImageAuthorizationTest {
    @TempDir Path uploads;
    @Mock WorkOrderReportRepository reports;
    @Mock WorkOrderReportImageRepository images;
    @Mock WorkOrderRepository orders;
    @Mock UserRepository users;
    @Mock NotificationService notifications;
    @Mock TokenService tokens;
    WorkOrderReportService service;
    MobileReportController controller;
    Factory first, second;
    WorkOrderReportImage image;
    static final byte[] CONTENT = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @BeforeEach void fixture() throws Exception {
        first = factory(11L); second = factory(12L);
        image = new WorkOrderReportImage();
        image.setId(101L); image.setFactory(first); image.setUploader(viewer(1L, first, Role.PRODUCTION));
        image.setOriginalFileName("normal-photo.png"); image.setStoredFileName("normal-photo.png");
        image.setContentType("image/png"); image.setFileSize((long) CONTENT.length);
        image.setCreatedAt(LocalDateTime.now());
        Files.write(uploads.resolve("normal-photo.png"), CONTENT);
        service = new WorkOrderReportService(reports, images, orders, users,
                new LocalReportImageStorage(uploads.toString()), notifications);
        controller = new MobileReportController(service, tokens);
    }

    @Test void unrelatedSameFactoryWorkerCannotReadAnotherPendingImage() {
        actor(viewer(4L, first, Role.PRODUCTION));
        assertNotFound();
    }

    @ParameterizedTest @ValueSource(strings = {"UPLOADER", "REPORTER", "SUBMITTER", "ADMIN"})
    void ownedOrRelatedUsersAndSameFactoryAdminCanReadAttachedImages(String kind) throws Exception {
        attachNormalReport();
        FactoryUser viewer = "UPLOADER".equals(kind) ? viewer(1L, first, Role.PRODUCTION)
                : "REPORTER".equals(kind) ? viewer(2L, first, Role.PRODUCTION)
                : "SUBMITTER".equals(kind) ? viewer(3L, first, Role.PRODUCTION)
                : viewer(5L, first, Role.ADMIN);
        actor(viewer);
        try (java.io.InputStream input = controller.getImage("Bearer FIXTURE_TOKEN", 101L).getBody().getInputStream()) {
            assertThat(StreamUtils.copyToByteArray(input)).containsExactly(CONTENT);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"SAME_FACTORY_WORKER", "OTHER_FACTORY_WORKER", "OTHER_FACTORY_ADMIN"})
    void unrelatedOrDifferentFactoryViewersReceiveTheSameNotFoundResponse(String kind) {
        attachNormalReport();
        actor("SAME_FACTORY_WORKER".equals(kind) ? viewer(4L, first, Role.PRODUCTION)
                : viewer(6L, second, "OTHER_FACTORY_ADMIN".equals(kind) ? Role.ADMIN : Role.PRODUCTION));
        assertNotFound();
    }

    @Test void sameFactoryAdminKeepsAccessToHistoricalImageWithoutPersonalMetadata() throws Exception {
        image.setUploader(null); image.setReport(null);
        actor(viewer(5L, first, Role.ADMIN));
        assertThat(controller.getImage("Bearer FIXTURE_TOKEN", 101L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void productionUserCannotClaimAnOwnerlessHistoricalImage() {
        image.setUploader(null); image.setReport(new WorkOrderReport());
        actor(viewer(4L, first, Role.PRODUCTION));
        assertNotFound();
    }

    @Test void imageWithoutAFactoryIsNotExposedThroughTheMobileEndpoint() {
        image.setFactory(null);
        actor(viewer(5L, first, Role.ADMIN));
        assertNotFound();
    }

    @Test void normalReportCannotAttachAnotherUsersPendingImage() {
        reportActor();
        WorkOrderReportRequest request = normalRequest();
        request.setImageIds(Collections.singletonList(101L));
        when(images.findByFactoryIdAndIdIn(11L, request.getImageIds())).thenReturn(Collections.singletonList(image));
        assertThatThrownBy(() -> service.createReport(11L, 4L, request))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(image.getReport()).isNull();
        verify(images, never()).saveAll(anyList());
        verifyNoInteractions(notifications);
    }

    @Test void normalReportCannotAttachAnOwnerlessHistoricalImage() {
        image.setUploader(null);
        reportActor();
        WorkOrderReportRequest request = normalRequest();
        request.setImageIds(Collections.singletonList(101L));
        when(images.findByFactoryIdAndIdIn(11L, request.getImageIds())).thenReturn(Collections.singletonList(image));
        assertThatThrownBy(() -> service.createReport(11L, 4L, request))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(image.getReport()).isNull();
        verify(images, never()).saveAll(anyList());
        verifyNoInteractions(notifications);
    }

    @Test void normalReportCannotAttachAnImageFromAnotherFactory() {
        reportActor();
        WorkOrderReportRequest request = normalRequest();
        request.setImageIds(Collections.singletonList(201L));
        when(images.findByFactoryIdAndIdIn(11L, request.getImageIds())).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> service.createReport(11L, 4L, request))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(images, never()).saveAll(anyList());
        verifyNoInteractions(notifications);
    }

    private void actor(FactoryUser viewer) {
        when(tokens.requireUser("Bearer FIXTURE_TOKEN")).thenReturn(new AuthenticatedUser(viewer.getId(),
                viewer.getFactory().getId(), viewer.getFactory().getCode(), viewer.getRole(), viewer.getName()));
        when(users.findByIdAndFactoryId(viewer.getId(), viewer.getFactory().getId())).thenReturn(Optional.of(viewer));
        when(images.findById(101L)).thenReturn(Optional.of(image));
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> controller.getImage("Bearer FIXTURE_TOKEN", 101L))
                .isInstanceOf(ApiException.class).hasMessage("图片不存在")
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void attachNormalReport() {
        WorkOrderReport report = new WorkOrderReport();
        report.setFactory(first); report.setReporter(viewer(2L, first, Role.PRODUCTION)); report.setSubmitterId(3L);
        image.setReport(report);
    }

    private void reportActor() {
        FactoryUser viewer = viewer(4L, first, Role.PRODUCTION);
        when(users.findByIdAndFactoryId(4L, 11L)).thenReturn(Optional.of(viewer));
        WorkOrder order = new WorkOrder(); order.setId(501L); order.setFactory(first); order.setStatus("NOT_STARTED");
        order.setCode("NORMAL-ORDER");
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"Cut\",\"enabled\":true}]");
        when(orders.findByIdAndFactoryIdForUpdate(501L, 11L)).thenReturn(Optional.of(order));
        when(reports.save(any(WorkOrderReport.class))).thenAnswer(invocation -> {
            WorkOrderReport report = invocation.getArgument(0); report.setId(601L); return report;
        });
    }

    private WorkOrderReportRequest normalRequest() {
        WorkOrderReportRequest request = new WorkOrderReportRequest(); request.setWorkOrderId(501L);
        request.setProcessSequenceNo(1); request.setReportQuantity(1); request.setGoodQuantity(1);
        return request;
    }

    private Factory factory(long id) {
        Factory factory = new Factory(); factory.setId(id); factory.setCode("PRIVATE-" + id); factory.setEnabled(true); return factory;
    }

    private FactoryUser viewer(long id, Factory factory, Role role) {
        FactoryUser viewer = new FactoryUser(); viewer.setId(id); viewer.setFactory(factory);
        viewer.setRole(role); viewer.setName("Fixture viewer " + id); viewer.setEnabled(true); return viewer;
    }
}
