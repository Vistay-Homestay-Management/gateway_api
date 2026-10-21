package com.vistay.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceInfoControllerTest {

    @Test
    void exposesStableServiceIdentity() {
        var info = new ServiceInfoController().serviceInfo();

        assertThat(info).containsEntry("name", "gateway");
        assertThat(info).containsEntry("version", "0.1.0");
    }
}
