package com.revenda.customers;

import com.revenda.common.test.LocalStackTestKms;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.NotFoundException;

/**
 * O KMS do LocalStack de teste, com a chave-mestra no mesmo apelido do {@code infra/localstack/init.sh}, que é o padrão
 * do {@code application.yml}: o serviço sobe como no ambiente local, e a chave de dados que um contexto deixa em
 * {@code data_key} abre em qualquer outro da suíte.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CustomersTestKms {

    public static final String KEY_ALIAS = "alias/customers-data";

    @Bean(destroyMethod = "close")
    KmsClient kmsClient() {
        KmsClient kms = LocalStackTestKms.client();
        try {
            kms.describeKey(request -> request.keyId(KEY_ALIAS));
        } catch (NotFoundException missing) {
            String keyId = LocalStackTestKms.createKey(kms);
            kms.createAlias(request -> request.aliasName(KEY_ALIAS).targetKeyId(keyId));
        }
        return kms;
    }
}
