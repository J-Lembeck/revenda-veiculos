#!/bin/bash
set -euo pipefail

region="${AWS_DEFAULT_REGION:-sa-east-1}"

create_fifo_queue() {
    awslocal sqs create-queue --queue-name "$1" --attributes "$2" --query QueueUrl --output text
}

queue_arn() {
    awslocal sqs get-queue-attributes --queue-url "$1" --attribute-names QueueArn \
        --query Attributes.QueueArn --output text
}

key_material() {
    printf '%b' "$(printf '%s' "$1" | sha256sum | cut -c1-64 | sed 's/../\\x&/g')" | base64 -w0
}

ensure_key() {
    local alias="alias/$1" description="$2" fixed_id="${3:-}" seed="${4:-}" key_id
    local tags=()
    if [[ -n "$fixed_id" ]]; then
        tags=(--tags "TagKey=_custom_id_,TagValue=$fixed_id"
            "TagKey=_custom_key_material_,TagValue=$(key_material "$seed")")
    fi
    if ! awslocal kms describe-key --key-id "$alias" >/dev/null 2>&1; then
        key_id=$(awslocal kms create-key --description "$description" "${tags[@]}" \
            --query KeyMetadata.KeyId --output text)
        awslocal kms create-alias --alias-name "$alias" --target-key-id "$key_id"
        awslocal kms enable-key-rotation --key-id "$key_id"
    fi
}

ensure_secret() {
    local name="$1" value="$2"
    if awslocal secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1; then
        awslocal secretsmanager put-secret-value --secret-id "$name" --secret-string "$value" >/dev/null
    else
        awslocal secretsmanager create-secret --name "$name" --secret-string "$value" >/dev/null
    fi
}

dlq_url=$(create_fifo_queue vehicle-commands-dlq.fifo '{"FifoQueue":"true","MessageRetentionPeriod":"1209600"}')
redrive=$(printf '{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}' "$(queue_arn "$dlq_url")")
create_fifo_queue vehicle-commands.fifo "{\"FifoQueue\":\"true\",\"RedrivePolicy\":\"$redrive\"}" >/dev/null

ensure_key customers-data "Dados pessoais dos clientes (customers-service)" \
    6c0c4a1e-0000-4000-8000-000000000001 "$CUSTOMERS_KMS_KEY_SEED"
ensure_key documents "Documentos de transferência (sales-service)"

bucket=transfer-documents
if ! awslocal s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
    awslocal s3api create-bucket --bucket "$bucket" \
        --create-bucket-configuration "LocationConstraint=$region" >/dev/null
fi
awslocal s3api put-public-access-block --bucket "$bucket" \
    --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
documents_key_arn=$(awslocal kms describe-key --key-id alias/documents --query KeyMetadata.Arn --output text)
awslocal s3api put-bucket-encryption --bucket "$bucket" --server-side-encryption-configuration \
    "{\"Rules\":[{\"ApplyServerSideEncryptionByDefault\":{\"SSEAlgorithm\":\"aws:kms\",\"KMSMasterKeyID\":\"$documents_key_arn\"},\"BucketKeyEnabled\":true}]}"
awslocal s3api put-bucket-versioning --bucket "$bucket" --versioning-configuration Status=Enabled

ensure_secret revenda/payment-gateway-api-key "$PAYMENT_GATEWAY_API_KEY"
ensure_secret revenda/payment-webhook-secret "$PAYMENT_WEBHOOK_SECRET"
