package com.revenda.cryptoprobe;

import com.revenda.common.test.LocalStackTestKms;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.kms.KmsClient;

@SpringBootApplication
class CryptoProbeApplication {

    @Bean(destroyMethod = "close")
    KmsClient kmsClient() {
        return LocalStackTestKms.client();
    }
}
