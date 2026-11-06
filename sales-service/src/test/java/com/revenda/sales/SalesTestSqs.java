package com.revenda.sales;

import com.revenda.common.test.LocalStackTestSqs;
import com.revenda.common.test.LocalStackTestSqs.FifoQueue;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * A fila de comandos do estoque no LocalStack de teste, uma FIFO só para a suíte do {@code sales}, com DLQ depois de 5
 * recebimentos como a do ambiente local. Todo contexto Spring do serviço usa este cliente.
 */
class SalesTestSqs {

    static final SqsClient SQS = LocalStackTestSqs.client();
    static final FifoQueue QUEUE = LocalStackTestSqs.createFifoQueue(SQS, "vehicle-commands", 5,
            Duration.ofSeconds(1));

    @Bean(destroyMethod = "")
    SqsClient sqsClient() {
        return SQS;
    }
}
