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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                userRepository
        );
        ReflectionTestUtils.setField(service, "reportImageDir", uploadDir.toString());
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
        verify(imageRepository).save(captor.capture());
        WorkOrderReportImage saved = captor.getValue();
        assertThat(saved.getOriginalFileName()).isEqualTo("report.png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getStoredFileName()).endsWith(".png");
        assertThat(saved.getStoredFileName()).doesNotContain("..");
        assertThat(Files.exists(uploadDir.resolve(saved.getStoredFileName()))).isTrue();
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
