package com.revenda.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.revenda.common.test.LocalKeyManagement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.services.kms.KmsClient;

class CryptoAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KmsAutoConfiguration.class, CryptoAutoConfiguration.class));

    private final ApplicationContextRunner withDatabase = runner
            .withBean(JdbcClient.class, () -> JdbcClient.create(mock(DataSource.class)));

    @Test
    void serviceWithoutTheKmsKeyGetsNoCipher() {
        withDatabase.run(context -> assertThat(context)
                .doesNotHaveBean(KeyManagementPort.class)
                .doesNotHaveBean(FieldCipher.class)
                .doesNotHaveBean(BlindIndexer.class));
    }

    @Test
    void serviceWithTheKmsKeyGetsTheKmsAdapterTheCipherAndTheIndex() {
        withDatabase.withBean(KmsClient.class, () -> mock(KmsClient.class))
                .withPropertyValues("revenda.crypto.kms-key-id=alias/customers-data")
                .run(context -> {
                    assertThat(context).getBean(KeyManagementPort.class).isInstanceOf(KmsKeyManagement.class);
                    assertThat(context).getBean(DataKeys.class).isInstanceOf(JdbcDataKeys.class);
                    assertThat(context).hasSingleBean(FieldCipher.class).hasSingleBean(BlindIndexer.class);
                });
    }

    @Test
    void testAdapterTakesThePlaceOfTheKms() {
        withDatabase.withBean(KeyManagementPort.class, LocalKeyManagement::new)
                .withPropertyValues("revenda.crypto.kms-key-id=alias/customers-data")
                .run(context -> {
                    assertThat(context).getBean(KeyManagementPort.class).isInstanceOf(LocalKeyManagement.class);
                    assertThat(context).doesNotHaveBean(KmsClient.class).hasSingleBean(FieldCipher.class);
                });
    }

    @Test
    void withoutDatabaseThereIsNoCipher() {
        runner.withBean(KeyManagementPort.class, LocalKeyManagement::new)
                .run(context -> assertThat(context).doesNotHaveBean(FieldCipher.class));
    }
}
