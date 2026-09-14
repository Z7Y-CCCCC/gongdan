package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.SmsCode;
import com.heihu.workorder.model.SmsIpRateLimit;
import com.heihu.workorder.repository.SmsCodeRepository;
import com.heihu.workorder.repository.SmsIpRateLimitRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** All provider calls are mocked; this class uses only its private in-memory database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sms_send_concurrency_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_TIMEOUT=10000",
        "spring.cache.type=none",
        "logging.file.name=output/sms-send-concurrency-test.log"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SmsSendConcurrencyIntegrationTest {
    @Autowired private SmsService service;
    @Autowired private SmsCodeRepository codes;
    @Autowired private SmsIpRateLimitRepository ipLimits;
    @MockBean private SmsSender sender;

    @BeforeEach
    void setUp() {
        codes.deleteAll();
        ipLimits.deleteAll();
        reset(sender);
    }

    @RepeatedTest(3)
    void concurrentFirstSendsForOnePhoneReachTheProviderExactlyOnce() throws Exception {
        List<String> phones = new ArrayList<>();
        List<String> ips = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            phones.add("13912345678");
            ips.add("198.51.100." + (i + 1));
        }
        List<HttpStatus> results = concurrentSend(phones, ips);
        assertThat(results.stream().filter(HttpStatus.OK::equals).count()).isEqualTo(1);
        assertThat(results).allMatch(status -> status == HttpStatus.OK || status == HttpStatus.TOO_MANY_REQUESTS);
        SmsCode stored = codes.findByPhone("13912345678").get();
        verify(sender, times(1)).sendVerificationCode("13912345678", stored.getCode());
        verify(sender, times(1)).sendVerificationCode(anyString(), anyString());
        assertThat(ipLimits.count()).isEqualTo(1);
    }

    @Test
    void anExpiredPhoneCannotBeSentTwiceFromDifferentIpsWhileDeliveryIsPending() throws Exception {
        assertPhoneReservationSerializes("198.51.100.2");
    }

    @Test
    void aRequestWaitingForTheSameIpCannotReuseAStalePhoneSnapshot() throws Exception {
        assertPhoneReservationSerializes("198.51.100.1");
    }

    private void assertPhoneReservationSerializes(String secondIp) throws Exception {
        String phone = "13912345679";
        SmsCode old = new SmsCode();
        old.setPhone(phone);
        old.setCode("135790");
        old.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        old.setVerified(false);
        codes.saveAndFlush(old);

        CountDownLatch delivering = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        doAnswer(invocation -> {
            delivering.countDown();
            if (!releaseDelivery.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("SMS test gate timed out");
            return null;
        }).when(sender).sendVerificationCode(eq(phone), anyString());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpStatus> first = executor.submit(() -> send(phone, "198.51.100.1"));
            assertThat(delivering.await(10, TimeUnit.SECONDS)).isTrue();
            Future<HttpStatus> second = executor.submit(() -> send(phone, secondIp));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            releaseDelivery.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(HttpStatus.OK);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            verify(sender, times(1)).sendVerificationCode(eq(phone), anyString());
        } finally {
            releaseDelivery.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentDifferentPhonesCannotExceedTheIpQuota() throws Exception {
        SmsIpRateLimit ip = new SmsIpRateLimit();
        ip.setClientIp("203.0.113.9");
        ip.setRequestCount(9);
        ip.setWindowStart(LocalDateTime.now());
        ipLimits.saveAndFlush(ip);
        List<String> phones = java.util.Arrays.asList("13912345680", "13912345681");
        List<String> ips = java.util.Arrays.asList(ip.getClientIp(), ip.getClientIp());
        assertThat(concurrentSend(phones, ips)).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ipLimits.findByClientIp(ip.getClientIp()).get().getRequestCount()).isEqualTo(10);
        assertThat(codes.count()).isEqualTo(1);
        verify(sender, times(1)).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void providerFailureRollsBackTheReservationWithoutAutomaticallyRepeatingDelivery() {
        doThrow(new IllegalStateException("mock provider failed"))
                .when(sender).sendVerificationCode(anyString(), anyString());
        assertThat(send("13912345682", "203.0.113.10")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(sender, times(1)).sendVerificationCode(anyString(), anyString());
        assertThat(codes.findByPhone("13912345682")).isEmpty();
        assertThat(ipLimits.findByClientIp("203.0.113.10")).isEmpty();

        doNothing().when(sender).sendVerificationCode(anyString(), anyString());
        assertThat(send("13912345682", "203.0.113.10")).isEqualTo(HttpStatus.OK);
        verify(sender, times(2)).sendVerificationCode(anyString(), anyString());
    }

    private HttpStatus send(String phone, String ip) {
        try {
            service.sendCode(phone, ip);
            return HttpStatus.OK;
        } catch (ApiException exception) {
            return exception.getStatus();
        }
    }

    private List<HttpStatus> concurrentSend(List<String> phones, List<String> ips) throws Exception {
        CountDownLatch ready = new CountDownLatch(phones.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(phones.size());
        List<Future<HttpStatus>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < phones.size(); i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("SMS start gate timed out");
                    return send(phones.get(index), ips.get(index));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpStatus> results = new ArrayList<>();
            for (Future<HttpStatus> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
