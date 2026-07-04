package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
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

    private WorkOrderReportService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderReportService(
                reportRepository,
                imageRepository,
                workOrderRepository,
                userRepository,
                new LocalReportImageStorage(uploadDir.toString())
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

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
