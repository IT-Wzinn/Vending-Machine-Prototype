package com.vendingmachine.order;

import com.vendingmachine.order.service.StalePaidOrderReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.dispense.recovery.tagger-enabled=false")
@ActiveProfiles("test")
public class StalePaidOrderReconciliationDisabledTest {

    @Autowired(required = false)
    private StalePaidOrderReconciliationService reconciliationService;

    @Test
    @DisplayName("Khi app.dispense.recovery.tagger-enabled=false, bean Tagger không được khởi tạo")
    void whenTaggerDisabled_thenBeanNotInstantiated() {
        assertThat(reconciliationService).isNull();
    }
}
