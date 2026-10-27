package com.revenda.common.test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/** O SQS do {@link LocalStackTestContainer}. */
public final class LocalStackTestSqs {

    private LocalStackTestSqs() {
    }

    public static SqsClient client() {
        return SqsClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(LocalStackTestContainer.endpoint())
                .region(LocalStackTestContainer.region())
                .credentialsProvider(LocalStackTestContainer.credentials())
                .build();
    }

    /**
     * Uma fila FIFO nova com a sua DLQ, como a do {@code infra/localstack/init.sh}, para o teste não ler a mensagem
     * de outro; o {@code visibilityTimeout} curto faz a mensagem recusada voltar logo.
     */
    public static FifoQueue createFifoQueue(SqsClient sqs, String name, int maxReceiveCount, Duration visibilityTimeout) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String dlqUrl = sqs.createQueue(request -> request.queueName(name + "-dlq-" + suffix + ".fifo")
                .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true"))).queueUrl();
        String dlqArn = sqs.getQueueAttributes(request -> request.queueUrl(dlqUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        String redrive = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + maxReceiveCount + "\"}";
        String url = sqs.createQueue(request -> request.queueName(name + "-" + suffix + ".fifo")
                .attributes(Map.of(
                        QueueAttributeName.FIFO_QUEUE, "true",
                        QueueAttributeName.VISIBILITY_TIMEOUT, Long.toString(visibilityTimeout.toSeconds()),
                        QueueAttributeName.REDRIVE_POLICY, redrive))).queueUrl();
        return new FifoQueue(url, dlqUrl);
    }

    public record FifoQueue(String url, String deadLetterUrl) {
    }
}
