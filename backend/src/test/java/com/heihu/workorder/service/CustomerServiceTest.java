package com.heihu.workorder.service;

import com.heihu.workorder.dto.CustomerRequest;
import com.heihu.workorder.model.Customer;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.CustomerRepository;
import com.heihu.workorder.repository.FactoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private FactoryRepository factoryRepository;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, factoryRepository);
    }

    @Test
    void createNormalizesCodeAndSavesCustomer() {
        Factory factory = new Factory();
        factory.setId(1L);
        when(customerRepository.findByFactoryIdAndCode(1L, "CUS-A")).thenReturn(Optional.empty());
        when(customerRepository.findByFactoryIdAndName(1L, "Main Customer")).thenReturn(Optional.empty());
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });

        assertThat(service.create(1L, request(" cus-a ", "Main Customer")).getCode()).isEqualTo("CUS-A");
    }

    @Test
    void createRejectsDuplicateName() {
        Factory factory = new Factory();
        factory.setId(1L);
        Customer existing = new Customer();
        existing.setId(2L);
        existing.setFactory(factory);
        existing.setName("Main Customer");

        when(customerRepository.findByFactoryIdAndCode(1L, "CUS-A")).thenReturn(Optional.empty());
        when(customerRepository.findByFactoryIdAndName(1L, "Main Customer")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, request("CUS-A", "Main Customer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户名称已存在");

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateRejectsCustomerFromOtherFactory() {
        Factory factory = new Factory();
        factory.setId(2L);
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setFactory(factory);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.update(1L, 10L, request("CUS-A", "Main Customer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    void deleteRemovesCustomerInFactory() {
        Factory factory = new Factory();
        factory.setId(1L);
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setFactory(factory);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

        service.delete(1L, 10L);

        verify(customerRepository).delete(customer);
    }

    private CustomerRequest request(String code, String name) {
        CustomerRequest request = new CustomerRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }
}
