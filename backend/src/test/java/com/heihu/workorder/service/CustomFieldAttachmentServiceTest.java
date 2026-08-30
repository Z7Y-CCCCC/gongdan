package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.CustomFieldAttachment;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.CustomFieldAttachmentRepository;
import com.heihu.workorder.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFieldAttachmentServiceTest {

    @Mock
    private CustomFieldAttachmentRepository attachmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReportImageStorage storage;

    private CustomFieldAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new CustomFieldAttachmentService(attachmentRepository, userRepository, storage);
    }

    @Test
    void uploadRejectsSpoofedImageContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evil.png",
                "image/png",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.upload(1L, 2L, "workorder", 10L, "image", file))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(storage, never()).store(any(String.class), any(MockMultipartFile.class));
    }

    @Test
    void uploadRejectsOverlongScopeBeforeStoring() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.upload(1L, 2L, repeat("x", 33), 10L, "file", file))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(storage, never()).store(any(String.class), any(MockMultipartFile.class));
    }

    @Test
    void uploadStoresAttachmentMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\report.png",
                "image/png;charset=utf-8",
                pngBytes()
        );
        Factory factory = factory(1L);
        FactoryUser user = user(2L, factory);

        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user));
        when(attachmentRepository.save(any(CustomFieldAttachment.class))).thenAnswer(invocation -> {
            CustomFieldAttachment attachment = invocation.getArgument(0);
            attachment.setId(88L);
            return attachment;
        });

        service.upload(1L, 2L, "workorder", 10L, "image", file);

        ArgumentCaptor<CustomFieldAttachment> captor = ArgumentCaptor.forClass(CustomFieldAttachment.class);
        verify(storage).store(org.mockito.ArgumentMatchers.startsWith("cf_"), org.mockito.ArgumentMatchers.eq(file));
        verify(attachmentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        CustomFieldAttachment saved = captor.getAllValues().get(1);
        assertThat(saved.getOriginalFileName()).isEqualTo("report.png");
        assertThat(saved.getScope()).isEqualTo("workorder");
        assertThat(saved.getFieldId()).isEqualTo(10L);
        assertThat(saved.getFieldType()).isEqualTo("image");
        assertThat(saved.getAccessPath()).isEqualTo("/api/custom-field-attachments/88/file");
    }

    @Test
    void uploadFallsBackWhenContentTypeIsTooLong() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.txt",
                "application/" + repeat("x", 200),
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        Factory factory = factory(1L);
        FactoryUser user = user(2L, factory);

        when(userRepository.findByIdAndFactoryId(2L, 1L)).thenReturn(Optional.of(user));
        when(attachmentRepository.save(any(CustomFieldAttachment.class))).thenAnswer(invocation -> {
            CustomFieldAttachment attachment = invocation.getArgument(0);
            attachment.setId(88L);
            return attachment;
        });

        service.upload(1L, 2L, "workorder", 10L, "file", file);

        ArgumentCaptor<CustomFieldAttachment> captor = ArgumentCaptor.forClass(CustomFieldAttachment.class);
        verify(attachmentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        CustomFieldAttachment saved = captor.getAllValues().get(1);
        assertThat(saved.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void getDownloadRejectsCrossFactoryAttachment() {
        CustomFieldAttachment attachment = new CustomFieldAttachment();
        attachment.setId(88L);
        attachment.setFactory(factory(9L));

        when(attachmentRepository.findById(88L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.getDownload(1L, 88L))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
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

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
